package citu.edu.stathis.mobile.features.profile.data.repository

import citu.edu.stathis.mobile.core.data.AuthTokenManager
import citu.edu.stathis.mobile.core.data.models.ClientResponse
import citu.edu.stathis.mobile.features.auth.data.enums.UserRoles
import citu.edu.stathis.mobile.features.auth.data.models.UserResponseDTO
import citu.edu.stathis.mobile.features.profile.data.models.FaceEmbeddingRequest
import citu.edu.stathis.mobile.features.profile.data.models.FaceEmbeddingResponse
import citu.edu.stathis.mobile.features.profile.data.models.UpdateStudentProfileRequest
import citu.edu.stathis.mobile.features.profile.data.models.UpdateUserProfileRequest
import citu.edu.stathis.mobile.features.profile.domain.ProfileApiService
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val profileApiService: ProfileApiService,
    private val authTokenManager: AuthTokenManager
) : ProfileRepository {

    override suspend fun getUserProfile(): ClientResponse<UserResponseDTO> {
        return try {
            val accessToken = authTokenManager.accessTokenFlow.firstOrNull()
            
            // Handle demo account bypass
            if (accessToken == "debug_access") {
                val mockProfile = UserResponseDTO(
                    physicalId = "debug_user",
                    email = "demo@example.com",
                    firstName = "Demo",
                    lastName = "User",
                    birthdate = null,
                    age = null,
                    profilePictureUrl = null,
                    school = "Demo University",
                    course = "Computer Science",
                    yearLevel = 3,
                    role = UserRoles.STUDENT,
                    department = null,
                    positionTitle = null,
                    heightInMeters = null,
                    weightInKg = null,
                    faceRegistered = false,
                    emailVerified = true
                )
                authTokenManager.updateUserIdentity(
                    physicalId = mockProfile.physicalId,
                    role = mockProfile.role
                )
                return ClientResponse(
                    success = true,
                    data = mockProfile,
                    message = "Demo profile loaded successfully."
                )
            }
            
            val response = profileApiService.getStudentProfile()
            authTokenManager.updateUserIdentity(
                physicalId = response.physicalId,
                role = response.role
            )
            ClientResponse(
                success = true, data = response,
                message = "Profile successfully fetched."           )
        } catch (e: HttpException) {
            ClientResponse(success = false, message = e.message() ?: "Failed to fetch profile.")
        } catch (e: IOException) {
            ClientResponse(success = false, message = "Network error. Could not fetch profile.")
        } catch (e: Exception) {
            ClientResponse(success = false, message = e.message ?: "An unknown error occurred.")
        }
    }

    override suspend fun updateUserProfile(
        firstName: String,
        lastName: String,
        birthdate: String?,
        age: Int?,
        profilePictureUrl: String?,
        heightInMeters: Double?,
        weightInKg: Double?
    ): ClientResponse<UserResponseDTO> {
        return try {
            val request = UpdateUserProfileRequest(
                firstName = firstName,
                lastName = lastName,
                birthdate = birthdate,
                age = age,
                profilePictureUrl = profilePictureUrl,
                heightInMeters = heightInMeters,
                weightInKg = weightInKg
            )
            val response = profileApiService.updateUserProfile(request)
            ClientResponse(success = true, data = response, message = "Profile updated successfully.")
        } catch (e: HttpException) {
            ClientResponse(success = false, message = e.message() ?: "Failed to update profile.")
        } catch (e: IOException) {
            ClientResponse(success = false, message = "Network error. Could not update profile.")
        } catch (e: Exception) {
            ClientResponse(success = false, message = e.message ?: "An unknown error occurred.")
        }
    }

    override suspend fun updateStudentProfile(
        school: String?,
        course: String?,
        yearLevel: Int?
    ): ClientResponse<UserResponseDTO> {
        return try {
            val request = UpdateStudentProfileRequest(
                school = school,
                course = course,
                yearLevel = yearLevel
            )
            val response = profileApiService.updateStudentProfile(request)
            ClientResponse(success = true, data = response, message = "Student profile updated.")
        } catch (e: HttpException) {
            ClientResponse(success = false, message = e.message() ?: "Failed to update student profile.")
        } catch (e: IOException) {
            ClientResponse(success = false, message = "Network error. Could not update student profile.")
        } catch (e: Exception) {
            ClientResponse(success = false, message = e.message ?: "An unknown error occurred.")
        }
    }

    override suspend fun registerFace(embeddingJson: String): ClientResponse<FaceEmbeddingResponse> {
        return try {
            val response = profileApiService.registerFace(FaceEmbeddingRequest(embedding = embeddingJson))
            ClientResponse(success = true, data = response, message = "Face registered successfully.")
        } catch (e: HttpException) {
            ClientResponse(success = false, message = e.message() ?: "Failed to register face.")
        } catch (e: IOException) {
            ClientResponse(success = false, message = "Network error. Could not register face.")
        } catch (e: Exception) {
            ClientResponse(success = false, message = e.message ?: "An unknown error occurred.")
        }
    }

    override suspend fun getFaceEmbedding(): ClientResponse<FaceEmbeddingResponse> {
        return try {
            val response = profileApiService.getFaceEmbedding()
            ClientResponse(success = true, data = response, message = "Face embedding fetched.")
        } catch (e: HttpException) {
            ClientResponse(success = false, message = e.message() ?: "Failed to fetch face embedding.")
        } catch (e: IOException) {
            ClientResponse(success = false, message = "Network error. Could not fetch face embedding.")
        } catch (e: Exception) {
            ClientResponse(success = false, message = e.message ?: "An unknown error occurred.")
        }
    }
}