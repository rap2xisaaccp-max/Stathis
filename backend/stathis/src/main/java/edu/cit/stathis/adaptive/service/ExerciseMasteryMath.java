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

  public static String recommendDifficulty(double mastery) {
    if (mastery >= 0.85) {
      return "EXPERT";
    }
    if (mastery >= 0.65) {
      return "ADVANCED";
    }
    if (mastery >= 0.40) {
      return "INTERMEDIATE";
    }
    return "BEGINNER";
  }

  /**
   * Soft goal-reps suggestion from recommended difficulty. When {@code currentGoalReps} is set,
   * nudges toward the baseline by at most ±4 so teachers are not asked to jump abruptly.
   */
  public static int recommendGoalReps(String difficulty, Integer currentGoalReps) {
    int baseline = baselineGoalReps(difficulty);
    if (currentGoalReps == null || currentGoalReps <= 0) {
      return baseline;
    }
    int delta = baseline - currentGoalReps;
    int step = (int) Math.round(delta / 2.0);
    step = Math.max(-4, Math.min(4, step));
    return Math.max(5, currentGoalReps + step);
  }

  public static int baselineGoalReps(String difficulty) {
    if (difficulty == null) {
      return 8;
    }
    return switch (difficulty.trim().toUpperCase()) {
      case "EXPERT" -> 20;
      case "ADVANCED" -> 15;
      case "INTERMEDIATE" -> 12;
      default -> 8;
    };
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
