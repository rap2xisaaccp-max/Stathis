package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.service.ProfileEffectivenessMath;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreferredModalityByExerciseTest {

  @Test
  void exploringWhenBelowLearnedThreshold() {
    Map<String, Object> effectiveness = new HashMap<>();
    for (int i = 0; i < 3; i++) {
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "SQUAT|DEPTH_LOW|VISUAL_HIGHLIGHT", 0.4, true);
    }

    Map<String, Object> byExercise =
        ProfileEffectivenessMath.derivePreferredByExercise(effectiveness);
    @SuppressWarnings("unchecked")
    Map<String, Object> squat = (Map<String, Object>) byExercise.get("SQUAT");
    assertNotNull(squat);
    assertEquals("EXPLORING", squat.get("source"));
    assertEquals("VISUAL_HIGHLIGHT", squat.get("modality"));
    assertEquals(3, ((Number) squat.get("n")).intValue());
  }

  @Test
  void learnedWhenNAndMarginMet() {
    Map<String, Object> effectiveness = new HashMap<>();
    for (int i = 0; i < 5; i++) {
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "PUSH_UP|SAG|VERBAL_TTS", 0.5, true);
    }
    for (int i = 0; i < 5; i++) {
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "PUSH_UP|SAG|VERBAL_TEXT", 0.05, false);
    }

    Map<String, Object> byExercise =
        ProfileEffectivenessMath.derivePreferredByExercise(effectiveness);
    @SuppressWarnings("unchecked")
    Map<String, Object> push = (Map<String, Object>) byExercise.get("PUSH_UP");
    assertNotNull(push);
    assertEquals("LEARNED", push.get("source"));
    assertEquals(FeedbackModality.VERBAL_TTS.name(), push.get("modality"));
    assertTrue(((Number) push.get("n")).intValue() >= 5);
  }

  @Test
  void exercisesCanDiverge() {
    Map<String, Object> effectiveness = new HashMap<>();
    for (int i = 0; i < 5; i++) {
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "SQUAT|DEPTH_LOW|VISUAL_HIGHLIGHT", 0.5, true);
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "SQUAT|DEPTH_LOW|VERBAL_TEXT", 0.05, false);
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "GLUTE_BRIDGE|LOW_ROM|VERBAL_TTS", 0.5, true);
      ProfileEffectivenessMath.updateBucket(
          effectiveness, "GLUTE_BRIDGE|LOW_ROM|VERBAL_TEXT", 0.05, false);
    }

    Map<String, Object> byExercise =
        ProfileEffectivenessMath.derivePreferredByExercise(effectiveness);
    assertEquals(
        "VISUAL_HIGHLIGHT",
        ((Map<?, ?>) byExercise.get("SQUAT")).get("modality"));
    assertEquals(
        "VERBAL_TTS",
        ((Map<?, ?>) byExercise.get("GLUTE_BRIDGE")).get("modality"));
  }
}
