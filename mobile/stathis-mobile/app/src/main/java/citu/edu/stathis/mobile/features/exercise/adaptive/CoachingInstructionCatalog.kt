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
            ?: CoachingInstruction(
                messageCode = "$exercise.${code.name}",
                reminder = "Adjust your form and try the next repetition.",
                escalation = "Slow down and check your alignment before continuing.",
                reinforcement = "Good adjustment. Keep that form."
            )
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
                "Lower until your thighs are near parallel with the floor.",
                "Sit your hips back and down more before standing up.",
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
                "Keep your chest lifted as you squat.",
                "Brace your torso and look forward while you lower.",
                "Nice chest position. Stay tall through the rep."
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
                "Keep a straight line from head to heels.",
                "Lower your hips slightly so your body stays in one line.",
                "Good line. Stay long from shoulders to heels."
            ),
            entry(
                "PUSH_UP",
                FormErrorCode.LOW_ROM,
                "Lower your chest closer to the floor.",
                "Bend your elbows more on the way down before pressing up.",
                "Better range. Keep using that fuller path."
            ),
            entry(
                "GLUTE_BRIDGE",
                FormErrorCode.LOW_ROM,
                "Lift your hips higher toward the ceiling.",
                "Drive through your heels and squeeze your glutes at the top.",
                "Strong lift. Hold that top position briefly."
            ),
            entry(
                "GLUTE_BRIDGE",
                FormErrorCode.SAG,
                "Keep your hips lifted evenly.",
                "Do not let your hips drop; press them up and hold.",
                "Good hip height. Keep both sides level."
            ),
            entry(
                "GLUTE_BRIDGE",
                FormErrorCode.CHEST_UP,
                "Keep your shoulders grounded and stable.",
                "Press your upper back into the floor while lifting your hips.",
                "Stable base. Nice control through the bridge."
            ),
            entry(
                "STATIC_LUNGES",
                FormErrorCode.KNEES_IN,
                "Keep your front knee tracking over your toes.",
                "Guide your front knee outward slightly as you bend.",
                "Good knee path. Stay stacked over the front foot."
            ),
            entry(
                "STATIC_LUNGES",
                FormErrorCode.DEPTH_LOW,
                "Lower until both knees bend smoothly.",
                "Drop your back knee a little closer to the floor with control.",
                "Better depth. Keep that controlled drop."
            ),
            entry(
                "STATIC_LUNGES",
                FormErrorCode.CHEST_UP,
                "Keep your torso upright during the lunge.",
                "Stand tall and avoid leaning forward as you bend.",
                "Upright posture looks good. Hold that tall stance."
            ),
            entry(
                "LYING_LEG_RAISES",
                FormErrorCode.LEGS_BENT,
                "Keep your legs straighter as you raise them.",
                "Straighten your knees a bit more before lifting.",
                "Straighter legs. Maintain that length."
            ),
            entry(
                "LYING_LEG_RAISES",
                FormErrorCode.LOW_ROM,
                "Raise your legs higher with control.",
                "Lift until your hips stay grounded and legs reach a higher angle.",
                "Good range. Control the return as well."
            ),
            entry(
                "LYING_LEG_RAISES",
                FormErrorCode.SAG,
                "Keep your lower back steady on the floor.",
                "Press your low back down gently while you raise your legs.",
                "Stable back. Keep that contact as you move."
            ),
            entry(
                "*",
                FormErrorCode.BODY_NOT_VISIBLE,
                "Keep your full body visible in the camera frame.",
                "Step back so shoulders, hips, and feet stay in view.",
                "Framing looks better. Stay in that position."
            ),
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
