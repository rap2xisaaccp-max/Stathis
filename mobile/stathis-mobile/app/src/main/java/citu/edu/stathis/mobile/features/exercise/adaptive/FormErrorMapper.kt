package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Maps on-device form issue strings and backend [PostureRulesService] flags to the shared
 * [FormErrorCode] contract used by intervention logs.
 */
object FormErrorMapper {

    /**
     * Prefer backend rule flags, then on-device detector messages.
     * Returns null when there is no error signal at all.
     */
    fun resolve(flags: List<String>, formIssues: List<String>): FormErrorCode? {
        for (flag in flags) {
            val code = FormErrorCode.fromFlag(flag)
            if (code != FormErrorCode.UNKNOWN) return code
        }
        for (issue in formIssues) {
            val code = FormErrorCode.fromMessage(issue)
            if (code != FormErrorCode.UNKNOWN) return code
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

    private fun estimateFromIssues(formIssues: List<String>, confidence: Float): Double {
        if (formIssues.isEmpty()) return 0.0
        val issueFactor = (formIssues.size * 0.35).coerceAtMost(1.0)
        val confidencePenalty = (1.0 - confidence.coerceIn(0f, 1f)).toDouble() * 0.25
        return (issueFactor + confidencePenalty).coerceIn(0.0, 1.0)
    }
}
