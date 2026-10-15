package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.coaching.InstructionIntensity;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.service.FormErrorCopy;
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

  @Test
  void reviewedCatalogCoverageEqualsAllowedMatrix() {
    java.util.Set<String> expected =
        java.util.Set.of(
            "PUSH_UP|PIKE",
            "PUSH_UP|SAG",
            "PUSH_UP|LOW_ROM",
            "SQUATS|DEPTH_LOW",
            "SQUATS|KNEES_IN",
            "SQUATS|CHEST_UP",
            "STATIC_LUNGES|DEPTH_LOW",
            "STATIC_LUNGES|KNEES_IN",
            "STATIC_LUNGES|CHEST_UP",
            "GLUTE_BRIDGE|LOW_ROM",
            "GLUTE_BRIDGE|SAG",
            "LYING_LEG_RAISES|LEGS_BENT",
            "LYING_LEG_RAISES|LOW_ROM",
            "LYING_LEG_RAISES|SAG");
    assertEquals(expected, CoachingInstructionCatalog.reviewedPhysicalKeys());
  }

  @Test
  void eachAllowedPairHasDistinctNonEmptyReminderAndEscalation() {
    String[][] matrix = {
      {"PUSH_UP", "PIKE"},
      {"PUSH_UP", "SAG"},
      {"PUSH_UP", "LOW_ROM"},
      {"SQUATS", "DEPTH_LOW"},
      {"SQUATS", "KNEES_IN"},
      {"SQUATS", "CHEST_UP"},
      {"STATIC_LUNGES", "DEPTH_LOW"},
      {"STATIC_LUNGES", "KNEES_IN"},
      {"STATIC_LUNGES", "CHEST_UP"},
      {"GLUTE_BRIDGE", "LOW_ROM"},
      {"GLUTE_BRIDGE", "SAG"},
      {"LYING_LEG_RAISES", "LEGS_BENT"},
      {"LYING_LEG_RAISES", "LOW_ROM"},
      {"LYING_LEG_RAISES", "SAG"}
    };
    for (String[] pair : matrix) {
      FormErrorCode code = FormErrorCode.valueOf(pair[1]);
      String reminder =
          CoachingInstructionCatalog.messageText(pair[0], code, InstructionIntensity.REMINDER);
      String escalation =
          CoachingInstructionCatalog.messageText(pair[0], code, InstructionIntensity.ESCALATION);
      assertFalse(reminder.isBlank(), pair[0] + "|" + pair[1]);
      assertFalse(escalation.isBlank(), pair[0] + "|" + pair[1]);
      assertNotEquals(reminder, escalation, pair[0] + "|" + pair[1]);
      assertTrue(CoachingInstructionCatalog.hasReviewedInstruction(pair[0], code));
    }
  }

  @Test
  void copyDoesNotMentionUnguaranteedAnatomy() {
    String gluteRom =
        CoachingInstructionCatalog.messageText(
                "GLUTE_BRIDGE", FormErrorCode.LOW_ROM, InstructionIntensity.REMINDER)
            + " "
            + CoachingInstructionCatalog.messageText(
                "GLUTE_BRIDGE", FormErrorCode.LOW_ROM, InstructionIntensity.ESCALATION);
    assertFalse(gluteRom.toLowerCase().contains("heel"));
    String lungeDepth =
        CoachingInstructionCatalog.messageText(
                "STATIC_LUNGES", FormErrorCode.DEPTH_LOW, InstructionIntensity.REMINDER)
            + " "
            + CoachingInstructionCatalog.messageText(
                "STATIC_LUNGES", FormErrorCode.DEPTH_LOW, InstructionIntensity.ESCALATION);
    assertTrue(lungeDepth.toLowerCase().contains("front knee"));
    assertFalse(lungeDepth.toLowerCase().contains("back knee"));
    String lungeKnees =
        CoachingInstructionCatalog.messageText(
                "STATIC_LUNGES", FormErrorCode.KNEES_IN, InstructionIntensity.REMINDER)
            + " "
            + CoachingInstructionCatalog.messageText(
                "STATIC_LUNGES", FormErrorCode.KNEES_IN, InstructionIntensity.ESCALATION);
    assertFalse(lungeKnees.toLowerCase().contains("front knee"));
    String llrRom =
        CoachingInstructionCatalog.messageText(
                "LYING_LEG_RAISES", FormErrorCode.LOW_ROM, InstructionIntensity.REMINDER)
            + " "
            + CoachingInstructionCatalog.messageText(
                "LYING_LEG_RAISES", FormErrorCode.LOW_ROM, InstructionIntensity.ESCALATION);
    String llrSag =
        CoachingInstructionCatalog.messageText(
                "LYING_LEG_RAISES", FormErrorCode.SAG, InstructionIntensity.REMINDER)
            + " "
            + CoachingInstructionCatalog.messageText(
                "LYING_LEG_RAISES", FormErrorCode.SAG, InstructionIntensity.ESCALATION);
    assertFalse(llrRom.toLowerCase().contains("grounded"));
    assertFalse(llrRom.toLowerCase().contains("on the floor"));
    assertFalse(llrSag.toLowerCase().contains("higher"));
    assertNotEquals(llrRom, llrSag);
    String squatChest =
        CoachingInstructionCatalog.messageText(
            "SQUATS", FormErrorCode.CHEST_UP, InstructionIntensity.REMINDER);
    assertTrue(squatChest.toLowerCase().contains("torso"));
    assertFalse(squatChest.toLowerCase().contains("chest dropping"));
    String pushRom =
        CoachingInstructionCatalog.messageText(
                "PUSH_UP", FormErrorCode.LOW_ROM, InstructionIntensity.REMINDER)
            + " "
            + CoachingInstructionCatalog.messageText(
                "PUSH_UP", FormErrorCode.LOW_ROM, InstructionIntensity.ESCALATION);
    assertTrue(pushRom.toLowerCase().contains("chest"));
    assertFalse(pushRom.toLowerCase().contains("elbow"));
  }

  @Test
  void teacherCopyMatchesReviewedPairs() {
    assertEquals("Torso leaning", FormErrorCopy.label(FormErrorCode.CHEST_UP, "SQUATS"));
    assertEquals(
        "Knee drifting inward", FormErrorCopy.label(FormErrorCode.KNEES_IN, "STATIC_LUNGES"));
    assertTrue(
        FormErrorCopy.explanation(FormErrorCode.DEPTH_LOW, "STATIC_LUNGES")
            .toLowerCase()
            .contains("front knee"));
    assertEquals("Shallow push-up", FormErrorCopy.label(FormErrorCode.LOW_ROM, "PUSH_UP"));
    assertFalse(CoachingInstructionCatalog.hasReviewedInstruction("SQUATS", FormErrorCode.SAG));
    assertTrue(
        CoachingInstructionCatalog.messageText("SQUATS", FormErrorCode.SAG, InstructionIntensity.REMINDER)
            .isEmpty());
  }
}
