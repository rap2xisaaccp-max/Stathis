package citu.edu.stathis.mobile.features.profile.domain

import citu.edu.stathis.mobile.features.profile.data.repository.ProfileRepository
import javax.inject.Inject

class EnsureBodyMetricsUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    /**
     * @return true if body metrics are complete and the caller may proceed;
     * false if the user must complete body metrics setup first.
     */
    suspend operator fun invoke(): Boolean {
        val response = profileRepository.getUserProfile()
        return response.success && response.data?.hasCompleteBodyMetrics() == true
    }
}
