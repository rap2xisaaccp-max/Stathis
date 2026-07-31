package edu.cit.stathis.adaptive.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Primary RCT / ablation metrics for APSLE evaluation (plan §16).
 *
 * <p>Operates on plain numeric samples so unit tests and classroom exports share one definition of
 * success rate, mean delta, and arm contrast.
 */
public final class RctEvaluationMetrics {

  private RctEvaluationMetrics() {}

  public record ArmStats(
      String arm,
      long interventions,
      long successes,
      double successRate,
      double meanDelta,
      double meanMastery) {}

  public record AblationContrast(
      ArmStats adaptive,
      ArmStats control,
      double meanDeltaLift,
      double successRateLift,
      boolean adaptiveOutperformsOnDelta) {}

  public static double mean(List<Double> values) {
    if (values == null || values.isEmpty()) {
      return 0.0;
    }
    double sum = 0.0;
    for (Double v : values) {
      if (v != null) {
        sum += v;
      }
    }
    return sum / values.size();
  }

  public static double successRate(long successes, long total) {
    if (total <= 0) {
      return 0.0;
    }
    return (double) successes / (double) total;
  }

  /** Cohen's d for two independent samples (pooled SD). Returns 0 when undefined. */
  public static double cohensD(List<Double> treatment, List<Double> control) {
    if (treatment == null || control == null || treatment.isEmpty() || control.isEmpty()) {
      return 0.0;
    }
    double meanT = mean(treatment);
    double meanC = mean(control);
    double varT = variance(treatment, meanT);
    double varC = variance(control, meanC);
    double pooled =
        Math.sqrt(
            ((treatment.size() - 1) * varT + (control.size() - 1) * varC)
                / Math.max(1, treatment.size() + control.size() - 2));
    if (pooled < 1e-9) {
      return 0.0;
    }
    return (meanT - meanC) / pooled;
  }

  public static ArmStats armStats(
      String arm, List<Double> deltas, long successes, Double meanMastery) {
    long n = deltas == null ? 0 : deltas.size();
    return new ArmStats(
        arm == null ? "UNKNOWN" : arm,
        n,
        successes,
        successRate(successes, n),
        mean(deltas),
        meanMastery == null ? 0.0 : meanMastery);
  }

  public static AblationContrast contrast(ArmStats adaptive, ArmStats control) {
    double deltaLift = adaptive.meanDelta() - control.meanDelta();
    double successLift = adaptive.successRate() - control.successRate();
    return new AblationContrast(
        adaptive, control, deltaLift, successLift, deltaLift > 0.0);
  }

  /**
   * Normalize experimentArm labels so PRACTICE suffixes stay grouped under base arm when comparing
   * policy (ADAPTIVE vs STATIC), while still countable separately upstream.
   */
  public static String baseArm(String experimentArm) {
    if (experimentArm == null || experimentArm.isBlank()) {
      return "ADAPTIVE";
    }
    String upper = experimentArm.trim().toUpperCase();
    if (upper.startsWith("STATIC")) {
      return "STATIC";
    }
    return "ADAPTIVE";
  }

  public static boolean isPracticeArm(String experimentArm) {
    return experimentArm != null && experimentArm.toUpperCase().contains("PRACTICE");
  }

  public static Map<String, Long> topErrors(Map<String, Long> frequency, int limit) {
    if (frequency == null || frequency.isEmpty()) {
      return Map.of();
    }
    return frequency.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
        .limit(Math.max(0, limit))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  public static Map<String, List<Double>> groupDeltasByBaseArm(
      Map<String, List<Double>> deltasByArm) {
    Map<String, List<Double>> grouped = new HashMap<>();
    if (deltasByArm == null) {
      return grouped;
    }
    for (Map.Entry<String, List<Double>> entry : deltasByArm.entrySet()) {
      String base = baseArm(entry.getKey());
      grouped.computeIfAbsent(base, k -> new ArrayList<>()).addAll(entry.getValue());
    }
    return grouped;
  }

  private static double variance(List<Double> values, double mean) {
    if (values.size() < 2) {
      return 0.0;
    }
    double acc = 0.0;
    for (Double v : values) {
      if (v == null) {
        continue;
      }
      double d = v - mean;
      acc += d * d;
    }
    return acc / (values.size() - 1);
  }
}
