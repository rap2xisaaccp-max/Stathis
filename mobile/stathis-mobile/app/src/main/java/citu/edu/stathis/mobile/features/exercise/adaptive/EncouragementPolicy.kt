package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Occasional praise for valid reps. A rejected rep never receives a line.
 *
 * Initial gaps (2–4 valid reps, motivational after 4 in a row, 20s between
 * motivational lines) are starting values and can be tuned after testing.
 */
class EncouragementPolicy(
    private val nextGap: () -> Int = { (2..4).random() },
    private val motivationalAfterValidStreak: Int = 4,
    private val motivationalCooldownMs: Long = 20_000L
) {
    private var repsSincePraise: Int = 0
    private var gap: Int = nextGap().coerceAtLeast(2)
    private var streak: Int = 0
    private var lastMotivationalAt: Long = 0L
    private var phraseIndex: Int = 0

    fun reset() {
        repsSincePraise = 0
        gap = nextGap().coerceAtLeast(2)
        streak = 0
        lastMotivationalAt = 0L
        phraseIndex = 0
    }

    fun onRejected() {
        streak = 0
    }

    /**
     * @param speak returns true when the line was actually spoken.
     * A blocked line stays due so the next valid rep can try again.
     */
    fun onValidRep(now: Long, speak: (String) -> Boolean) {
        streak += 1
        repsSincePraise += 1
        val motivationalDue =
            streak >= motivationalAfterValidStreak &&
                (lastMotivationalAt == 0L || now - lastMotivationalAt >= motivationalCooldownMs)
        val complimentDue = repsSincePraise >= gap
        if (!motivationalDue && !complimentDue) return
        val line = if (motivationalDue) MOTIVATIONAL else COMPLIMENTS[phraseIndex % COMPLIMENTS.size]
        if (!speak(line)) return
        repsSincePraise = 0
        gap = nextGap().coerceAtLeast(2)
        if (motivationalDue) {
            streak = 0
            lastMotivationalAt = now
        } else {
            phraseIndex += 1
        }
    }

    companion object {
        val COMPLIMENTS: List<String> =
            listOf(
                "Good rep!",
                "Nice form!",
                "Great job!",
                "Keep it up!",
                "You're doing well!"
            )

        const val MOTIVATIONAL: String = "You're holding that form. Keep going."
    }
}
