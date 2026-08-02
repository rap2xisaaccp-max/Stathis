package edu.cit.stathis.task.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExerciseDifficultyTest {

  @Test
  void canonicalMapsExpertToAdvanced() {
    assertEquals(ExerciseDifficulty.ADVANCED, ExerciseDifficulty.EXPERT.canonical());
    assertEquals(ExerciseDifficulty.BEGINNER, ExerciseDifficulty.BEGINNER.canonical());
  }

  @Test
  void fromTeacherInputAcceptsThreeBandsAndMapsExpert() {
    assertEquals(ExerciseDifficulty.BEGINNER, ExerciseDifficulty.fromTeacherInput("BEGINNER"));
    assertEquals(ExerciseDifficulty.INTERMEDIATE, ExerciseDifficulty.fromTeacherInput("intermediate"));
    assertEquals(ExerciseDifficulty.ADVANCED, ExerciseDifficulty.fromTeacherInput("ADVANCED"));
    assertEquals(ExerciseDifficulty.ADVANCED, ExerciseDifficulty.fromTeacherInput("EXPERT"));
  }

  @Test
  void fromTeacherInputRejectsUnknown() {
    assertThrows(IllegalArgumentException.class, () -> ExerciseDifficulty.fromTeacherInput("HARD"));
  }
}
