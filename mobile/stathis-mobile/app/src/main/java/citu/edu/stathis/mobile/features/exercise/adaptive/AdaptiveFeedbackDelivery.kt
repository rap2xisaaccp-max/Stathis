package citu.edu.stathis.mobile.features.exercise.adaptive

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Highlight + TTS delivery used after a confirmed intervention claim. */
interface CoachingDelivery {
    fun ensureInitialized()
    fun resetSessionSpeech()
    fun deliver(feedback: DeliveredFeedback, now: Long = System.currentTimeMillis()): DeliveredFeedback
    fun speakTechnical(message: String, now: Long = System.currentTimeMillis())
    fun onTechnicalConditionCleared()
    fun stopSpeaking()
}

/**
 * Delivers adaptive feedback channels (text coordination, skeleton highlight targets, TTS).
 *
 * Physical coaching: highlight + TTS + evidence, gated behind a logged intervention id.
 * Camera/technical guidance: text + TTS only, via [speakTechnical] — never an intervention.
 */
@Singleton
class AdaptiveFeedbackDelivery @Inject constructor(
    @ApplicationContext private val context: Context
) : CoachingDelivery {
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val speechGate = CoachingTtsSpeechGate()
    @Volatile private var speakingLane: CoachingTtsLane? = null
    private val deliveryLog = CopyOnWriteArrayList<ModalityDeliveryPlanner.DeliveryEvent>()

    override fun ensureInitialized() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyLanguage()
                ready.set(true)
                val now = System.currentTimeMillis()
                val pending = speechGate.markReady(now)
                if (pending?.action == CoachingTtsAction.SPEAK_NOW) {
                    submitSpeak(pending.message, now, pending.lane ?: CoachingTtsLane.PHYSICAL)
                }
            } else {
                speechGate.markInitFailed()
                Timber.w("TTS init failed with status=%s", status)
            }
        }
    }

    override fun resetSessionSpeech() {
        speechGate.resetAll()
        speakingLane = null
        tts?.stop()
    }

    /**
     * Applies modality channels for a logged intervention. Returns the UI-facing payload.
     * Does nothing for speak/highlight when [feedback.interventionId] is blank.
     */
    override fun deliver(feedback: DeliveredFeedback, now: Long): DeliveredFeedback {
        val planned =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = feedback.interventionId,
                modality = feedback.modality,
                errorCode = feedback.errorCode,
                message = feedback.message,
                exerciseType = feedback.exerciseType
            )

        if (planned.speak) {
            speakPhysical(planned.message, now)
        }

        deliveryLog.add(
            ModalityDeliveryPlanner.DeliveryEvent(
                interventionId = planned.interventionId,
                modality = planned.modality,
                channel = planned.deliveryChannel,
                spoke = planned.speak,
                highlighted = planned.highlightJoints,
                epochMs = now
            )
        )
        return planned
    }

    override fun speakTechnical(message: String, now: Long) {
        ensureInitialized()
        val decision = speechGate.requestTechnical(message, now)
        if (decision.action == CoachingTtsAction.SPEAK_NOW) {
            submitSpeak(decision.message, now, CoachingTtsLane.TECHNICAL)
        }
    }

    override fun onTechnicalConditionCleared() {
        speechGate.clearTechnical()
        if (speakingLane == CoachingTtsLane.TECHNICAL) {
            tts?.stop()
            speakingLane = null
        }
    }

    private fun speakPhysical(message: String, now: Long) {
        ensureInitialized()
        val decision = speechGate.requestPhysical(message, now)
        if (decision.action == CoachingTtsAction.SPEAK_NOW) {
            submitSpeak(decision.message, now, CoachingTtsLane.PHYSICAL)
        }
    }

    private fun submitSpeak(message: String, now: Long, lane: CoachingTtsLane) {
        if (message.isBlank()) return
        val engine = tts
        if (engine == null || !ready.get()) {
            Timber.d("TTS not ready; holding %s speech until engine is ready", lane)
            return
        }
        val utteranceId = "apsle-${lane.name.lowercase()}-$now"
        val result = engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            speechGate.markSpoken(lane, now, message)
            speakingLane = lane
        } else {
            Timber.w("TTS speak failed status=%s lane=%s", result, lane)
        }
    }

    private fun applyLanguage() {
        val engine = tts ?: return
        val us = engine.setLanguage(Locale.US)
        if (us == TextToSpeech.LANG_MISSING_DATA || us == TextToSpeech.LANG_NOT_SUPPORTED) {
            Timber.w("TTS Locale.US unsupported (%s); trying Locale.ENGLISH", us)
            val en = engine.setLanguage(Locale.ENGLISH)
            if (en == TextToSpeech.LANG_MISSING_DATA || en == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.w("TTS English unsupported (%s); using engine default", en)
            }
        }
    }

    override fun stopSpeaking() {
        speechGate.cancelPending()
        speechGate.resetAll()
        speakingLane = null
        tts?.stop()
    }

    fun deliveryEventsForTests(): List<ModalityDeliveryPlanner.DeliveryEvent> = deliveryLog.toList()

    fun clearDeliveryLogForTests() {
        deliveryLog.clear()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
        speechGate.resetAll()
        speechGate.markInitFailed()
        speakingLane = null
    }
}
