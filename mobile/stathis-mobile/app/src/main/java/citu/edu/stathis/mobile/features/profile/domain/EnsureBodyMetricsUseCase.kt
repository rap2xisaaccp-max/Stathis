package citu.edu.stathis.mobile.features.profile.domain

import citu.edu.stathis.mobile.features.profile.data.repository.ProfileRepository
import javax.inject.Inject

class EnsureBodyMetricsUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(): Boolean {
        val response = profileRepository.getUserProfile()
        val profile = response.data ?: return false
        return response.success
            && profile.hasCompleteBodyMetrics()
            && profile.hasFaceRegistered()
    }
}
