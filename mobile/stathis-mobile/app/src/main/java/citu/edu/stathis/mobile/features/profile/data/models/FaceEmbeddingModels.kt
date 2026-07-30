package citu.edu.stathis.mobile.features.profile.data.models

import kotlinx.serialization.Serializable

@Serializable
data class FaceEmbeddingRequest(
    val embedding: String
)

@Serializable
data class FaceEmbeddingResponse(
    val faceRegistered: Boolean = false,
    val embedding: String? = null
)
