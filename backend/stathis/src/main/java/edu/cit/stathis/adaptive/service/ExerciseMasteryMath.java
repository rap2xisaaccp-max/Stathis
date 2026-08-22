package edu.cit.stathis.adaptive.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure mastery / difficulty helpers. Recommendations are soft — teachers must approve
 * before changing exercise templates.
 */
public final class ExerciseMasteryMath {

  private ExerciseMasteryMath() {}

  public static double updateMastery(double prior, double delta, boolean success) {
    double deltaBoost = Math.max(0.0, delta) * 0.25;
    double penalty = success ? 0.0 : 0.05;
    return clamp(prior + deltaBoost - penalty, 0.0, 1.0);
  }

  /**
   * Three teacher-facing bands only (Beginner / Intermediate / Advanced).
   * High mastery maps to Advanced — Expert is no longer recommended.
   */
  public static String recommendDifficulty(double mastery) {
    if (mastery >= 0.65) {
      return "ADVANCED";
    }
    if (mastery >= 0.40) {
      return "INTERMEDIATE";
    }
    return "BEGINNER";
  }

  /** Collapse legacy EXPERT labels onto Advanced for recommendations. */
  public static String normalizeDifficulty(String difficulty) {
    if (difficulty == null || difficulty.isBlank()) {
      return "BEGINNER";
    }
    String d = difficulty.trim().toUpperCase();
    if ("EXPERT".equals(d)) {
      return "ADVANCED";
    }
    if ("ADVANCED".equals(d) || "INTERMEDIATE".equals(d) || "BEGINNER".equals(d)) {
      return d;
    }
    return "BEGINNER";
  }

  /**
   * Soft goal-reps suggestion aligned with template picker (10 / 20 / 30).
   * When {@code currentGoalReps} is set, nudges toward the baseline by at most ±10
   * so teachers are not asked to jump across the full ladder in one step.
   */
  public static int recommendGoalReps(String difficulty, Integer currentGoalReps) {
    int baseline = baselineGoalReps(difficulty);
    if (currentGoalReps == null || currentGoalReps <= 0) {
      return baseline;
    }
    int delta = baseline - currentGoalReps;
    int step = (int) Math.round(delta / 2.0);
    step = Math.max(-10, Math.min(10, step));
    int nudged = currentGoalReps + step;
    // Snap soft suggestions onto the teacher template options when close.
    return snapToTemplateGoalReps(nudged);
  }

  public static int baselineGoalReps(String difficulty) {
    return switch (normalizeDifficulty(difficulty)) {
      case "ADVANCED" -> 30;
      case "INTERMEDIATE" -> 20;
      default -> 10;
    };
  }

  /** Prefer the create-template goal options: 10, 20, 30. */
  public static int snapToTemplateGoalReps(int reps) {
    if (reps < 15) {
      return 10;
    }
    if (reps < 25) {
      return 20;
    }
    return 30;
  }

  public static String buildRationale(
      double masteryLevel,
      String recommendedDifficulty,
      int recommendedGoalReps,
      Map<String, Object> commonErrors) {
    String topError = topErrorCode(commonErrors);
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Mastery %.0f%% suggests %s with about %d goal reps.",
        masteryLevel * 100.0, recommendedDifficulty, recommendedGoalReps));
    if (topError != null) {
      sb.append(" Most frequent form error: ").append(topError.replace('_', ' ')).append('.');
    }
    sb.append(" Soft recommendation only — apply manually in the exercise template.");
    return sb.toString();
  }

  public static double fromSessionErrors(int sessions, int totalErrors) {
    if (sessions <= 0) {
      return 0.0;
    }
    return clamp(1.0 - (totalErrors / (sessions * 4.0)), 0.0, 1.0);
  }

  public static int totalErrorCount(Map<String, Object> commonErrors) {
    if (commonErrors == null || commonErrors.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (Object value : commonErrors.values()) {
      if (value instanceof Number number) {
        total += number.intValue();
      }
    }
    return total;
  }

  public static String topErrorCode(Map<String, Object> commonErrors) {
    if (commonErrors == null || commonErrors.isEmpty()) {
      return null;
    }
    return commonErrors.entrySet().stream()
        .max(
            Comparator.comparingInt(
                e -> e.getValue() instanceof Number n ? n.intValue() : 0))
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  public static List<String> topErrorCodes(Map<String, Object> commonErrors, int limit) {
    if (commonErrors == null || commonErrors.isEmpty()) {
      return List.of();
    }
    List<Map.Entry<String, Object>> entries = new ArrayList<>(commonErrors.entrySet());
    entries.sort(
        (a, b) ->
            Integer.compare(
                b.getValue() instanceof Number n ? n.intValue() : 0,
                a.getValue() instanceof Number n ? n.intValue() : 0));
    return entries.stream().limit(Math.max(0, limit)).map(Map.Entry::getKey).toList();
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }
}
