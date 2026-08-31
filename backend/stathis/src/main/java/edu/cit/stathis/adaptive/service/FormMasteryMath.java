package edu.cit.stathis.adaptive.service;

import java.util.List;

/**
 * Attempt-level Form Mastery from recorded classroom exercise accuracy.
 *
 * This is not coaching frequency, not Policy B cycle counts, and not percent of
 * correct reps. Eligible attempts are completed classroom exercise submissions
 * with meaningful recorded form data.
 */
public final class FormMasteryMath {

  private FormMasteryMath() {}

  /**
   * Classroom exercise attempts only. Practice never writes {@code score_attempt}.
   * Cancelled/incomplete attempts are not persisted. Quiz rows and any attempt
   * with {@code reps <= 0} are excluded, even if accuracy is non-zero — no valid
   * repetition was completed. Measured 0% is valid only when {@code reps > 0}
   * and accuracy is 0.
   */
  public static boolean isEligibleClassroomExerciseAttempt(
      String exerciseTemplateId, String quizTemplateId, Integer reps, Double accuracy) {
    if (exerciseTemplateId == null || exerciseTemplateId.isBlank()) {
      return false;
    }
    if (quizTemplateId != null && !quizTemplateId.isBlank()) {
      return false;
    }
    int safeReps = reps == null ? 0 : reps;
    return safeReps > 0;
  }

  /**
   * Mean of recorded attempt accuracy values (0–100) as a 0–1 Form Mastery level.
   * Returns {@code null} when there are no eligible accuracies — callers must not
   * treat that as 0% or 100%.
   */
  public static Double meanFormMasteryLevel(List<Double> accuracyPercents) {
    if (accuracyPercents == null || accuracyPercents.isEmpty()) {
      return null;
    }
    double sum = 0.0;
    int count = 0;
    for (Double accuracy : accuracyPercents) {
      if (accuracy == null || Double.isNaN(accuracy)) {
        continue;
      }
      sum += clamp(accuracy, 0.0, 100.0);
      count++;
    }
    if (count <= 0) {
      return null;
    }
    return clamp((sum / count) / 100.0, 0.0, 1.0);
  }

  public static int displayPercent(double formMasteryLevel) {
    return (int) Math.round(clamp(formMasteryLevel, 0.0, 1.0) * 100.0);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
