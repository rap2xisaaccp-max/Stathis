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
     * Live accuracy %, pose-state labels, and form-cue cards are not student progress UI
     * on classroom/practice. They remain available on explicit debug overlays such as
     * exercise_test.
     */
    fun showLiveFormQualityOverlay(explicitDebugOverlayEnabled: Boolean): Boolean =
        explicitDebugOverlayEnabled

    /**
     * Results / post-attempt accuracy belongs after the exercise, not on the live overlay.
     * Classroom/practice hide live form-quality metrics; the results card still shows Accuracy.
     */
    fun showPostAttemptAccuracy(): Boolean = true

    /**
     * Physical detector correction bullets are never rendered on the student live path.
     * Callers still pass raw [formIssues] into [AdaptiveFeedbackEngine.onFormSignal].
     */
    fun studentLiveFormCueIssues(@Suppress("UNUSED_PARAMETER") formIssues: List<String>): List<String> =
        emptyList()

    /** Physical coaching and rep-driven signals run only while identity-verified counting is active. */
    fun acceptPhysicalCoachingSignals(countingActive: Boolean): Boolean = countingActive
}
