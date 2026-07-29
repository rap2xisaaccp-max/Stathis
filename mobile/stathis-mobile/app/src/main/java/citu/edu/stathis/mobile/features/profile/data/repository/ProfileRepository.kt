package citu.edu.stathis.mobile.features.profile.data.repository

import citu.edu.stathis.mobile.core.data.models.ClientResponse
import citu.edu.stathis.mobile.features.auth.data.models.UserResponseDTO

interface ProfileRepository {

    suspend fun getUserProfile(): ClientResponse<UserResponseDTO>

    suspend fun updateUserProfile(
        firstName: String,
        lastName: String,
        birthdate: String?,
        profilePictureUrl: String?,
        heightInMeters: Double? = null,
        weightInKg: Double? = null
    ): ClientResponse<UserResponseDTO>

    suspend fun updateStudentProfile(
        school: String?,
        course: String?,
        yearLevel: Int?
    ): ClientResponse<UserResponseDTO>
}
