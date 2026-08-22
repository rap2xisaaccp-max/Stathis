package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.service.ExerciseMasteryMath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ExerciseMasteryMathTest {

  @Test
  void fromSessionErrorsDropsWithMoreCorrections() {
    assertEquals(0.0, ExerciseMasteryMath.fromSessionErrors(0, 4), 1e-6);
    assertEquals(0.75, ExerciseMasteryMath.fromSessionErrors(1, 1), 1e-6);
    assertEquals(0, ExerciseMasteryMath.totalErrorCount(Map.of()));
    Map<String, Object> errors = new LinkedHashMap<>();
    errors.put("SAG", 2);
    errors.put("PIKE", 1);
    assertEquals(3, ExerciseMasteryMath.totalErrorCount(errors));
  }

  @Test
  void updateMasteryRewardsPositiveDelta() {
    double updated = ExerciseMasteryMath.updateMastery(0.40, 0.40, true);
    assertEquals(0.50, updated, 1e-6);
  }

  @Test
  void updateMasteryPenalizesFailure() {
    double updated = ExerciseMasteryMath.updateMastery(0.40, 0.0, false);
    assertEquals(0.35, updated, 1e-6);
  }

  @Test
  void recommendDifficultyUsesThreeBands() {
    assertEquals("BEGINNER", ExerciseMasteryMath.recommendDifficulty(0.10));
    assertEquals("INTERMEDIATE", ExerciseMasteryMath.recommendDifficulty(0.50));
    assertEquals("ADVANCED", ExerciseMasteryMath.recommendDifficulty(0.70));
    assertEquals("ADVANCED", ExerciseMasteryMath.recommendDifficulty(0.90));
  }

  @Test
  void normalizeDifficultyMapsExpertToAdvanced() {
    assertEquals("ADVANCED", ExerciseMasteryMath.normalizeDifficulty("EXPERT"));
    assertEquals("INTERMEDIATE", ExerciseMasteryMath.normalizeDifficulty("intermediate"));
  }

  @Test
  void recommendGoalRepsUsesTemplateAlignedBaseline() {
    assertEquals(10, ExerciseMasteryMath.recommendGoalReps("BEGINNER", null));
    assertEquals(20, ExerciseMasteryMath.recommendGoalReps("INTERMEDIATE", null));
    assertEquals(30, ExerciseMasteryMath.recommendGoalReps("ADVANCED", null));
    // Legacy EXPERT → Advanced baseline 30
    assertEquals(30, ExerciseMasteryMath.recommendGoalReps("EXPERT", null));
  }

  @Test
  void recommendGoalRepsNudgesSoftlyTowardBaseline() {
    // From 10 toward INTERMEDIATE baseline 20 → step +5 → snap to 20
    assertEquals(20, ExerciseMasteryMath.recommendGoalReps("INTERMEDIATE", 10));
    // Legacy EXPERT treated as Advanced (30); from 10 → step +10 → 20 (mid snap)
    assertEquals(20, ExerciseMasteryMath.recommendGoalReps("EXPERT", 10));
  }

  @Test
  void rationaleMarksSoftTeacherApproval() {
    Map<String, Object> errors = new LinkedHashMap<>();
    errors.put("KNEES_IN", 5);
    errors.put("DEPTH_LOW", 2);
    String rationale =
        ExerciseMasteryMath.buildRationale(0.72, "ADVANCED", 30, errors);
    assertTrue(rationale.contains("ADVANCED"));
    assertTrue(rationale.contains("30"));
    assertTrue(rationale.contains("KNEES IN") || rationale.contains("KNEES_IN".replace('_', ' ')));
    assertTrue(rationale.toLowerCase().contains("soft recommendation"));
  }

  @Test
  void topErrorCodesOrderedByFrequency() {
    Map<String, Object> errors = new LinkedHashMap<>();
    errors.put("SAG", 1);
    errors.put("KNEES_IN", 9);
    errors.put("PIKE", 4);
    List<String> top = ExerciseMasteryMath.topErrorCodes(errors, 2);
    assertEquals(List.of("KNEES_IN", "PIKE"), top);
  }
}
