package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import java.util.Map;

/** Teacher-facing physical-form copy. Technical codes are never stored as evidence. */
public final class FormErrorCopy {

  private FormErrorCopy() {}

  private record Copy(String label, String explanation) {}

  private static final Map<FormErrorCode, Copy> COPY =
      Map.of(
          FormErrorCode.SAG,
          new Copy("Hips sagging", "Hips or torso dropping below a straight body line."),
          FormErrorCode.LOW_ROM,
          new Copy("Incomplete movement", "Movement did not travel through the full useful range."),
          FormErrorCode.DEPTH_LOW,
          new Copy("Not deep enough", "Squat or lunge did not reach enough depth."),
          FormErrorCode.KNEES_IN,
          new Copy(
              "Knees moving inward", "Knees collapsing inward instead of tracking over the toes."),
          FormErrorCode.CHEST_UP,
          new Copy("Torso leaning", "Torso not staying upright."),
          FormErrorCode.PIKE,
          new Copy("Hips too high", "Hips rising above a straight push-up line."),
          FormErrorCode.LEGS_BENT,
          new Copy("Legs not straight", "Knees bent during a straight-leg movement."));

  private static final Map<String, Copy> BY_EXERCISE =
      Map.ofEntries(
          entry("SQUATS", FormErrorCode.DEPTH_LOW, "Not deep enough", "Squat did not reach enough depth."),
          entry(
              "SQUATS",
              FormErrorCode.KNEES_IN,
              "Knees moving inward",
              "Knees collapsing inward instead of tracking over the toes."),
          entry(
              "SQUATS",
              FormErrorCode.CHEST_UP,
              "Torso leaning",
              "Torso leaned forward instead of staying more upright during the squat."),
          entry("PUSH_UP", FormErrorCode.PIKE, "Hips too high", "Hips rising above a straight push-up line."),
          entry("PUSH_UP", FormErrorCode.SAG, "Hips sagging", "Hips dropping below a straight push-up line."),
          entry("PUSH_UP", FormErrorCode.LOW_ROM, "Shallow push-up", "Chest did not lower through a useful range."),
          entry(
              "STATIC_LUNGES",
              FormErrorCode.DEPTH_LOW,
              "Not deep enough",
              "The front knee did not bend enough to reach useful lunge depth."),
          entry(
              "STATIC_LUNGES",
              FormErrorCode.KNEES_IN,
              "Knee drifting inward",
              "A knee collapsed inward instead of tracking over the toes."),
          entry(
              "STATIC_LUNGES",
              FormErrorCode.CHEST_UP,
              "Torso leaning",
              "Torso collapsing or leaning instead of staying upright in the lunge."),
          entry(
              "GLUTE_BRIDGE",
              FormErrorCode.LOW_ROM,
              "Hips not high enough",
              "Bridge did not lift the hips through a full range."),
          entry(
              "GLUTE_BRIDGE",
              FormErrorCode.SAG,
              "Hips dropping",
              "Hips dropped instead of staying lifted in the bridge."),
          entry(
              "LYING_LEG_RAISES",
              FormErrorCode.LEGS_BENT,
              "Legs not straight",
              "Knees bent during a straight-leg raise."),
          entry(
              "LYING_LEG_RAISES",
              FormErrorCode.LOW_ROM,
              "Legs not high enough",
              "Legs did not raise through a useful range."),
          entry(
              "LYING_LEG_RAISES",
              FormErrorCode.SAG,
              "Lower back lifting",
              "Hips or lower back left the floor during the raise."));

  public static String label(FormErrorCode code) {
    return label(code, null);
  }

  public static String explanation(FormErrorCode code) {
    return explanation(code, null);
  }

  public static String label(FormErrorCode code, String exerciseType) {
    if (code == null) {
      return "Unknown form error";
    }
    Copy specific = BY_EXERCISE.get(key(exerciseType, code));
    if (specific != null) {
      return specific.label();
    }
    Copy copy = COPY.get(code);
    return copy == null ? code.name().replace('_', ' ') : copy.label();
  }

  public static String explanation(FormErrorCode code, String exerciseType) {
    if (code == null) {
      return "";
    }
    Copy specific = BY_EXERCISE.get(key(exerciseType, code));
    if (specific != null) {
      return specific.explanation();
    }
    Copy copy = COPY.get(code);
    return copy == null ? "" : copy.explanation();
  }

  private static String key(String exerciseType, FormErrorCode code) {
    return CoachingInstructionCatalog.normalizeExercise(exerciseType) + "|" + code.name();
  }

  private static Map.Entry<String, Copy> entry(
      String exercise, FormErrorCode code, String label, String explanation) {
    return Map.entry(exercise + "|" + code.name(), new Copy(label, explanation));
  }
}
