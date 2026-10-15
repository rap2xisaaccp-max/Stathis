package edu.cit.stathis.task.enums;

/**
 * Teacher-facing bands are BEGINNER / INTERMEDIATE / ADVANCED.
 * {@link #EXPERT} remains only for reading legacy DB rows and is always
 * treated as {@link #ADVANCED} via {@link #canonical()}.
 */
public enum ExerciseDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    /** @deprecated Legacy; use {@link #ADVANCED}. Kept for persisted rows. */
    @Deprecated
    EXPERT;

    /** Collapse legacy EXPERT onto Advanced for APIs and scoring. */
    public ExerciseDifficulty canonical() {
        return this == EXPERT ? ADVANCED : this;
    }

    public static ExerciseDifficulty fromTeacherInput(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Exercise difficulty is required");
        }
        String normalized = rawValue.trim().toUpperCase().replace(' ', '_');
        return switch (normalized) {
            case "BEGINNER" -> BEGINNER;
            case "INTERMEDIATE" -> INTERMEDIATE;
            case "ADVANCED", "EXPERT" -> ADVANCED;
            default -> throw new IllegalArgumentException(
                    "Invalid exercise difficulty. Must be one of: BEGINNER, INTERMEDIATE, ADVANCED");
        };
    }
}