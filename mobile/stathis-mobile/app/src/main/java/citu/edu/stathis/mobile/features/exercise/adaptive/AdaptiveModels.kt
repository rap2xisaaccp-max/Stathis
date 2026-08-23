package citu.edu.stathis.mobile.features.exercise.adaptive

enum class FeedbackModality {
    VERBAL_TEXT,
    VISUAL_HIGHLIGHT,
    DEMONSTRATION,
    VERBAL_TTS
}

enum class PolicySource {
    EXPLORE,
    EXPLOIT,
    DEFAULT,
    STATIC_CONTROL
}

enum class FormErrorCode {
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

    companion object {
        fun fromMessage(raw: String?): FormErrorCode {
            if (raw.isNullOrBlank()) return UNKNOWN
            val normalized = raw.trim().uppercase().replace('-', '_').replace(' ', '_')
            return runCatching { valueOf(normalized) }.getOrDefault(UNKNOWN)
        }

        fun fromFlag(flag: String?): FormErrorCode {
            if (flag.isNullOrBlank()) return UNKNOWN
            val normalized = flag.trim().uppercase().replace('-', '_').replace(' ', '_')
            return runCatching { valueOf(normalized) }.getOrDefault(UNKNOWN)
        }
    }
}
