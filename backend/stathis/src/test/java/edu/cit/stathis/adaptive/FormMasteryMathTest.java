package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.service.FormMasteryMath;
import edu.cit.stathis.adaptive.service.FormMasteryMath.AttemptInput;
import edu.cit.stathis.adaptive.service.FormMasteryMath.Evaluation;
import edu.cit.stathis.adaptive.service.FormMasteryMath.State;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormMasteryMathTest {

  @Test
  void oneStrongAttemptStaysLearning() {
    Evaluation evaluation = FormMasteryMath.evaluate(List.of(legacy(100.0, 10)));
    assertNotNull(evaluation);
    assertEquals(1.0, evaluation.level(), 1e-9);
    assertEquals(State.LEARNING, evaluation.state());
    assertEquals(100, FormMasteryMath.displayPercent(evaluation.level()));
  }

  @Test
  void measuredZeroFiftyAndHundred() {
    assertEquals(0.0, FormMasteryMath.evaluate(List.of(legacy(0.0, 8))).level(), 1e-9);
    assertEquals(0.5, FormMasteryMath.evaluate(List.of(legacy(50.0, 8))).level(), 1e-9);
    assertEquals(1.0, FormMasteryMath.evaluate(List.of(legacy(100.0, 8))).level(), 1e-9);
  }

  @Test
  void noEligibleAccuraciesIsUndefinedNotZeroOrHundred() {
    assertNull(FormMasteryMath.evaluate(List.of()));
    assertNull(FormMasteryMath.evaluate(null));
  }

  @Test
  void retriesUseHistoryWeightInsteadOfAnEqualMean() {
    // Missing goal/attempted collapse quality to accuracy: 0.40 then 0.60.
    // level = 0.65 * 0.40 + 0.35 * 0.60 = 0.47, not the equal mean 0.50.
    Evaluation evaluation = FormMasteryMath.evaluate(List.of(legacy(40.0, 10), legacy(60.0, 10)));
    assertEquals(0.47, evaluation.level(), 1e-9);
    assertEquals(State.LEARNING, evaluation.state());
    assertEquals(47, FormMasteryMath.displayPercent(evaluation.level()));
  }

  @Test
  void fullQualityUsesAccuracyCompletionAndValidity() {
    AttemptInput attempt = new AttemptInput(80.0, 8, 10, 10);
    // 0.50*0.80 + 0.30*0.80 + 0.20*0.80 = 0.80
    assertEquals(0.80, FormMasteryMath.attemptQuality(attempt), 1e-9);

    AttemptInput mixed = new AttemptInput(90.0, 8, 10, 10);
    // accuracy 0.90, completion 0.80, validity 0.80
    // 0.50*0.90 + 0.30*0.80 + 0.20*0.80 = 0.45 + 0.24 + 0.16 = 0.85
    assertEquals(0.85, FormMasteryMath.attemptQuality(mixed), 1e-9);
  }

  @Test
  void threeStrongAttemptsCanReachMastered() {
    AttemptInput strong = new AttemptInput(95.0, 10, 10, 10);
    Evaluation evaluation = FormMasteryMath.evaluate(List.of(strong, strong, strong));
    assertEquals(State.MASTERED, evaluation.state());
    assertTrue(evaluation.level() >= FormMasteryMath.Thresholds.MASTERED_LEVEL);
  }

  @Test
  void oneWeakAttemptAfterMasteryLowersLevelAndKeepsMastered() {
    AttemptInput strong = new AttemptInput(95.0, 10, 10, 10);
    AttemptInput weak = new AttemptInput(40.0, 4, 10, 10);
    Evaluation mastered = FormMasteryMath.evaluate(List.of(strong, strong, strong));
    assertEquals(State.MASTERED, mastered.state());
    Evaluation after =
        FormMasteryMath.evaluate(List.of(strong, strong, strong, weak));
    assertEquals(State.MASTERED, after.state());
    assertTrue(after.level() < mastered.level());
    assertTrue(after.level() > weak.accuracyPercent() / 100.0);
  }

  @Test
  void twoWeakAttemptsLeaveMastered() {
    AttemptInput strong = new AttemptInput(95.0, 10, 10, 10);
    AttemptInput weak = new AttemptInput(40.0, 4, 10, 10);
    Evaluation after =
        FormMasteryMath.evaluate(List.of(strong, strong, strong, weak, weak));
    assertNotEquals(State.MASTERED, after.state());
    assertTrue(after.level() < FormMasteryMath.Thresholds.MASTERED_LEVEL);
  }

  @Test
  void missingAttemptedRepsDoesNotDoublePenalizeHistoricalRows() {
    AttemptInput historical = new AttemptInput(80.0, 10, null, null);
    assertEquals(0.80, FormMasteryMath.attemptQuality(historical), 1e-9);
  }

  @Test
  void excludesQuizAndEmptyAttempts() {
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt(null, null, 10, 80.0));
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt(" ", null, 10, 80.0));
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", "QUIZ-1", 10, 80.0));
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, 0, 0.0));
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, null, 0.0));
  }

  @Test
  void excludesZeroRepsEvenWhenAccuracyIsPositive() {
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, 0, 50.0));
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, -1, 80.0));
    assertFalse(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, null, 90.0));
  }

  @Test
  void includesMeasuredZeroAccuracyWhenRepsExist() {
    assertTrue(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", null, 8, 0.0));
  }

  @Test
  void includesBlankQuizIdAsExerciseAttempt() {
    assertTrue(FormMasteryMath.isEligibleClassroomExerciseAttempt("TPL-1", "  ", 5, 80.0));
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

  private static AttemptInput legacy(double accuracy, int reps) {
    return new AttemptInput(accuracy, reps, null, null);
  }
}
