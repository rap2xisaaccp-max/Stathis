package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.service.FormMasteryMath;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormMasteryMathTest {

  @Test
  void averagesMultipleAttemptAccuracies() {
    assertEquals(0.50, FormMasteryMath.meanFormMasteryLevel(List.of(40.0, 60.0)), 1e-9);
    assertEquals(50, FormMasteryMath.displayPercent(0.50));
  }

  @Test
  void measuredZeroFiftyAndHundred() {
    assertEquals(0.0, FormMasteryMath.meanFormMasteryLevel(List.of(0.0)), 1e-9);
    assertEquals(0, FormMasteryMath.displayPercent(0.0));
    assertEquals(0.5, FormMasteryMath.meanFormMasteryLevel(List.of(50.0)), 1e-9);
    assertEquals(50, FormMasteryMath.displayPercent(0.5));
    assertEquals(1.0, FormMasteryMath.meanFormMasteryLevel(List.of(100.0)), 1e-9);
    assertEquals(100, FormMasteryMath.displayPercent(1.0));
  }

  @Test
  void noEligibleAccuraciesIsUndefinedNotZeroOrHundred() {
    assertNull(FormMasteryMath.meanFormMasteryLevel(List.of()));
    assertNull(FormMasteryMath.meanFormMasteryLevel(null));
  }

  @Test
  void retriesAreSeparateCompletedAttemptsInTheMean() {
    assertEquals(0.70, FormMasteryMath.meanFormMasteryLevel(List.of(40.0, 100.0)), 1e-9);
  }

  @Test
  void excludesQuizAndEmptyAttempts() {
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt(null, null, 10, 80.0));
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt(" ", null, 10, 80.0));
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", "QUIZ-1", 10, 80.0));
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, 0, 0.0));
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, null, 0.0));
  }

  @Test
  void excludesZeroRepsEvenWhenAccuracyIsPositive() {
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, 0, 50.0));
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, -1, 80.0));
    assertFalse(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, null, 90.0));
  }

  @Test
  void includesMeasuredZeroAccuracyWhenRepsExist() {
    assertTrue(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, 8, 0.0));
  }

  @Test
  void includesBlankQuizIdAsExerciseAttempt() {
    assertTrue(
        FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", "  ", 5, 80.0));
  }

  @Test
  void groupingKeysMatchNormalizedExerciseAliases() {
    assertEquals(
        "SQUATS",
        edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise("SQUAT"));
    assertEquals(
        "SQUATS",
        edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise("SQUATS"));
    assertEquals(
        "PUSH_UP",
        edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise("PUSHUPS"));
    assertEquals(
        "PUSH_UP",
        edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise("PUSH_UP"));
    assertEquals(
        "STATIC_LUNGES",
        edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise("LUNGE"));
  }
}
