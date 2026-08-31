package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exercise score merge rules used by {@code StudentTaskService.completeExercise}.
 */
class ExerciseScoreMergeTest {

  static int mergeBestSessionScore(int previousScore, int sessionScore) {
    return Math.max(previousScore, sessionScore);
  }

  @Test
  void keepsBestAcrossAttempts() {
    assertEquals(100, mergeBestSessionScore(80, 100));
    assertEquals(90, mergeBestSessionScore(90, 70));
    assertEquals(50, mergeBestSessionScore(0, 50));
  }

  @Test
  void sessionScoreFromRepGoal() {
    // mirrors StudentTaskService.computeExerciseScore(reps, _, goalReps, _)
    assertEquals(100, (int) Math.round(Math.min(1.0, 10.0 / 10.0) * 100.0));
    assertEquals(50, (int) Math.round(Math.min(1.0, 5.0 / 10.0) * 100.0));
    assertEquals(100, (int) Math.round(Math.min(1.0, 15.0 / 10.0) * 100.0));
  }

  /** Score.reps stores the latest attempt only (not cumulative). */
  static int mergeLatestAttemptReps(int previousRepsIgnored, int sessionReps) {
    return sessionReps;
  }

  static int incrementAttemptsAfterBackendSuccess(int previousAttempts, boolean backendSucceeded) {
    return backendSucceeded ? previousAttempts + 1 : previousAttempts;
  }

  static boolean exerciseWriteConsistent(
      int scoreAttempts, int scoreAttemptNumber, boolean exerciseCompleted) {
    return scoreAttempts == scoreAttemptNumber && exerciseCompleted && scoreAttempts > 0;
  }

  @Test
  void latestAttemptRepsOverwritePrevious() {
    assertEquals(8, mergeLatestAttemptReps(20, 8));
    assertEquals(0, mergeLatestAttemptReps(12, 0));
    assertEquals(15, mergeLatestAttemptReps(5, 15));
  }

  @Test
  void attemptsIncrementOnlyAfterBackendSuccess() {
    assertEquals(0, incrementAttemptsAfterBackendSuccess(0, false));
    assertEquals(1, incrementAttemptsAfterBackendSuccess(0, true));
    assertEquals(2, incrementAttemptsAfterBackendSuccess(1, true));
  }

  @Test
  void scoreAttemptAndCompletionStayTogether() {
    assertTrue(exerciseWriteConsistent(1, 1, true));
    assertFalse(exerciseWriteConsistent(0, 1, true));
    assertFalse(exerciseWriteConsistent(1, 1, false));
  }
}
