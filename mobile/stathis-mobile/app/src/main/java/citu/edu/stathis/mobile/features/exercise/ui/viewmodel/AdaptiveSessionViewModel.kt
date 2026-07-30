package citu.edu.stathis.mobile.features.exercise.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.exercise.adaptive.AdaptiveFeedbackEngine
import citu.edu.stathis.mobile.features.exercise.adaptive.DeliveredFeedback
import citu.edu.stathis.mobile.features.exercise.adaptive.FormErrorMapper
import citu.edu.stathis.mobile.features.exercise.adaptive.RctExperimentPrefs
import citu.edu.stathis.mobile.features.exercise.data.OnDeviceFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AdaptiveSessionViewModel @Inject constructor(
    private val engine: AdaptiveFeedbackEngine
) : ViewModel() {

    private val _feedback = MutableStateFlow<DeliveredFeedback?>(null)
    val feedback: StateFlow<DeliveredFeedback?> = _feedback.asStateFlow()

    private val _highlight = MutableStateFlow(false)
    val highlight: StateFlow<Boolean> = _highlight.asStateFlow()

    private val _highlightLandmarks = MutableStateFlow<Set<Int>>(emptySet())
    val highlightLandmarks: StateFlow<Set<Int>> = _highlightLandmarks.asStateFlow()

    private val _highlightBones = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val highlightBones: StateFlow<List<Pair<Int, Int>>> = _highlightBones.asStateFlow()

    fun startSession(
        exerciseType: String,
        taskId: String? = null,
        classroomId: String? = null,
        staticControl: Boolean = false,
        sessionContext: String = RctExperimentPrefs.CONTEXT_TASK
    ) {
        engine.startSession(exerciseType, taskId, classroomId, staticControl, sessionContext)
    }

    fun onExerciseFeedback(feedback: OnDeviceFeedback) {
        viewModelScope.launch {
            val flags = feedback.backendFlags
            val severity =
                FormErrorMapper.estimateSeverity(
                    formIssues = feedback.formIssues,
                    confidence = feedback.confidence,
                    flags = flags,
                    ruleSeverity = feedback.ruleSeverity
                )
            val delivered =
                engine.onFormSignal(
                    formIssues = feedback.formIssues,
                    flags = flags,
                    severity = severity,
                    currentReps = feedback.repCount,
                    visibilityOk = feedback.formIssues.none {
                        it.contains("visible", ignoreCase = true)
                    }
                )
            publishDelivery(delivered)
        }
    }

    fun flushAndEnd() {
        viewModelScope.launch {
            engine.flush()
            engine.endSession()
            publishDelivery(null)
        }
    }

    private fun publishDelivery(delivered: DeliveredFeedback?) {
        _feedback.value = delivered?.takeIf { it.showTextBanner }
        _highlight.value = delivered?.highlightJoints == true
        _highlightLandmarks.value = delivered?.highlightLandmarkIds.orEmpty()
        _highlightBones.value = delivered?.highlightBones.orEmpty()
    }
}
