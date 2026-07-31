package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.service.ExerciseMasteryMath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ExerciseMasteryMathTest {

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
  void recommendDifficultyUsesFourBands() {
    assertEquals("BEGINNER", ExerciseMasteryMath.recommendDifficulty(0.10));
    assertEquals("INTERMEDIATE", ExerciseMasteryMath.recommendDifficulty(0.50));
    assertEquals("ADVANCED", ExerciseMasteryMath.recommendDifficulty(0.70));
    assertEquals("EXPERT", ExerciseMasteryMath.recommendDifficulty(0.90));
  }

  @Test
  void recommendGoalRepsUsesDifficultyBaseline() {
    assertEquals(8, ExerciseMasteryMath.recommendGoalReps("BEGINNER", null));
    assertEquals(12, ExerciseMasteryMath.recommendGoalReps("INTERMEDIATE", null));
    assertEquals(15, ExerciseMasteryMath.recommendGoalReps("ADVANCED", null));
    assertEquals(20, ExerciseMasteryMath.recommendGoalReps("EXPERT", null));
  }

  @Test
  void recommendGoalRepsNudgesSoftlyTowardBaseline() {
    // From 8 toward INTERMEDIATE baseline 12 → step +2
    assertEquals(10, ExerciseMasteryMath.recommendGoalReps("INTERMEDIATE", 8));
    // Cap step at ±4
    assertEquals(12, ExerciseMasteryMath.recommendGoalReps("EXPERT", 8));
  }

  @Test
  void rationaleMarksSoftTeacherApproval() {
    Map<String, Object> errors = new LinkedHashMap<>();
    errors.put("KNEES_IN", 5);
    errors.put("DEPTH_LOW", 2);
    String rationale =
        ExerciseMasteryMath.buildRationale(0.72, "ADVANCED", 15, errors);
    assertTrue(rationale.contains("ADVANCED"));
    assertTrue(rationale.contains("15"));
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
