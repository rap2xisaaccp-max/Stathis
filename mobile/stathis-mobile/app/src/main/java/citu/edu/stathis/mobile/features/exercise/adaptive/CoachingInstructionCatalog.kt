package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Mirrors backend CoachingInstructionCatalog — reviewed exercise-scoped coaching copy.
 */
enum class InstructionIntensity {
    REMINDER,
    ESCALATION,
    REINFORCEMENT
}

data class CoachingInstruction(
    val messageCode: String,
    val reminder: String,
    val escalation: String,
    val reinforcement: String
) {
    fun textFor(intensity: InstructionIntensity): String =
        when (intensity) {
            InstructionIntensity.ESCALATION -> escalation
            InstructionIntensity.REINFORCEMENT -> reinforcement
            InstructionIntensity.REMINDER -> reminder
        }

    fun codeFor(intensity: InstructionIntensity): String = "$messageCode.${intensity.name}"
}

object CoachingInstructionCatalog {
    fun normalizeExercise(raw: String?): String {
        if (raw.isNullOrBlank()) return "UNKNOWN"
        val n = raw.trim().uppercase().replace('-', '_').replace(' ', '_')
        return when (n) {
            "PUSH_UP", "PUSH_UPS", "PUSHUP", "PUSHUPS" -> "PUSH_UP"
            "SQUAT", "SQUATS" -> "SQUATS"
            "GLUTE_BRIDGE", "GLUTE_BRIDGES" -> "GLUTE_BRIDGE"
            "STATIC_LUNGE", "STATIC_LUNGES", "LUNGE", "LUNGES" -> "STATIC_LUNGES"
            "LYING_LEG_RAISE", "LYING_LEG_RAISES", "LEG_RAISE", "LEG_RAISES" -> "LYING_LEG_RAISES"
            else -> n
        }
    }

    fun resolve(exerciseType: String?, errorCode: FormErrorCode?): CoachingInstruction {
        val code = errorCode ?: FormErrorCode.UNKNOWN
        val exercise = normalizeExercise(exerciseType)
        return catalog["$exercise|${code.name}"]
            ?: catalog["*|${code.name}"]
            ?: if (FormErrorClassifier.isTechnical(code)) {
                CoachingInstruction(
                    messageCode = "$exercise.${code.name}",
                    reminder = "Make sure the camera can see your body clearly.",
                    escalation = "Adjust your distance and lighting so joints stay in view.",
                    reinforcement = "Framing looks better. Stay in that position."
                )
            } else {
                // Never used for live physical delivery (engine requires hasReviewedInstruction).
                CoachingInstruction(
                    messageCode = "$exercise.${code.name}",
                    reminder = "",
                    escalation = "",
                    reinforcement = ""
                )
            }
    }

    fun messageText(
        exerciseType: String?,
        errorCode: FormErrorCode?,
        intensity: InstructionIntensity = InstructionIntensity.REMINDER
    ): String = resolve(exerciseType, errorCode).textFor(intensity)

    fun messageCode(
        exerciseType: String?,
        errorCode: FormErrorCode?,
        intensity: InstructionIntensity = InstructionIntensity.REMINDER
    ): String = resolve(exerciseType, errorCode).codeFor(intensity)

    fun hasReviewedInstruction(exerciseType: String?, errorCode: FormErrorCode?): Boolean {
        if (errorCode == null) return false
        val exercise = normalizeExercise(exerciseType)
        if (FormErrorClassifier.isTechnical(errorCode)) {
            if (errorCode == FormErrorCode.LOW_VISIBILITY) return false
            return catalog.containsKey("*|${errorCode.name}")
        }
        return FormErrorClassifier.isAllowedPhysical(exercise, errorCode) &&
            catalog.containsKey("$exercise|${errorCode.name}")
    }

    /** Exercise-scoped physical catalog keys that are allowed and reviewed. */
    fun reviewedPhysicalKeys(): Set<String> =
        catalog.keys.filter { key ->
            val parts = key.split('|')
            if (parts.size != 2 || parts[0] == "*") return@filter false
            val code = runCatching { FormErrorCode.valueOf(parts[1]) }.getOrNull() ?: return@filter false
            FormErrorClassifier.isAllowedPhysical(parts[0], code)
        }.toSet()

    private fun entry(
        exercise: String,
        code: FormErrorCode,
        reminder: String,
        escalation: String,
        reinforcement: String
    ): Pair<String, CoachingInstruction> {
        val base = (if (exercise == "*") "SHARED" else exercise) + "." + code.name
        return "$exercise|${code.name}" to CoachingInstruction(base, reminder, escalation, reinforcement)
    }

    private val catalog: Map<String, CoachingInstruction> =
        mapOf(
            entry(
                "SQUATS",
                FormErrorCode.DEPTH_LOW,
                "Squat deeper by bending your knees more.",
                "Lower your hips farther down before standing up.",
                "Good depth. Keep reaching that same low position."
            ),
            entry(
                "SQUATS",
                FormErrorCode.KNEES_IN,
                "Keep your knees aligned with your toes.",
                "Push your knees slightly outward as you lower.",
                "Good correction. Maintain that knee position."
            ),
            entry(
                "SQUATS",
                FormErrorCode.CHEST_UP,
                "Keep your torso upright as you squat.",
                "Do not lean forward as you lower; stay more upright.",
                "Upright torso looks good. Stay tall through the rep."
            ),
            entry(
                "PUSH_UP",
                FormErrorCode.SAG,
                "Keep your hips level with your shoulders.",
                "Tighten your core so your hips do not drop toward the floor.",
                "Solid plank line. Hold that core engagement."
            ),
            entry(
                "PUSH_UP",
                FormErrorCode.PIKE,
                "Keep your hips in line with your shoulders and ankles.",
                "Lower your hips slightly so they stay in one line with your shoulders.",
                "Good line. Stay long from shoulders to ankles."
            ),
            entry(
                "PUSH_UP",
                FormErrorCode.LOW_ROM,
                "Lower your chest closer to the floor.",
                "Lower your chest farther toward the floor before pressing up.",
                "Better range. Keep using that fuller path."
            ),
            entry(
                "GLUTE_BRIDGE",
                FormErrorCode.LOW_ROM,
                "Lift your hips higher toward the ceiling.",
                "Raise your hips higher at the top of the bridge.",
                "Strong lift. Hold that top position briefly."
            ),
            entry(
                "GLUTE_BRIDGE",
                FormErrorCode.SAG,
                "Keep your hips lifted; do not let them drop.",
                "Press your hips up and hold so they do not sag.",
                "Good hip height. Keep them lifted."
            ),
            entry(
                "STATIC_LUNGES",
                FormErrorCode.KNEES_IN,
                "Keep your knees tracking over your toes.",
                "Do not let either knee collapse inward as you lunge.",
                "Good knee path. Keep tracking over your toes."
            ),
            entry(
                "STATIC_LUNGES",
                FormErrorCode.DEPTH_LOW,
                "Bend the front knee deeper into the lunge.",
                "Lower until the front knee bends more before you stand up.",
                "Better depth. Keep that controlled drop."
            ),
            entry(
                "STATIC_LUNGES",
                FormErrorCode.CHEST_UP,
                "Keep your torso upright during the lunge.",
                "Do not lean forward as you bend; stay tall through your torso.",
                "Upright posture looks good. Hold that tall stance."
            ),
            entry(
                "LYING_LEG_RAISES",
                FormErrorCode.LEGS_BENT,
                "Keep your legs straighter as you raise them.",
                "Straighten your knees a bit more as you raise.",
                "Straighter legs. Maintain that length."
            ),
            entry(
                "LYING_LEG_RAISES",
                FormErrorCode.LOW_ROM,
                "Raise your legs higher with control.",
                "Lift your legs farther up before lowering them.",
                "Good range. Control the return as well."
            ),
            entry(
                "LYING_LEG_RAISES",
                FormErrorCode.SAG,
                "Keep your hips and torso on the floor.",
                "Do not let your hips lift off the floor as you raise your legs.",
                "Stable back. Keep that contact as you move."
            ),
            entry(
                "*",
                FormErrorCode.BODY_NOT_VISIBLE,
                "Keep your full body visible in the camera frame.",
                "Step back so shoulders, hips, and feet stay in view.",
                "Framing looks better. Stay in that position."
            ),
            // LOW_VISIBILITY: no live detector/framing/rules emitter. Kept only so an
            // explicit LOW_VISIBILITY flag stays on the technical path and never falls
            // through to physical "Adjust your form…" copy. Not a reviewed physical cue.
            entry(
                "*",
                FormErrorCode.LOW_VISIBILITY,
                "Make sure the camera can see your key joints.",
                "Improve lighting or center yourself so joints stay visible.",
                "Visibility improved. Continue with that setup."
            ),
            entry(
                "*",
                FormErrorCode.LOW_CONFIDENCE,
                "Hold still briefly so your form can be read clearly.",
                "Slow the movement a little until tracking is stable.",
                "Tracking looks steadier. Resume controlled reps."
            )
        )
}
