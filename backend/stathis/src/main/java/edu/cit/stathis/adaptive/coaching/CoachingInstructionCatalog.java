package edu.cit.stathis.adaptive.coaching;

import edu.cit.stathis.adaptive.enums.FormErrorCode;
import java.util.Locale;
import java.util.Map;

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
    return new CoachingInstruction(
        exercise + "." + code.name(),
        "Adjust your form and try the next repetition.",
        "Slow down and check your alignment before continuing.",
        "Good adjustment. Keep that form.");
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

  private static String key(String exercise, FormErrorCode code) {
    return exercise + "|" + code.name();
  }

  private static Map<String, CoachingInstruction> buildCatalog() {
    return Map.ofEntries(
        // --- Squat ---
        entry(
            "SQUATS",
            FormErrorCode.DEPTH_LOW,
            "Lower until your thighs are near parallel with the floor.",
            "Sit your hips back and down more before standing up.",
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
            "Keep your chest lifted as you squat.",
            "Brace your torso and look forward while you lower.",
            "Nice chest position. Stay tall through the rep."),

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
            "Keep a straight line from head to heels.",
            "Lower your hips slightly so your body stays in one line.",
            "Good line. Stay long from shoulders to heels."),
        entry(
            "PUSH_UP",
            FormErrorCode.LOW_ROM,
            "Lower your chest closer to the floor.",
            "Bend your elbows more on the way down before pressing up.",
            "Better range. Keep using that fuller path."),

        // --- Glute Bridge ---
        entry(
            "GLUTE_BRIDGE",
            FormErrorCode.LOW_ROM,
            "Lift your hips higher toward the ceiling.",
            "Drive through your heels and squeeze your glutes at the top.",
            "Strong lift. Hold that top position briefly."),
        entry(
            "GLUTE_BRIDGE",
            FormErrorCode.SAG,
            "Keep your hips lifted evenly.",
            "Do not let your hips drop; press them up and hold.",
            "Good hip height. Keep both sides level."),
        entry(
            "GLUTE_BRIDGE",
            FormErrorCode.CHEST_UP,
            "Keep your shoulders grounded and stable.",
            "Press your upper back into the floor while lifting your hips.",
            "Stable base. Nice control through the bridge."),

        // --- Static Lunges ---
        entry(
            "STATIC_LUNGES",
            FormErrorCode.KNEES_IN,
            "Keep your front knee tracking over your toes.",
            "Guide your front knee outward slightly as you bend.",
            "Good knee path. Stay stacked over the front foot."),
        entry(
            "STATIC_LUNGES",
            FormErrorCode.DEPTH_LOW,
            "Lower until both knees bend smoothly.",
            "Drop your back knee a little closer to the floor with control.",
            "Better depth. Keep that controlled drop."),
        entry(
            "STATIC_LUNGES",
            FormErrorCode.CHEST_UP,
            "Keep your torso upright during the lunge.",
            "Stand tall and avoid leaning forward as you bend.",
            "Upright posture looks good. Hold that tall stance."),

        // --- Lying Leg Raises ---
        entry(
            "LYING_LEG_RAISES",
            FormErrorCode.LEGS_BENT,
            "Keep your legs straighter as you raise them.",
            "Straighten your knees a bit more before lifting.",
            "Straighter legs. Maintain that length."),
        entry(
            "LYING_LEG_RAISES",
            FormErrorCode.LOW_ROM,
            "Raise your legs higher with control.",
            "Lift until your hips stay grounded and legs reach a higher angle.",
            "Good range. Control the return as well."),
        entry(
            "LYING_LEG_RAISES",
            FormErrorCode.SAG,
            "Keep your lower back steady on the floor.",
            "Press your low back down gently while you raise your legs.",
            "Stable back. Keep that contact as you move."),

        // --- Shared visibility / confidence ---
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
