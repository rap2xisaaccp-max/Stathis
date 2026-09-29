package edu.cit.stathis.task;

import edu.cit.stathis.task.entity.Score;

/**
 * Whether one assigned exercise qualifies as officially complete.
 *
 * <p>The current rule matches today's graded submit: a saved attempt counts. Host-defined
 * minimum valid reps, form accuracy, score, or other requirements replace this method later.
 * {@code task_exercise} does not store those thresholds.
 */
public final class ExerciseCompletionPolicy {

  public static final String NOT_STARTED = "NOT_STARTED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String COMPLETED = "COMPLETED";

  private ExerciseCompletionPolicy() {}

  /** A graded row exists. This is not the future qualification rule. */
  public static boolean hasSavedAttempt(Score score) {
    return score != null && (score.getAttempts() > 0 || score.isCompleted());
  }

  /**
   * Official completion for one assigned exercise. Today this matches a saved graded attempt.
   * Recommendation #3 changes this method only.
   */
  public static boolean qualifies(Score score) {
    return hasSavedAttempt(score);
  }

  public static String status(Score score) {
    if (!hasSavedAttempt(score)) {
      return NOT_STARTED;
    }
    if (qualifies(score)) {
      return COMPLETED;
    }
    return IN_PROGRESS;
  }

  /**
   * Aggregate attempt count exposed on the legacy single-exercise progress fields.
   * A one-exercise task keeps that exercise's count. A multi-exercise task stays at 0
   * until every required exercise qualifies, so an older app does not treat the whole
   * task as done after exercise 1.
   */
  public static int legacyExerciseAttempts(
      int requiredExercises, int qualifiedExercises, int firstExerciseAttempts) {
    if (requiredExercises <= 1) {
      return Math.max(0, firstExerciseAttempts);
    }
    if (requiredExercises > 0 && qualifiedExercises >= requiredExercises) {
      return Math.max(1, firstExerciseAttempts);
    }
    return 0;
  }
}
