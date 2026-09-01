package citu.edu.stathis.mobile.features.exercise.adaptive

/**
 * Student-facing live UI policy: confirmed physical errors are highlight + TTS only.
 * Technical/camera guidance may show as text and also be spoken. Detector strings stay internal.
 */
object LiveCoachingUiPolicy {
    const val TECHNICAL_CHANNEL = "technical"

    /**
     * On-screen live text is allowed only for technical/camera guidance.
     * Claimed physical coaching must not put [DeliveredFeedback.message] on screen.
     * Technical guidance may set [DeliveredFeedback.speak] without becoming a physical claim.
     */
    fun studentTextBanner(delivered: DeliveredFeedback?): DeliveredFeedback? =
        delivered?.takeIf {
            it.deliveryChannel == TECHNICAL_CHANNEL &&
                it.showTextBanner &&
                it.interventionId.isBlank() &&
                !it.highlightJoints
        }

    /** Live on-screen banner is camera guidance only — never a coaching sentence. */
    fun showCameraGuidanceBanner(message: String?, deliveryChannel: String?): Boolean =
        !message.isNullOrBlank() && deliveryChannel == TECHNICAL_CHANNEL

    /** Classifier / Flags / Top-3 only when an explicit debug overlay is enabled. */
    fun showClassifierDebug(explicitDebugOverlayEnabled: Boolean): Boolean =
        explicitDebugOverlayEnabled

    /**
     * One live Accuracy % indicator on classroom/practice. This is student progress,
     * not a coaching channel.
     */
    fun showLiveAccuracyIndicator(classroomOrPracticeLiveScreen: Boolean): Boolean =
        classroomOrPracticeLiveScreen

    /**
     * Duplicate form-quality UI (pose-state labels, extra "A" percentage bar, form-cue
     * cards) stays off the student live path. exercise_test may still use [showClassifierDebug].
     */
    fun showDuplicateLiveFormQualityUi(explicitDebugOverlayEnabled: Boolean): Boolean =
        explicitDebugOverlayEnabled

    /**
     * Results / post-attempt accuracy belongs after the exercise and uses the submitted value.
     */
    fun showPostAttemptAccuracy(): Boolean = true

    /**
     * Do not paint a fabricated 0% before [SessionAccuracyTracker] has an assessable sample.
     */
    fun formatLiveAccuracyPercent(hasSamples: Boolean, accuracyPercent: Int): String =
        if (hasSamples) "$accuracyPercent%" else "—"

    /** Classroom and practice share one live Accuracy surface. */
    fun liveAccuracyIndicatorCount(
        classroomOrPracticeLiveScreen: Boolean,
        showDuplicateFormPercent: Boolean = false
    ): Int =
        listOf(
            showLiveAccuracyIndicator(classroomOrPracticeLiveScreen),
            showDuplicateFormPercent
        ).count { it }

    /**
     * Physical detector correction bullets are never rendered on the student live path.
     * Callers still pass raw [formIssues] into [AdaptiveFeedbackEngine.onFormSignal].
     */
    fun studentLiveFormCueIssues(@Suppress("UNUSED_PARAMETER") formIssues: List<String>): List<String> =
        emptyList()

    /** Physical coaching and rep-driven signals run only while identity-verified counting is active. */
    fun acceptPhysicalCoachingSignals(countingActive: Boolean): Boolean = countingActive
}
