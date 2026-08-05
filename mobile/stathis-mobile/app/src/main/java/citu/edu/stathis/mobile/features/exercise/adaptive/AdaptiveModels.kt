package citu.edu.stathis.mobile.features.exercise.adaptive

enum class FeedbackModality {
    VERBAL_TEXT,
    VISUAL_HIGHLIGHT,
    VERBAL_TTS
}

enum class PolicySource {
    EXPLORE,
    EXPLOIT,
    DEFAULT
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
            val n = raw.trim().lowercase().replace(' ', '_')
            return when {
                n.contains("depth") || n.contains("deeper") || n.contains("parallel") -> DEPTH_LOW
                n.contains("knee") -> KNEES_IN
                n.contains("chest") || n.contains("back straight") -> CHEST_UP
                n.contains("pike") || n.contains("head to heels") -> PIKE
                n.contains("sag") -> SAG
                n.contains("trunk") || n.contains("rom") || n.contains("flexion") -> LOW_ROM
                n.contains("confidence") -> LOW_CONFIDENCE
                n.contains("straighter") || n.contains("legs") -> LEGS_BENT
                n.contains("visible") || n.contains("shoulders") || n.contains("hips") -> BODY_NOT_VISIBLE
                else -> runCatching { valueOf(raw.trim().uppercase()) }.getOrDefault(UNKNOWN)
            }
        }

        fun fromFlag(flag: String?): FormErrorCode {
            if (flag.isNullOrBlank()) return UNKNOWN
            return runCatching { valueOf(flag.trim().uppercase()) }.getOrElse { fromMessage(flag) }
        }
    }
}
