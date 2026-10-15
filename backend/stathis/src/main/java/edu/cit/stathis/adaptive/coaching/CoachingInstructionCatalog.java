package edu.cit.stathis.adaptive.coaching;

import edu.cit.stathis.adaptive.enums.FormErrorCode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static, reviewed instruction library keyed by normalized exercise type + form error.
 * Mobile clients mirror this catalog for offline fallbacks.
 */
public final class CoachingInstructionCatalog {

  private CoachingInstructionCatalog() {}

  private static final Map<String, CoachingInstruction> BY_KEY = buildCatalog();

  public static String normalizeExercise(String raw) {
    if (raw == null || raw.isBlank()) {
      return "UNKNOWN";
    }
    String n = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (n) {
      case "PUSH_UP", "PUSH_UPS", "PUSHUP", "PUSHUPS" -> "PUSH_UP";
      case "SQUAT", "SQUATS" -> "SQUATS";
      case "GLUTE_BRIDGE", "GLUTE_BRIDGES" -> "GLUTE_BRIDGE";
      case "STATIC_LUNGE", "STATIC_LUNGES", "LUNGE", "LUNGES" -> "STATIC_LUNGES";
      case "LYING_LEG_RAISE", "LYING_LEG_RAISES", "LEG_RAISE", "LEG_RAISES" -> "LYING_LEG_RAISES";
      default -> n;
    };
  }

  public static CoachingInstruction resolve(String exerciseType, FormErrorCode errorCode) {
    FormErrorCode code = errorCode != null ? errorCode : FormErrorCode.UNKNOWN;
    String exercise = normalizeExercise(exerciseType);
    CoachingInstruction specific = BY_KEY.get(key(exercise, code));
    if (specific != null) {
      return specific;
    }
    CoachingInstruction shared = BY_KEY.get(key("*", code));
    if (shared != null) {
      return shared;
    }
    if (code != null && code.isTechnical()) {
      return new CoachingInstruction(
          exercise + "." + code.name(),
          "Make sure the camera can see your body clearly.",
          "Adjust your distance and lighting so joints stay in view.",
          "Framing looks better. Stay in that position.");
    }
    return new CoachingInstruction(exercise + "." + code.name(), "", "", "");
  }

  public static String messageText(
      String exerciseType, FormErrorCode errorCode, InstructionIntensity intensity) {
    InstructionIntensity level = intensity != null ? intensity : InstructionIntensity.REMINDER;
    return resolve(exerciseType, errorCode).textFor(level);
  }

  public static String messageCode(
      String exerciseType, FormErrorCode errorCode, InstructionIntensity intensity) {
    InstructionIntensity level = intensity != null ? intensity : InstructionIntensity.REMINDER;
    return resolve(exerciseType, errorCode).codeFor(level);
  }

  public static boolean hasReviewedInstruction(String exerciseType, FormErrorCode errorCode) {
    if (errorCode == null) {
      return false;
    }
    if (errorCode.isTechnical()) {
      if (errorCode == FormErrorCode.LOW_VISIBILITY) {
        return false;
      }
      return BY_KEY.containsKey(key("*", errorCode));
    }
    return BY_KEY.containsKey(key(normalizeExercise(exerciseType), errorCode));
  }

  public static Set<String> reviewedPhysicalKeys() {
    return BY_KEY.keySet().stream()
        .filter(k -> !k.startsWith("*|"))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static String key(String exercise, FormErrorCode code) {
    return exercise + "|" + code.name();
  }

  private static Map<String, CoachingInstruction> buildCatalog() {
    return Map.ofEntries(
        // --- Squat ---
        entry(
            "SQUATS",
            FormErrorCode.DEPTH_LOW,
            "Squat deeper by bending your knees more.",
            "Lower your hips farther down before standing up.",
            "Good depth. Keep reaching that same low position."),
        entry(
            "SQUATS",
            FormErrorCode.KNEES_IN,
            "Keep your knees aligned with your toes.",
            "Push your knees slightly outward as you lower.",
            "Good correction. Maintain that knee position."),
        entry(
            "SQUATS",
            FormErrorCode.CHEST_UP,
            "Keep your torso upright as you squat.",
            "Do not lean forward as you lower; stay more upright.",
            "Upright torso looks good. Stay tall through the rep."),

        // --- Push-up ---
        entry(
            "PUSH_UP",
            FormErrorCode.SAG,
            "Keep your hips level with your shoulders.",
            "Tighten your core so your hips do not drop toward the floor.",
            "Solid plank line. Hold that core engagement."),
        entry(
            "PUSH_UP",
            FormErrorCode.PIKE,
            "Keep your hips in line with your shoulders and ankles.",
            "Lower your hips slightly so they stay in one line with your shoulders.",
            "Good line. Stay long from shoulders to ankles."),
        entry(
            "PUSH_UP",
            FormErrorCode.LOW_ROM,
            "Lower your chest closer to the floor.",
            "Lower your chest farther toward the floor before pressing up.",
            "Better range. Keep using that fuller path."),

        // --- Glute Bridge ---
        entry(
            "GLUTE_BRIDGE",
            FormErrorCode.LOW_ROM,
            "Lift your hips higher toward the ceiling.",
            "Raise your hips higher at the top of the bridge.",
            "Strong lift. Hold that top position briefly."),
        entry(
            "GLUTE_BRIDGE",
            FormErrorCode.SAG,
            "Keep your hips lifted; do not let them drop.",
            "Press your hips up and hold so they do not sag.",
            "Good hip height. Keep them lifted."),

        // --- Static Lunges ---
        entry(
            "STATIC_LUNGES",
            FormErrorCode.KNEES_IN,
            "Keep your knees tracking over your toes.",
            "Do not let either knee collapse inward as you lunge.",
            "Good knee path. Keep tracking over your toes."),
        entry(
            "STATIC_LUNGES",
            FormErrorCode.DEPTH_LOW,
            "Bend the front knee deeper into the lunge.",
            "Lower until the front knee bends more before you stand up.",
            "Better depth. Keep that controlled drop."),
        entry(
            "STATIC_LUNGES",
            FormErrorCode.CHEST_UP,
            "Keep your torso upright during the lunge.",
            "Do not lean forward as you bend; stay tall through your torso.",
            "Upright posture looks good. Hold that tall stance."),

        // --- Lying Leg Raises ---
        entry(
            "LYING_LEG_RAISES",
            FormErrorCode.LEGS_BENT,
            "Keep your legs straighter as you raise them.",
            "Straighten your knees a bit more as you raise.",
            "Straighter legs. Maintain that length."),
        entry(
            "LYING_LEG_RAISES",
            FormErrorCode.LOW_ROM,
            "Raise your legs higher with control.",
            "Lift your legs farther up before lowering them.",
            "Good range. Control the return as well."),
        entry(
            "LYING_LEG_RAISES",
            FormErrorCode.SAG,
            "Keep your hips and torso on the floor.",
            "Do not let your hips lift off the floor as you raise your legs.",
            "Stable back. Keep that contact as you move."),

        // Shared technical copy. LOW_VISIBILITY has no live emitter; retained so an
        // explicit flag never falls through to physical form-copy.
        entry(
            "*",
            FormErrorCode.BODY_NOT_VISIBLE,
            "Keep your full body visible in the camera frame.",
            "Step back so shoulders, hips, and feet stay in view.",
            "Framing looks better. Stay in that position."),
        entry(
            "*",
            FormErrorCode.LOW_VISIBILITY,
            "Make sure the camera can see your key joints.",
            "Improve lighting or center yourself so joints stay visible.",
            "Visibility improved. Continue with that setup."),
        entry(
            "*",
            FormErrorCode.LOW_CONFIDENCE,
            "Hold still briefly so your form can be read clearly.",
            "Slow the movement a little until tracking is stable.",
            "Tracking looks steadier. Resume controlled reps."));
  }

  private static Map.Entry<String, CoachingInstruction> entry(
      String exercise, FormErrorCode code, String reminder, String escalation, String reinforcement) {
    String base = ("*".equals(exercise) ? "SHARED" : exercise) + "." + code.name();
    return Map.entry(
        key(exercise, code),
        new CoachingInstruction(base, reminder, escalation, reinforcement));
  }
}
