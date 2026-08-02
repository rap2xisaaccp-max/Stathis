package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.coaching.InstructionIntensity;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import org.junit.jupiter.api.Test;

class CoachingInstructionCatalogTest {

  @Test
  void fiveExercisesHaveDistinctPrimaryReminderMessages() {
    String squat =
        CoachingInstructionCatalog.messageText(
            "SQUATS", FormErrorCode.DEPTH_LOW, InstructionIntensity.REMINDER);
    String pushUp =
        CoachingInstructionCatalog.messageText(
            "PUSH_UP", FormErrorCode.SAG, InstructionIntensity.REMINDER);
    String bridge =
        CoachingInstructionCatalog.messageText(
            "GLUTE_BRIDGE", FormErrorCode.LOW_ROM, InstructionIntensity.REMINDER);
    String lunge =
        CoachingInstructionCatalog.messageText(
            "STATIC_LUNGES", FormErrorCode.KNEES_IN, InstructionIntensity.REMINDER);
    String legRaise =
        CoachingInstructionCatalog.messageText(
            "LYING_LEG_RAISES", FormErrorCode.LEGS_BENT, InstructionIntensity.REMINDER);

    assertEquals(5, java.util.Set.of(squat, pushUp, bridge, lunge, legRaise).size());
    assertFalse(squat.toLowerCase().contains("fix form"));
  }

  @Test
  void messageCodesAreExerciseScoped() {
    assertEquals(
        "SQUATS.DEPTH_LOW.REMINDER",
        CoachingInstructionCatalog.messageCode(
            "squat", FormErrorCode.DEPTH_LOW, InstructionIntensity.REMINDER));
    assertEquals(
        "PUSH_UP.SAG.ESCALATION",
        CoachingInstructionCatalog.messageCode(
            "PUSH_UP", FormErrorCode.SAG, InstructionIntensity.ESCALATION));
  }

  @Test
  void escalationDiffersFromReminder() {
    String reminder =
        CoachingInstructionCatalog.messageText(
            "SQUATS", FormErrorCode.KNEES_IN, InstructionIntensity.REMINDER);
    String escalation =
        CoachingInstructionCatalog.messageText(
            "SQUATS", FormErrorCode.KNEES_IN, InstructionIntensity.ESCALATION);
    assertNotEquals(reminder, escalation);
  }
}
