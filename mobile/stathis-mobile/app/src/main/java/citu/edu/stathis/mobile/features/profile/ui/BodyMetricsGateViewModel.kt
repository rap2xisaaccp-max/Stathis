package citu.edu.stathis.mobile.features.profile.ui

import androidx.lifecycle.ViewModel
import citu.edu.stathis.mobile.features.profile.domain.EnsureBodyMetricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BodyMetricsGateViewModel @Inject constructor(
    private val ensureBodyMetrics: EnsureBodyMetricsUseCase
) : ViewModel() {
    suspend fun ensureComplete(): Boolean = ensureBodyMetrics()
}
