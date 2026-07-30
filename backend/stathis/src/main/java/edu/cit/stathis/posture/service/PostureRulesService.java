package edu.cit.stathis.posture.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PostureRulesService {

  public static class RulesResult {
    public List<String> flags;
    public List<String> messages;
    /** Aggregate rule severity in [0, 1], derived from flag magnitudes (not ML confidence). */
    public double severity;

    public RulesResult(List<String> flags, List<String> messages) {
      this(flags, messages, severityFromFlags(flags));
    }

    public RulesResult(List<String> flags, List<String> messages, double severity) {
      this.flags = flags;
      this.messages = messages;
      this.severity = clamp01(severity);
    }
  }

  public RulesResult evaluate(String predictedClass, float[][] lastFrame) {
    Set<String> flags = new HashSet<>();
    List<String> messages = new ArrayList<>();
    Map<String, Double> flagSeverities = new HashMap<>();

    if (predictedClass == null || lastFrame == null || lastFrame.length != 33 || lastFrame[0].length != 4) {
      return new RulesResult(new ArrayList<>(flags), messages, 0.0);
    }

    switch (predictedClass) {
      case "squat":
        applySquatRules(lastFrame, flags, messages, flagSeverities);
        break;
      case "push_up":
        applyPushUpRules(lastFrame, flags, messages, flagSeverities);
        break;
      case "plank":
        applyPlankRules(lastFrame, flags, messages, flagSeverities);
        break;
      case "sit_up":
        applySitUpRules(lastFrame, flags, messages, flagSeverities);
        break;
      default:
        break;
    }

    double severity = flagSeverities.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    if (severity <= 0.0 && !flags.isEmpty()) {
      severity = severityFromFlags(new ArrayList<>(flags));
    }
    return new RulesResult(new ArrayList<>(flags), messages, severity);
  }

  /**
   * Flag-only severity when angle magnitudes are unavailable (e.g. mobile fallback).
   * Prefer {@link #evaluate} which uses metric excess over thresholds.
   */
  public static double severityFromFlags(List<String> flags) {
    if (flags == null || flags.isEmpty()) {
      return 0.0;
    }
    double max = 0.0;
    for (String flag : flags) {
      max = Math.max(max, baseSeverityForFlag(flag));
    }
    // Multiple concurrent errors raise severity slightly.
    double multi = Math.min(0.15, (flags.size() - 1) * 0.05);
    return clamp01(max + multi);
  }

  public static double baseSeverityForFlag(String flag) {
    if (flag == null) {
      return 0.4;
    }
    return switch (flag.trim().toLowerCase()) {
      case "depth_low" -> 0.55;
      case "knees_in" -> 0.65;
      case "chest_up" -> 0.5;
      case "sag" -> 0.7;
      case "pike" -> 0.6;
      case "low_rom" -> 0.55;
      default -> 0.4;
    };
  }

  private void applySquatRules(
      float[][] lm, Set<String> flags, List<String> messages, Map<String, Double> severities) {
    float[] lHip = lm[23];
    float[] rHip = lm[24];
    float[] lKnee = lm[25];
    float[] rKnee = lm[26];
    float[] lAnkle = lm[27];
    float[] rAnkle = lm[28];
    float[] lShoulder = lm[11];
    float[] rShoulder = lm[12];

    float[] hipCenter = midpoint(lHip, rHip);
    float[] shoulderCenter = midpoint(lShoulder, rShoulder);

    float kneeAngleLeft = angle(lHip, lKnee, lAnkle);
    float kneeAngleRight = angle(rHip, rKnee, rAnkle);
    float minKnee = Math.min(kneeAngleLeft, kneeAngleRight);
    if (minKnee > 150f) {
      flags.add("depth_low");
      messages.add("Go deeper to at least parallel.");
      // 150° → ~0.4, 180° → 1.0
      double excess = (minKnee - 150.0) / 30.0;
      severities.put("depth_low", clamp01(0.4 + excess * 0.6));
    }

    boolean kneesInLeft = (Math.abs(lKnee[0] - hipCenter[0]) < Math.abs(lAnkle[0] - hipCenter[0]));
    boolean kneesInRight = (Math.abs(rKnee[0] - hipCenter[0]) < Math.abs(rAnkle[0] - hipCenter[0]));
    if (kneesInLeft && kneesInRight) {
      flags.add("knees_in");
      messages.add("Push knees outward over toes.");
      float leftGap = Math.abs(lAnkle[0] - hipCenter[0]) - Math.abs(lKnee[0] - hipCenter[0]);
      float rightGap = Math.abs(rAnkle[0] - hipCenter[0]) - Math.abs(rKnee[0] - hipCenter[0]);
      double inward = Math.max(leftGap, rightGap);
      severities.put("knees_in", clamp01(0.5 + Math.min(0.5, inward * 2.0)));
    }

    float torsoLean = angleToVertical(vector(shoulderCenter, hipCenter));
    if (torsoLean > 40f) {
      flags.add("chest_up");
      messages.add("Keep chest up.");
      double excess = (torsoLean - 40.0) / 40.0;
      severities.put("chest_up", clamp01(0.4 + excess * 0.6));
    }
  }

  private void applyPushUpRules(
      float[][] lm, Set<String> flags, List<String> messages, Map<String, Double> severities) {
    float[] shoulder = midpoint(lm[11], lm[12]);
    float[] hip = midpoint(lm[23], lm[24]);
    float[] ankle = midpoint(lm[27], lm[28]);

    float sagMetric = hip[1] - lineYAtX(shoulder, ankle, hip[0]);
    if (sagMetric < -0.1f) {
      flags.add("pike");
      messages.add("Keep a straight line from head to heels.");
      double excess = (-sagMetric - 0.1) / 0.2;
      severities.put("pike", clamp01(0.45 + excess * 0.55));
    } else if (sagMetric > 0.1f) {
      flags.add("sag");
      messages.add("Avoid sagging hips.");
      double excess = (sagMetric - 0.1) / 0.2;
      severities.put("sag", clamp01(0.5 + excess * 0.5));
    }
  }

  private void applyPlankRules(
      float[][] lm, Set<String> flags, List<String> messages, Map<String, Double> severities) {
    applyPushUpRules(lm, flags, messages, severities);
    if (flags.isEmpty()) {
      messages.add("Maintain a straight line from shoulders to heels.");
    }
  }

  private void applySitUpRules(
      float[][] lm, Set<String> flags, List<String> messages, Map<String, Double> severities) {
    float[] shoulder = midpoint(lm[11], lm[12]);
    float[] hip = midpoint(lm[23], lm[24]);
    float delta = shoulder[1] - hip[1];
    if (delta > -0.1f) {
      flags.add("low_rom");
      messages.add("Increase trunk flexion.");
      double excess = (delta + 0.1) / 0.25;
      severities.put("low_rom", clamp01(0.4 + excess * 0.6));
    }
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private static float[] midpoint(float[] a, float[] b) {
    return new float[] {(a[0] + b[0]) * 0.5f, (a[1] + b[1]) * 0.5f, (a[2] + b[2]) * 0.5f, 1f};
  }

  private static float[] vector(float[] from, float[] to) {
    return new float[] {to[0] - from[0], to[1] - from[1], to[2] - from[2], 1f};
  }

  private static float angle(float[] a, float[] b, float[] c) {
    float[] ba = new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    float[] bc = new float[] {c[0] - b[0], c[1] - b[1], c[2] - b[2]};
    float dot = ba[0] * bc[0] + ba[1] * bc[1] + ba[2] * bc[2];
    float nba = (float) Math.sqrt(ba[0] * ba[0] + ba[1] * ba[1] + ba[2] * ba[2]);
    float nbc = (float) Math.sqrt(bc[0] * bc[0] + bc[1] * bc[1] + bc[2] * bc[2]);
    float cos = dot / (nba * nbc + 1e-6f);
    cos = Math.max(-1f, Math.min(1f, cos));
    return (float) (Math.acos(cos) * 180.0 / Math.PI);
  }

  private static float angleToVertical(float[] v) {
    float[] vertical = new float[] {0f, -1f, 0f};
    float dot = v[0] * vertical[0] + v[1] * vertical[1] + v[2] * vertical[2];
    float nv = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    float cos = dot / (nv + 1e-6f);
    cos = Math.max(-1f, Math.min(1f, cos));
    return (float) (Math.acos(cos) * 180.0 / Math.PI);
  }

  private static float lineYAtX(float[] p1, float[] p2, float x) {
    float dx = p2[0] - p1[0];
    if (Math.abs(dx) < 1e-6f) return p1[1];
    float t = (x - p1[0]) / dx;
    return p1[1] + t * (p2[1] - p1[1]);
  }
}
