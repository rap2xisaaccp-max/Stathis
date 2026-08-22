package edu.cit.stathis.adaptive.service;

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
          new Copy("Chest / torso dropping", "Torso collapsing or not staying upright."),
          FormErrorCode.PIKE,
          new Copy("Hips too high", "Hips rising above a straight push-up line."),
          FormErrorCode.LEGS_BENT,
          new Copy("Legs not straight", "Knees bent during a straight-leg movement."));

  public static String label(FormErrorCode code) {
    Copy copy = COPY.get(code);
    if (copy == null) {
      return code == null ? "Unknown form error" : code.name().replace('_', ' ');
    }
    return copy.label();
  }

  public static String explanation(FormErrorCode code) {
    Copy copy = COPY.get(code);
    return copy == null ? "" : copy.explanation();
  }
}
