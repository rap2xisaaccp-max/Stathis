package citu.edu.stathis.mobile.features.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.profile.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val success: Boolean = false,
        val firstName: String = "",
        val lastName: String = "",
        val birthdate: String = "",
        val profilePictureUrl: String? = null,
        val heightCm: String = "",
        val weightKg: String = "",
        val school: String = "",
        val course: String = "",
        val yearLevel: String = ""
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
                    val cm = meters * 100.0
                    if (cm % 1.0 == 0.0) cm.toInt().toString() else "%.1f".format(cm)
                }.orEmpty()
                val weightKg = profile.weightInKg?.let { kg ->
                    if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg)
                }.orEmpty()
                _state.value = UiState(
                    isLoading = false,
                    firstName = profile.firstName,
                    lastName = profile.lastName,
                    birthdate = profile.birthdate.orEmpty(),
                    profilePictureUrl = profile.profilePictureUrl,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    school = profile.school.orEmpty(),
                    course = profile.course.orEmpty(),
                    yearLevel = profile.yearLevel?.toString().orEmpty()
                )
            } else {
                _state.value = UiState(
                    isLoading = false,
                    errorMessage = resp.message ?: "Failed to load profile."
                )
            }
        }
    }

    fun onFirstNameChange(value: String) {
        _state.value = _state.value.copy(firstName = value, errorMessage = null)
    }

    fun onLastNameChange(value: String) {
        _state.value = _state.value.copy(lastName = value, errorMessage = null)
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

    fun onSchoolChange(value: String) {
        _state.value = _state.value.copy(school = value, errorMessage = null)
    }

    fun onCourseChange(value: String) {
        _state.value = _state.value.copy(course = value, errorMessage = null)
    }

    fun onYearLevelChange(value: String) {
        _state.value = _state.value.copy(yearLevel = value.filter { it.isDigit() }, errorMessage = null)
    }

    fun saveChanges() {
        if (_state.value.isSaving) return
        val current = _state.value
        if (current.firstName.isBlank() || current.lastName.isBlank()) {
            _state.value = current.copy(errorMessage = "First and last name are required.")
            return
        }

        val heightMeters = current.heightCm.toDoubleOrNull()?.div(100.0)
        val weightKg = current.weightKg.toDoubleOrNull()
        if (current.heightCm.isNotBlank() && heightMeters == null) {
            _state.value = current.copy(errorMessage = "Enter a valid height in centimeters.")
            return
        }
        if (current.weightKg.isNotBlank() && weightKg == null) {
            _state.value = current.copy(errorMessage = "Enter a valid weight in kilograms.")
            return
        }

        _state.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val userResp = profileRepository.updateUserProfile(
                firstName = current.firstName.trim(),
                lastName = current.lastName.trim(),
                birthdate = current.birthdate.trim().ifBlank { null },
                profilePictureUrl = current.profilePictureUrl,
                heightInMeters = heightMeters,
                weightInKg = weightKg
            )
            if (!userResp.success) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = userResp.message ?: "Failed to update profile."
                )
                return@launch
            }

            val studentResp = profileRepository.updateStudentProfile(
                school = current.school.trim().ifBlank { null },
                course = current.course.trim().ifBlank { null },
                yearLevel = current.yearLevel.toIntOrNull()
            )
            _state.value = if (studentResp.success) {
                _state.value.copy(isSaving = false, success = true)
            } else {
                _state.value.copy(
                    isSaving = false,
                    errorMessage = studentResp.message ?: "Failed to update student details."
                )
            }
        }
    }
}
