package citu.edu.stathis.mobile.features.tasks.presentation

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed class TemplateSubmitState {
    data object Idle : TemplateSubmitState()
    data object Saving : TemplateSubmitState()
    data object Success : TemplateSubmitState()
    data class Failed(val message: String) : TemplateSubmitState()
}

/**
 * Results-screen Complete is not a second writer. Navigation is allowed only after
 * the graded POST succeeds. Failed saves stay on the result flow and are retryable.
 */
object GradedSubmitPolicy {
    fun canNavigateAway(state: TemplateSubmitState): Boolean = state is TemplateSubmitState.Success

    fun canRetrySave(state: TemplateSubmitState): Boolean = state is TemplateSubmitState.Failed

    fun isSaveInFlight(state: TemplateSubmitState): Boolean = state is TemplateSubmitState.Saving

    fun canStartNewAttempt(state: TemplateSubmitState): Boolean =
        state is TemplateSubmitState.Idle ||
            state is TemplateSubmitState.Success ||
            state is TemplateSubmitState.Failed

    fun resultsCompleteWritesAgain(): Boolean = false
}

object GradedSubmitScope {
    /** Survives ViewModel/job cancellation from popping the template screen. */
    suspend fun <T> runUncancelled(block: suspend () -> T): T =
        withContext(NonCancellable) { block() }
}
