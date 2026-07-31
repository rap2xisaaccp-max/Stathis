package citu.edu.stathis.mobile.features.profile.data.repository

import citu.edu.stathis.mobile.core.data.models.ClientResponse
import citu.edu.stathis.mobile.features.auth.data.models.UserResponseDTO
import citu.edu.stathis.mobile.features.profile.data.models.FaceEmbeddingResponse

interface ProfileRepository {

    suspend fun getUserProfile(): ClientResponse<UserResponseDTO>

    suspend fun updateUserProfile(
        firstName: String,
        lastName: String,
        birthdate: String? = null,
        age: Int? = null,
        profilePictureUrl: String?,
        heightInMeters: Double? = null,
        weightInKg: Double? = null
    ): ClientResponse<UserResponseDTO>

    suspend fun updateStudentProfile(
        school: String?,
        course: String?,
        yearLevel: Int?
    ): ClientResponse<UserResponseDTO>

    suspend fun registerFace(embeddingJson: String): ClientResponse<FaceEmbeddingResponse>

    suspend fun getFaceEmbedding(): ClientResponse<FaceEmbeddingResponse>
}
