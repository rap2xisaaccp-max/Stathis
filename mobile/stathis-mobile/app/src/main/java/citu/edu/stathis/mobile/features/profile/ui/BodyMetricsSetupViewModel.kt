package citu.edu.stathis.mobile.features.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.profile.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

@HiltViewModel
class BodyMetricsSetupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val success: Boolean = false,
        val firstName: String = "",
        val lastName: String = "",
        val profilePictureUrl: String? = null,
        val birthdate: String = "",
        val heightCm: String = "",
        val weightKg: String = ""
    )

    private val _state = MutableStateFlow(UiState(isLoading = true))
    val state: StateFlow<UiState> = _state

    init {
        loadProfile()
    }

    fun loadProfile() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val resp = profileRepository.getUserProfile()
            if (resp.success && resp.data != null) {
                val profile = resp.data
                val heightCm = profile.heightInMeters?.let { meters ->
                    (meters * 100.0).let { cm ->
                        if (cm % 1.0 == 0.0) cm.toInt().toString() else "%.1f".format(cm)
                    }
                }.orEmpty()
                val weightKg = profile.weightInKg?.let { kg ->
                    if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg)
                }.orEmpty()
                _state.value = UiState(
                    isLoading = false,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    profilePictureUrl = profile.profilePictureUrl,
                    birthdate = profile.birthdate.orEmpty(),
                    heightCm = heightCm,
                    weightKg = weightKg
                )
            } else {
                _state.value = UiState(
                    isLoading = false,
                    errorMessage = resp.message ?: "Failed to load profile."
                )
            }
        }
    }

    fun onBirthdateChange(value: String) {
        _state.value = _state.value.copy(birthdate = value, errorMessage = null)
    }

    fun onHeightCmChange(value: String) {
        _state.value = _state.value.copy(heightCm = value.filter { it.isDigit() || it == '.' }, errorMessage = null)
    }

    fun onWeightKgChange(value: String) {
        _state.value = _state.value.copy(weightKg = value.filter { it.isDigit() || it == '.' }, errorMessage = null)
    }

    fun saveBodyMetrics() {
        if (_state.value.isSaving) return
        val validationError = validate()
        if (validationError != null) {
            _state.value = _state.value.copy(errorMessage = validationError)
            return
        }

        val heightMeters = _state.value.heightCm.toDouble() / 100.0
        val weightKg = _state.value.weightKg.toDouble()

        _state.value = _state.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val resp = profileRepository.updateUserProfile(
                firstName = _state.value.firstName.ifBlank { "Student" },
                lastName = _state.value.lastName.ifBlank { "User" },
                birthdate = _state.value.birthdate.trim(),
                profilePictureUrl = _state.value.profilePictureUrl,
                heightInMeters = heightMeters,
                weightInKg = weightKg
            )
            _state.value = if (resp.success) {
                _state.value.copy(isSaving = false, success = true)
            } else {
                _state.value.copy(isSaving = false, errorMessage = resp.message ?: "Failed to save body metrics.")
            }
        }
    }

    private fun validate(): String? {
        val birthdate = _state.value.birthdate.trim()
        if (birthdate.isBlank()) return "Please enter your date of birth."
        try {
            val parsed = LocalDate.parse(birthdate)
            if (!parsed.isBefore(LocalDate.now())) return "Birthdate must be in the past."
        } catch (_: DateTimeParseException) {
            return "Use birthdate format YYYY-MM-DD."
        }

        val heightCm = _state.value.heightCm.toDoubleOrNull()
            ?: return "Please enter a valid height in centimeters."
        if (heightCm < 50.0 || heightCm > 250.0) return "Height must be between 50 and 250 cm."

        val weightKg = _state.value.weightKg.toDoubleOrNull()
            ?: return "Please enter a valid weight in kilograms."
        if (weightKg < 20.0 || weightKg > 300.0) return "Weight must be between 20 and 300 kg."

        return null
    }
}
