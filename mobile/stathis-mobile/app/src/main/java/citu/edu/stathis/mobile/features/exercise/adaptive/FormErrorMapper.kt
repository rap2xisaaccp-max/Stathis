package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Maps on-device form issue strings and backend [PostureRulesService] flags to the shared
 * [FormErrorCode] contract used by coaching, highlights, TTS, and teacher evidence.
 *
 * Physical codes are accepted only when they are valid for the current exercise. Unmapped or
 * cross-exercise signals become [FormErrorCode.UNKNOWN] so they cannot create coaching evidence.
 */
object FormErrorMapper {

    /**
     * Prefer backend rule flags, then on-device detector messages.
     * Returns null when there is no error signal at all.
     */
    fun resolve(
        flags: List<String>,
        formIssues: List<String>,
        exerciseType: String? = null
    ): FormErrorCode? {
        val exercise = CoachingInstructionCatalog.normalizeExercise(exerciseType)
        for (flag in flags) {
            val code = parseExactFlag(flag)
            accept(code, exercise)?.let { return it }
        }
        for (issue in formIssues) {
            val code = mapIssue(issue, exercise)
            accept(code, exercise)?.let { return it }
        }
        if (flags.isNotEmpty() || formIssues.isNotEmpty()) {
            return FormErrorCode.UNKNOWN
        }
        return null
    }

    /**
     * Severity from on-device issues and optional backend rule severity.
     * Rule severity (angle/metric based) takes precedence when present.
     */
    fun estimateSeverity(
        formIssues: List<String>,
        confidence: Float,
        flags: List<String> = emptyList(),
        ruleSeverity: Double? = null
    ): Double {
        if (ruleSeverity != null && ruleSeverity > 0.0) {
            return ruleSeverity.coerceIn(0.0, 1.0)
        }
        if (flags.isNotEmpty()) {
            val flagSeverity = severityFromFlags(flags)
            if (formIssues.isEmpty()) return flagSeverity
            val issueSeverity = estimateFromIssues(formIssues, confidence)
            return maxOf(flagSeverity, issueSeverity)
        }
        return estimateFromIssues(formIssues, confidence)
    }

    fun severityFromFlags(flags: List<String>): Double {
        if (flags.isEmpty()) return 0.0
        val max =
            flags.maxOf { flag ->
                when (flag.trim().lowercase()) {
                    "depth_low" -> 0.55
                    "knees_in" -> 0.65
                    "chest_up" -> 0.5
                    "sag" -> 0.7
                    "pike" -> 0.6
                    "low_rom" -> 0.55
                    else -> 0.4
                }
            }
        val multi = ((flags.size - 1) * 0.05).coerceAtMost(0.15)
        return (max + multi).coerceIn(0.0, 1.0)
    }

    private fun accept(code: FormErrorCode, exercise: String): FormErrorCode? {
        if (code == FormErrorCode.UNKNOWN) return null
        if (FormErrorClassifier.isTechnical(code)) return code
        if (FormErrorClassifier.isAllowedPhysical(exercise, code)) return code
        return null
    }

    private fun parseExactFlag(flag: String): FormErrorCode {
        if (flag.isBlank()) return FormErrorCode.UNKNOWN
        val normalized = flag.trim().uppercase().replace('-', '_').replace(' ', '_')
        return runCatching { FormErrorCode.valueOf(normalized) }.getOrDefault(FormErrorCode.UNKNOWN)
    }

    private fun mapIssue(raw: String, exercise: String): FormErrorCode {
        val n = raw.trim().lowercase()
        if (n.isEmpty()) return FormErrorCode.UNKNOWN
        if (n.contains("confidence")) return FormErrorCode.LOW_CONFIDENCE
        if (n.contains("visible") || n.contains("visibility")) return FormErrorCode.BODY_NOT_VISIBLE
        return when (exercise) {
            "SQUATS" -> mapSquat(n)
            "PUSH_UP" -> mapPushUp(n)
            "STATIC_LUNGES" -> mapLunge(n)
            "GLUTE_BRIDGE" -> mapGlute(n)
            "LYING_LEG_RAISES" -> mapLegRaise(n)
            else -> FormErrorCode.UNKNOWN
        }
    }

    private fun mapSquat(n: String): FormErrorCode =
        when {
            n.contains("depth") ||
                n.contains("deeper") ||
                n.contains("parallel") ||
                n.contains("sit back") ||
                n.contains("hinge") -> FormErrorCode.DEPTH_LOW
            n.contains("extend your knees") || n.contains("stand tall") -> FormErrorCode.UNKNOWN
            n.contains("knee") -> FormErrorCode.KNEES_IN
            n.contains("chest") || n.contains("torso") -> FormErrorCode.CHEST_UP
            else -> FormErrorCode.UNKNOWN
        }

    private fun mapPushUp(n: String): FormErrorCode =
        when {
            n.contains("pike") ||
                n.contains("head to heels") ||
                n.contains("straight line") -> FormErrorCode.PIKE
            n.contains("sag") -> FormErrorCode.SAG
            n.contains("lower your chest") ||
                n.contains("closer to the ground") ||
                n.contains("closer to the floor") ||
                n.contains("elbow") -> FormErrorCode.LOW_ROM
            else -> FormErrorCode.UNKNOWN
        }

    private fun mapLunge(n: String): FormErrorCode =
        when {
            n.contains("depth") ||
                n.contains("deeper") ||
                n.contains("lower until") ||
                n.contains("drop your back knee") -> FormErrorCode.DEPTH_LOW
            n.contains("back leg") || n.contains("between lunges") -> FormErrorCode.UNKNOWN
            n.contains("knee") || n.contains("toes") || n.contains("tracking") -> FormErrorCode.KNEES_IN
            n.contains("torso") ||
                n.contains("chest") ||
                n.contains("upright") ||
                n.contains("lean") -> FormErrorCode.CHEST_UP
            else -> FormErrorCode.UNKNOWN
        }

    private fun mapGlute(n: String): FormErrorCode =
        when {
            n.contains("with control before") || n.contains("next bridge") -> FormErrorCode.UNKNOWN
            n.contains("sag") ||
                n.contains("evenly") ||
                (n.contains("hips") && n.contains("drop")) -> FormErrorCode.SAG
            n.contains("higher") ||
                n.contains("ceiling") ||
                n.contains("full bridge") ||
                n.contains("lift") ||
                n.contains("drive") -> FormErrorCode.LOW_ROM
            else -> FormErrorCode.UNKNOWN
        }

    private fun mapLegRaise(n: String): FormErrorCode =
        when {
            n.contains("abrupt") || n.contains("together") -> FormErrorCode.UNKNOWN
            n.contains("hips and torso") ||
                n.contains("on the floor") ||
                n.contains("lower back") -> FormErrorCode.SAG
            n.contains("higher") -> FormErrorCode.LOW_ROM
            n.contains("straighter") || n.contains("straighten") || n.contains("bent") ->
                FormErrorCode.LEGS_BENT
            else -> FormErrorCode.UNKNOWN
        }

    private fun estimateFromIssues(formIssues: List<String>, confidence: Float): Double {
        if (formIssues.isEmpty()) return 0.0
        val issueFactor = (formIssues.size * 0.35).coerceAtMost(1.0)
        val confidencePenalty = (1.0 - confidence.coerceIn(0f, 1f)).toDouble() * 0.25
        return (issueFactor + confidencePenalty).coerceIn(0.0, 1.0)
    }
}
