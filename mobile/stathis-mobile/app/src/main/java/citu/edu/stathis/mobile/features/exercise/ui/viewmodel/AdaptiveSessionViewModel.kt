package citu.edu.stathis.mobile.features.exercise.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveFeedbackEngine
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveSessionSummary
import citu.edu.stathis.mobile.features.exercise.adaptive.DeliveredFeedback
import citu.edu.stathis.mobile.features.exercise.adaptive.FormMasteryDto
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorMapper
import citu.edu.stathis.mobile.features.exercise.adaptive.FormEvidenceCapture
import citu.edu.stathis.mobile.features.exercise.adaptive.LatestFrameBuffer
import citu.edu.stathis.mobile.features.exercise.adaptive.LiveCoachingUiPolicy
import citu.edu.stathis.mobile.features.exercise.adaptive.PoseGeometry
import citu.edu.stathis.mobile.features.exercise.adaptive.StudentLearningProfileDto
import citu.edu.stathis.mobile.features.exercise.data.OnDeviceFeedback
import citu.edu.stathis.mobile.features.exercise.data.remote.api.AdaptiveApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class AdaptiveSessionViewModel @Inject constructor(
    private val engine: AdaptiveFeedbackEngine,
    private val adaptiveApi: AdaptiveApi,
    private val frameBuffer: LatestFrameBuffer,
    private val evidenceCapture: FormEvidenceCapture
) : ViewModel() {

    private val _feedback = MutableStateFlow<DeliveredFeedback?>(null)
    val feedback: StateFlow<DeliveredFeedback?> = _feedback.asStateFlow()

    private val _highlight = MutableStateFlow(false)
    val highlight: StateFlow<Boolean> = _highlight.asStateFlow()

    private val _highlightLandmarks = MutableStateFlow<Set<Int>>(emptySet())
    val highlightLandmarks: StateFlow<Set<Int>> = _highlightLandmarks.asStateFlow()

    private val _highlightBones = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val highlightBones: StateFlow<List<Pair<Int, Int>>> = _highlightBones.asStateFlow()

    private val _sessionSummary = MutableStateFlow(AdaptiveSessionSummary())
    val sessionSummary: StateFlow<AdaptiveSessionSummary> = _sessionSummary.asStateFlow()

    private val _learningProfile = MutableStateFlow<StudentLearningProfileDto?>(null)
    val learningProfile: StateFlow<StudentLearningProfileDto?> = _learningProfile.asStateFlow()

    private val _formMastery = MutableStateFlow<List<FormMasteryDto>>(emptyList())
    val formMastery: StateFlow<List<FormMasteryDto>> = _formMastery.asStateFlow()

    private val _profileLoading = MutableStateFlow(false)
    val profileLoading: StateFlow<Boolean> = _profileLoading.asStateFlow()

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    fun startSession(
        exerciseType: String,
        taskId: String? = null,
        classroomId: String? = null,
        attemptNumber: Int? = null
    ) {
        frameBuffer.clear()
        engine.startSession(exerciseType, taskId, classroomId, attemptNumber)
        _sessionSummary.value = AdaptiveSessionSummary()
    }

    fun onCopiedPreviewFrame(bitmap: Bitmap) {
        frameBuffer.updateFromBitmap(bitmap)
        // Fulfils any confirmed correction event whose snapshot had no frame to copy yet.
        evidenceCapture.onPreviewFrameAvailable()
    }

    fun onPoseGeometry(geometry: PoseGeometry) {
        frameBuffer.updatePose(geometry)
        evidenceCapture.onPreviewFrameAvailable()
    }

    fun onExerciseFeedback(feedback: OnDeviceFeedback, countingActive: Boolean = true) {
        viewModelScope.launch {
            if (!LiveCoachingUiPolicy.acceptPhysicalCoachingSignals(countingActive)) {
                engine.suppressPhysicalCoaching()
                publishDelivery(engine.activeFeedback())
                return@launch
            }
            val framingInvalid = feedback.framingInvalid
            val flags = if (framingInvalid) emptyList() else feedback.backendFlags
            val severity =
                FormErrorMapper.estimateSeverity(
                    formIssues = feedback.formIssues,
                    confidence = feedback.confidence,
                    flags = flags,
                    ruleSeverity = if (framingInvalid) null else feedback.ruleSeverity
                )
            val delivered =
                engine.onFormSignal(
                    formIssues = feedback.formIssues,
                    flags = flags,
                    severity = severity,
                    currentReps = feedback.repCount,
                    visibilityOk = !framingInvalid
                )
            publishDelivery(delivered)
            _sessionSummary.value = engine.sessionSummary()
        }
    }

    fun suppressPhysicalCoaching() {
        viewModelScope.launch {
            engine.suppressPhysicalCoaching()
            publishDelivery(engine.activeFeedback())
        }
    }

    fun flushAndEnd() {
        viewModelScope.launch {
            // Snapshot uploads run after the intervention batch, so they must survive the
            // student leaving this screen and the ViewModel being cleared mid-flush.
            withContext(NonCancellable) {
                engine.flush()
                engine.endSession()
            }
            _sessionSummary.value = engine.sessionSummary()
            publishDelivery(null)
            loadLearningProfileAndMastery()
        }
    }

    fun snapshotSessionSummary(): AdaptiveSessionSummary {
        val summary = engine.sessionSummary()
        _sessionSummary.value = summary
        return summary
    }

    fun loadLearningProfileAndMastery() {
        viewModelScope.launch {
            _profileLoading.value = true
            _profileError.value = null
            try {
                _learningProfile.value = adaptiveApi.getOwnProfile()
                _formMastery.value = adaptiveApi.getOwnFormMastery()
            } catch (t: Throwable) {
                Timber.w(t, "Failed to load coaching profile/mastery")
                _profileError.value = t.message ?: "Could not load coaching data"
            } finally {
                _profileLoading.value = false
            }
        }
    }

    private fun publishDelivery(delivered: DeliveredFeedback?) {
        _feedback.value = LiveCoachingUiPolicy.studentTextBanner(delivered)
        _highlight.value = delivered?.highlightJoints == true
        _highlightLandmarks.value = delivered?.highlightLandmarkIds.orEmpty()
        _highlightBones.value = delivered?.highlightBones.orEmpty()
    }
}
