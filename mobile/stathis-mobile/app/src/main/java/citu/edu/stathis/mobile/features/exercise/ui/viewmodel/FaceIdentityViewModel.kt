package citu.edu.stathis.mobile.features.exercise.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.exercise.data.facerecognition.FaceRecognitionService
import citu.edu.stathis.mobile.features.profile.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FaceIdentityViewModel @Inject constructor(
    private val faceRecognitionService: FaceRecognitionService,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val faceRegistered: Boolean = false,
        val enrolledEmbedding: FloatArray? = null,
        val errorMessage: String? = null,
        val registrationSuccess: Boolean = false,
        val isSaving: Boolean = false,
        val statusText: String = "Position your face in the frame",
        val lastSimilarity: Float = 0f,
        val registrationSamplesReady: Boolean = false,
        val registrationSampleCount: Int = 0,
        val identityVerified: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val verifySession = FaceRecognitionService.MatchSession()
    private val registrationSamples = mutableListOf<FloatArray>()

    init {
        refreshEnrollment()
    }

    fun refreshEnrollment() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val resp = profileRepository.getFaceEmbedding()
            if (resp.success && resp.data != null) {
                val embedding = resp.data.embedding?.let { faceRecognitionService.embeddingFromJson(it) }
                val registered = resp.data.faceRegistered && embedding != null
                _state.value = _state.value.copy(
                    isLoading = false,
                    faceRegistered = registered,
                    enrolledEmbedding = embedding,
                    errorMessage = if (!registered && resp.data.faceRegistered) {
                        "Please re-register your face. Biometric model was upgraded."
                    } else null
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    faceRegistered = false,
                    enrolledEmbedding = null,
                    errorMessage = resp.message
                )
            }
        }
    }

    fun resetVerification() {
        verifySession.reset()
        _state.value = _state.value.copy(
            statusText = "Look at the camera to verify your identity.",
            lastSimilarity = 0f,
            identityVerified = false
        )
    }

    fun clearSessionVerification() {
        verifySession.reset()
        _state.value = _state.value.copy(
            identityVerified = false,
            statusText = "Position your face in the frame",
            lastSimilarity = 0f
        )
    }

    /**
     * One-time biometric verification for this exercise session.
     */
    fun onVerificationProbe(
        embedding: FloatArray?,
        qualityMessage: String? = null
    ): Boolean {
        if (_state.value.identityVerified) return true
        val enrolled = _state.value.enrolledEmbedding ?: run {
            _state.value = _state.value.copy(statusText = "No registered face found for this account.")
            return false
        }
        val progress = verifySession.onProbe(
            service = faceRecognitionService,
            probe = embedding,
            enrolled = enrolled,
            qualityMessage = qualityMessage
        )
        _state.value = _state.value.copy(
            statusText = progress.statusText,
            lastSimilarity = progress.similarity,
            identityVerified = progress.verified
        )
        return progress.verified
    }

    fun onRegistrationProbe(
        embedding: FloatArray?,
        qualityMessage: String? = null
    ) {
        if (_state.value.isSaving) return
        if (embedding == null) {
            _state.value = _state.value.copy(
                statusText = qualityMessage ?: "Looking for your face..."
            )
            return
        }

        // Keep registration cluster consistent (reject outliers vs current mean)
        if (registrationSamples.isNotEmpty()) {
            val mean = averageEmbeddings(registrationSamples)
            val sim = faceRecognitionService.cosineSimilarity(embedding, mean)
            if (sim < FaceRecognitionService.MATCH_THRESHOLD - 0.05f) {
                _state.value = _state.value.copy(
                    statusText = "Keep the same face centered — sample rejected."
                )
                return
            }
        }

        registrationSamples.add(embedding)
        val needed = REGISTRATION_SAMPLES
        while (registrationSamples.size > needed) {
            registrationSamples.removeAt(0)
        }
        val count = registrationSamples.size
        _state.value = _state.value.copy(
            statusText = if (count < needed) {
                "Hold still… capturing biometric sample $count/$needed"
            } else {
                "Samples ready. Tap Register face to save."
            },
            registrationSampleCount = count,
            registrationSamplesReady = count >= needed
        )
    }

    fun registerCapturedSamples() {
        if (_state.value.isSaving) return
        if (registrationSamples.size < REGISTRATION_SAMPLES) {
            _state.value = _state.value.copy(
                errorMessage = "Hold still until $REGISTRATION_SAMPLES face samples are captured."
            )
            return
        }
        registerEmbedding(averageEmbeddings(registrationSamples.takeLast(REGISTRATION_SAMPLES)))
    }

    fun registerEmbedding(embedding: FloatArray) {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, errorMessage = null, statusText = "Saving biometric profile...")
        viewModelScope.launch {
            val json = faceRecognitionService.embeddingToJson(embedding)
            val resp = profileRepository.registerFace(json)
            _state.value = if (resp.success) {
                registrationSamples.clear()
                _state.value.copy(
                    isSaving = false,
                    registrationSuccess = true,
                    faceRegistered = true,
                    enrolledEmbedding = embedding,
                    statusText = "Face registered successfully",
                    registrationSamplesReady = false,
                    registrationSampleCount = 0
                )
            } else {
                _state.value.copy(
                    isSaving = false,
                    errorMessage = resp.message ?: "Failed to save face data.",
                    statusText = "Registration failed. Try again."
                )
            }
        }
    }

    private fun averageEmbeddings(samples: List<FloatArray>): FloatArray {
        val size = samples.first().size
        val sum = FloatArray(size)
        for (sample in samples) {
            for (i in 0 until size) sum[i] += sample[i]
        }
        val n = samples.size.toFloat()
        for (i in sum.indices) sum[i] /= n
        var norm = 0.0
        for (v in sum) norm += v * v
        val denom = kotlin.math.sqrt(norm).toFloat().coerceAtLeast(1e-10f)
        for (i in sum.indices) sum[i] /= denom
        return sum
    }

    fun faceService(): FaceRecognitionService = faceRecognitionService

    companion object {
        private const val REGISTRATION_SAMPLES = 6
    }
}
