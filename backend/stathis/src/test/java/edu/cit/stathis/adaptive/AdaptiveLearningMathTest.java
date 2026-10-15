package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.coaching.InstructionIntensity;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import org.junit.jupiter.api.Test;

class AdaptiveLearningMathTest {

  @Test
  void successfulDeltaThreshold() {
    assertTrue(StudentLearningProfileService.isSuccessfulDelta(0.15));
    assertTrue(StudentLearningProfileService.isSuccessfulDelta(0.4));
    assertFalse(StudentLearningProfileService.isSuccessfulDelta(0.1));
  }

  @Test
  void formErrorCodeFromFlags() {
    assertEquals(FormErrorCode.CHEST_UP, FormErrorCode.fromFlag("chest_up"));
    assertEquals(FormErrorCode.KNEES_IN, FormErrorCode.fromFlag("knees_in"));
    assertEquals(FormErrorCode.DEPTH_LOW, FormErrorCode.fromFlag("depth_low"));
    assertEquals(FormErrorCode.UNKNOWN, FormErrorCode.fromFlag("something_weird"));
  }

  @Test
  void catalogMessagesCoverCoreErrors() {
    assertTrue(
        CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.CHEST_UP, InstructionIntensity.REMINDER)
            .toLowerCase()
            .contains("torso"));
    assertTrue(
        CoachingInstructionCatalog.messageText("PUSH_UP", FormErrorCode.SAG, InstructionIntensity.REMINDER)
            .toLowerCase()
            .contains("hip"));
  }
}
