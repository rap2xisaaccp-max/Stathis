package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaskComponentCompletionTest {

  @Test
  void exerciseOnlyCompletesWhenExerciseDone() {
    assertTrue(
        TaskComponentCompletion.isFullyComplete(false, false, true, false, false, true));
    assertFalse(
        TaskComponentCompletion.isFullyComplete(false, false, true, false, false, false));
  }

  @Test
  void mixedTaskRequiresEveryAttachedComponent() {
    assertFalse(
        TaskComponentCompletion.isFullyComplete(true, true, true, false, true, true));
    assertFalse(
        TaskComponentCompletion.isFullyComplete(true, true, true, true, false, true));
    assertFalse(
        TaskComponentCompletion.isFullyComplete(true, true, true, true, true, false));
    assertTrue(
        TaskComponentCompletion.isFullyComplete(true, true, true, true, true, true));
  }

  @Test
  void lessonAndExerciseDoesNotRequireQuiz() {
    assertTrue(
        TaskComponentCompletion.isFullyComplete(true, false, true, true, false, true));
    assertFalse(
        TaskComponentCompletion.isFullyComplete(true, false, true, true, false, false));
  }

  @Test
  void noTemplatesIsNotComplete() {
    assertFalse(
        TaskComponentCompletion.isFullyComplete(false, false, false, true, true, true));
  }
}
