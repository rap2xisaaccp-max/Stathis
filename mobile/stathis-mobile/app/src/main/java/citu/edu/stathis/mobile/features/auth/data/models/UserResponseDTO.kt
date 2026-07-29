package citu.edu.stathis.mobile.features.auth.data.models

import citu.edu.stathis.mobile.features.auth.data.enums.UserRoles
import kotlinx.serialization.Serializable

@Serializable
data class UserResponseDTO(
    val physicalId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val birthdate: String? = null,
    val age: Int? = null,
    val profilePictureUrl: String? = null,
    val role: UserRoles,
    val school: String? = null,
    val course: String? = null,
    val yearLevel: Int? = null,
    val department: String? = null,
    val positionTitle: String? = null,
    val heightInMeters: Double? = null,
    val weightInKg: Double? = null,
    val faceRegistered: Boolean = false,
    val emailVerified: Boolean = true
) {
    fun hasCompleteBodyMetrics(): Boolean {
        return age != null
            && age > 0
            && heightInMeters != null
            && heightInMeters > 0.0
            && weightInKg != null
            && weightInKg > 0.0
    }

    fun hasFaceRegistered(): Boolean = faceRegistered
}
