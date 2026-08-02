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

/**
 * Delivers adaptive feedback channels (text coordination, skeleton highlight targets, TTS).
 * Visual / TTS are supporting features gated behind a logged intervention id.
 */
@Singleton
class AdaptiveFeedbackDelivery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var lastSpokenAt = 0L
    private val deliveryLog = CopyOnWriteArrayList<ModalityDeliveryPlanner.DeliveryEvent>()

    fun ensureInitialized() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ready.set(true)
            } else {
                Timber.w("TTS init failed with status=%s", status)
            }
        }
    }

    /**
     * Applies modality channels for a logged intervention. Returns the UI-facing payload.
     * Does nothing for speak/highlight when [feedback.interventionId] is blank.
     */
    fun deliver(feedback: DeliveredFeedback, now: Long = System.currentTimeMillis()): DeliveredFeedback {
        val planned =
            ModalityDeliveryPlanner.toDeliveredFeedback(
                interventionId = feedback.interventionId,
                modality = feedback.modality,
                errorCode = feedback.errorCode,
                message = feedback.message,
                exerciseType = feedback.exerciseType
            )

        if (planned.speak) {
            speak(planned.message, now)
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

    fun speak(message: String, now: Long = System.currentTimeMillis()) {
        if (message.isBlank()) return
        ensureInitialized()
        if (now - lastSpokenAt < 2500L) return
        lastSpokenAt = now
        val engine = tts ?: return
        if (!ready.get()) {
            Timber.d("TTS not ready; skipping speak")
            return
        }
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "apsle-$now")
    }

    fun stopSpeaking() {
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
        lastSpokenAt = 0L
    }
}
