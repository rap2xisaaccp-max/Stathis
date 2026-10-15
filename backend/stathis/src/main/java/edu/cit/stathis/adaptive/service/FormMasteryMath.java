package edu.cit.stathis.adaptive.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent per-student, per-exercise Form Mastery from completed classroom attempts.
 *
 * <p>This is not coaching frequency ({@code exercise_mastery.mastery_level}), not a Policy B
 * cycle count, and not the latest attempt replacing history. Attempt quality mixes form
 * accuracy, valid-rep completion, and valid-vs-attempted reps. The running level is an
 * exponential blend so one weak attempt moves the number without erasing it.
 *
 * <p>{@link Thresholds} are the initial system values. Tune them after validation; do not
 * copy the coaching-frequency bands (0.40 / 0.65).
 */
public final class FormMasteryMath {

  private FormMasteryMath() {}

  /**
   * Initial Form Mastery weights and state lines. Change these constants together with
   * {@code FormMasteryMathTest} when the thresholds are retuned.
   */
  public static final class Thresholds {
    /** Share of attempt quality that comes from recorded form accuracy. */
    public static final double ACCURACY_WEIGHT = 0.50;
    /** Share from valid reps / goal reps. */
    public static final double COMPLETION_WEIGHT = 0.30;
    /** Share from valid reps / attempted reps. */
    public static final double VALIDITY_WEIGHT = 0.20;
    /** Weight kept from the previous mastery level. */
    public static final double HISTORY_WEIGHT = 0.65;
    /** Weight given to the newest attempt. */
    public static final double ATTEMPT_WEIGHT = 0.35;
    public static final int MIN_QUALIFYING_FOR_MASTERED = 3;
    public static final int RECENT_WINDOW = 3;
    /** Below this level, with fewer than three attempts, the state is Learning. */
    public static final double IMPROVING_LEVEL = 0.70;
    /** Level required to enter Mastered, along with a strong recent window. */
    public static final double MASTERED_LEVEL = 0.85;
    /** Each of the last {@link #RECENT_WINDOW} attempts must reach this quality. */
    public static final double MASTERED_RECENT_QUALITY = 0.80;
    /**
     * After Mastered, a level below this can leave Mastered only once at least two
     * attempts have been applied since entering. One attempt cannot cross this alone.
     */
    public static final double MASTERED_EXIT_LEVEL = 0.75;
    /** A qualifying attempt below this counts as weak while Mastered. */
    public static final double MASTERED_EXIT_QUALITY = 0.70;
    public static final int MASTERED_EXIT_CONSECUTIVE = 2;

    private Thresholds() {}
  }

  public enum State {
    LEARNING,
    IMPROVING,
    MASTERED
  }

  /** One eligible classroom attempt, oldest-to-newest when passed to {@link #evaluate}. */
  public static final class AttemptInput {
    private final double accuracyPercent;
    private final int validReps;
    private final Integer goalReps;
    private final Integer attemptedReps;

    public AttemptInput(
        double accuracyPercent, int validReps, Integer goalReps, Integer attemptedReps) {
      this.accuracyPercent = accuracyPercent;
      this.validReps = validReps;
      this.goalReps = goalReps;
      this.attemptedReps = attemptedReps;
    }

    public double accuracyPercent() {
      return accuracyPercent;
    }

    public int validReps() {
      return validReps;
    }

    public Integer goalReps() {
      return goalReps;
    }

    public Integer attemptedReps() {
      return attemptedReps;
    }
  }

  public static final class Evaluation {
    private final double level;
    private final State state;
    private final int qualifyingCount;

    public Evaluation(double level, State state, int qualifyingCount) {
      this.level = level;
      this.state = state;
      this.qualifyingCount = qualifyingCount;
    }

    public double level() {
      return level;
    }

    public State state() {
      return state;
    }

    public int qualifyingCount() {
      return qualifyingCount;
    }
  }

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
   * Folds qualifying attempts in chronological order. Returns null when there are none.
   * Callers must not treat that as 0% or 100%.
   */
  public static Evaluation evaluate(List<AttemptInput> chronological) {
    if (chronological == null || chronological.isEmpty()) {
      return null;
    }
    Double level = null;
    boolean mastered = false;
    int consecutiveWeak = 0;
    int updatesSinceMastered = 0;
    int qualifying = 0;
    List<Double> recent = new ArrayList<>();
    for (AttemptInput attempt : chronological) {
      if (attempt == null || attempt.validReps() <= 0) {
        continue;
      }
      double quality = attemptQuality(attempt);
      qualifying++;
      level =
          level == null
              ? quality
              : clamp(
                  Thresholds.HISTORY_WEIGHT * level + Thresholds.ATTEMPT_WEIGHT * quality,
                  0.0,
                  1.0);
      recent.add(quality);
      if (recent.size() > Thresholds.RECENT_WINDOW) {
        recent.remove(0);
      }
      if (mastered) {
        updatesSinceMastered++;
        if (quality < Thresholds.MASTERED_EXIT_QUALITY) {
          consecutiveWeak++;
        } else {
          consecutiveWeak = 0;
        }
        boolean repeatedWeak = consecutiveWeak >= Thresholds.MASTERED_EXIT_CONSECUTIVE;
        boolean levelFell =
            updatesSinceMastered >= 2 && level < Thresholds.MASTERED_EXIT_LEVEL;
        if (repeatedWeak || levelFell) {
          mastered = false;
          consecutiveWeak = 0;
          updatesSinceMastered = 0;
        }
      }
      if (!mastered
          && qualifying >= Thresholds.MIN_QUALIFYING_FOR_MASTERED
          && level >= Thresholds.MASTERED_LEVEL
          && recent.size() >= Thresholds.RECENT_WINDOW
          && recentAllAtLeast(recent, Thresholds.MASTERED_RECENT_QUALITY)) {
        mastered = true;
        consecutiveWeak = 0;
        updatesSinceMastered = 0;
      }
    }
    if (level == null || qualifying <= 0) {
      return null;
    }
    State state;
    if (mastered) {
      state = State.MASTERED;
    } else if (qualifying >= Thresholds.MIN_QUALIFYING_FOR_MASTERED
        && level >= Thresholds.IMPROVING_LEVEL) {
      state = State.IMPROVING;
    } else {
      state = State.LEARNING;
    }
    return new Evaluation(level, state, qualifying);
  }

  /**
   * When goal reps or attempted reps were not stored (older rows), that term uses form
   * accuracy so the missing count is not punished twice. When both are missing, quality
   * equals form accuracy.
   */
  public static double attemptQuality(AttemptInput attempt) {
    double formAccuracy = clamp(attempt.accuracyPercent() / 100.0, 0.0, 1.0);
    int valid = Math.max(0, attempt.validReps());
    double completion =
        attempt.goalReps() == null || attempt.goalReps() <= 0
            ? formAccuracy
            : clamp(valid / (double) attempt.goalReps(), 0.0, 1.0);
    double validity =
        attempt.attemptedReps() == null || attempt.attemptedReps() <= 0
            ? formAccuracy
            : clamp(valid / (double) attempt.attemptedReps(), 0.0, 1.0);
    return clamp(
        Thresholds.ACCURACY_WEIGHT * formAccuracy
            + Thresholds.COMPLETION_WEIGHT * completion
            + Thresholds.VALIDITY_WEIGHT * validity,
        0.0,
        1.0);
  }

  public static int displayPercent(double formMasteryLevel) {
    return (int) Math.round(clamp(formMasteryLevel, 0.0, 1.0) * 100.0);
  }

  private static boolean recentAllAtLeast(List<Double> recent, double minimum) {
    for (Double quality : recent) {
      if (quality == null || quality < minimum) {
        return false;
      }
    }
    return !recent.isEmpty();
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
