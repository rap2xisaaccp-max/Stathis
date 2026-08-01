package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.service.AdaptivePolicyService;
import edu.cit.stathis.adaptive.service.ProfileEffectivenessMath;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import org.junit.jupiter.api.Test;

/** Lightweight math smoke checks; detailed Phase 3 coverage lives in StudentLearningProfileUpdateMathTest. */
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
  void defaultMessagesCoverCoreErrors() {
    assertTrue(AdaptivePolicyService.defaultMessage(FormErrorCode.CHEST_UP).contains("chest"));
    assertTrue(AdaptivePolicyService.defaultMessage(FormErrorCode.SAG).toLowerCase().contains("sag"));
  }

  @Test
  void modalityEnumValuesForV1() {
    assertNotNull(FeedbackModality.VERBAL_TEXT);
    assertNotNull(FeedbackModality.VISUAL_HIGHLIGHT);
    assertNotNull(FeedbackModality.VERBAL_TTS);
  }

  @Test
  void profileMathConstantsAreStable() {
    assertEquals(0.3, ProfileEffectivenessMath.EWMA_ALPHA, 1e-9);
    assertEquals(0.15, ProfileEffectivenessMath.SUCCESS_DELTA_THRESHOLD, 1e-9);
    assertEquals(5, ProfileEffectivenessMath.WARMUP_N);
  }
}
