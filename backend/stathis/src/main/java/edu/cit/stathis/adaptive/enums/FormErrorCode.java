package edu.cit.stathis.adaptive.enums;

/**
 * Canonical form-error codes shared with mobile detectors and PostureRulesService flags.
 */
public enum FormErrorCode {
  DEPTH_LOW,
  KNEES_IN,
  CHEST_UP,
  PIKE,
  SAG,
  LOW_ROM,
  LOW_VISIBILITY,
  LOW_CONFIDENCE,
  LEGS_BENT,
  BODY_NOT_VISIBLE,
  UNKNOWN;

  public static FormErrorCode fromFlag(String flagOrMessage) {
    if (flagOrMessage == null || flagOrMessage.isBlank()) {
      return UNKNOWN;
    }
    String normalized = flagOrMessage.trim().toLowerCase().replace(' ', '_');
    return switch (normalized) {
      case "depth_low", "go_deeper_to_at_least_parallel." -> DEPTH_LOW;
      case "knees_in", "push_knees_outward_over_toes." -> KNEES_IN;
      case "chest_up", "keep_chest_up." -> CHEST_UP;
      case "pike", "keep_a_straight_line_from_head_to_heels." -> PIKE;
      case "sag", "avoid_sagging_hips." -> SAG;
      case "low_rom", "increase_trunk_flexion." -> LOW_ROM;
      case "low_detection_confidence", "low_confidence" -> LOW_CONFIDENCE;
      case "ensure_major_body_parts_are_visible.",
          "ensure_shoulders,_elbows,_and_wrists_are_visible.",
          "keep_shoulders,_hips,_and_knees_visible.",
          "keep_your_hips,_knees,_and_ankles_visible.",
          "keep_hips,_knees,_and_ankles_visible.",
          "body_not_visible" -> BODY_NOT_VISIBLE;
      case "keep_your_legs_straighter_for_better_control.", "legs_bent" -> LEGS_BENT;
      default -> {
        try {
          yield FormErrorCode.valueOf(flagOrMessage.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
          yield UNKNOWN;
        }
      }
    };
  }
}
