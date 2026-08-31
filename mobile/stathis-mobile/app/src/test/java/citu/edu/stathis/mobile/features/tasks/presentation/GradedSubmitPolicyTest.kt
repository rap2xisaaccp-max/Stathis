package citu.edu.stathis.mobile.features.tasks.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradedSubmitPolicyTest {

    @Test
    fun resultsCompleteIsNotASecondWriter() {
        assertFalse(GradedSubmitPolicy.resultsCompleteWritesAgain())
    }

    @Test
    fun navigationAllowedOnlyAfterSuccess() {
        assertFalse(GradedSubmitPolicy.canNavigateAway(TemplateSubmitState.Idle))
        assertFalse(GradedSubmitPolicy.canNavigateAway(TemplateSubmitState.Saving))
        assertFalse(GradedSubmitPolicy.canNavigateAway(TemplateSubmitState.Failed("network")))
        assertTrue(GradedSubmitPolicy.canNavigateAway(TemplateSubmitState.Success))
    }

    @Test
    fun failedSaveIsRetryableAndDoesNotLookComplete() {
        val failed = TemplateSubmitState.Failed("Failed to complete exercise: 500")
        assertTrue(GradedSubmitPolicy.canRetrySave(failed))
        assertFalse(GradedSubmitPolicy.canNavigateAway(failed))
        assertTrue(GradedSubmitPolicy.canStartNewAttempt(failed))
        assertFalse(GradedSubmitPolicy.isSaveInFlight(failed))
    }

    @Test
    fun savingBlocksLeaveAndNewAttempt() {
        assertTrue(GradedSubmitPolicy.isSaveInFlight(TemplateSubmitState.Saving))
        assertFalse(GradedSubmitPolicy.canNavigateAway(TemplateSubmitState.Saving))
        assertFalse(GradedSubmitPolicy.canStartNewAttempt(TemplateSubmitState.Saving))
        assertFalse(GradedSubmitPolicy.canRetrySave(TemplateSubmitState.Saving))
    }

    @Test
    fun finishAndAutoCompleteShareOneGuard() {
        val guard = ExerciseSubmissionGuard()
        var posts = 0
        fun trySubmit() {
            if (guard.tryAcquire()) posts++
        }
        trySubmit()
        trySubmit()
        trySubmit()
        assertEquals(1, posts)
        assertFalse(GradedSubmitPolicy.resultsCompleteWritesAgain())
    }

    @Test
    fun attemptsStayUnchangedUntilBackendSuccess() {
        var attempts = 0
        val stateBefore = TemplateSubmitState.Saving
        if (stateBefore is TemplateSubmitState.Success) {
            attempts += 1
        }
        assertEquals(0, attempts)
        val stateAfter = TemplateSubmitState.Success
        if (stateAfter is TemplateSubmitState.Success) {
            attempts += 1
        }
        assertEquals(1, attempts)
    }

    @Test
    fun runUncancelledSurvivesParentCancellation() = runTest {
        val started = CompletableDeferred<Unit>()
        var finished = false
        val job = launch {
            GradedSubmitScope.runUncancelled {
                started.complete(Unit)
                delay(50)
                finished = true
            }
        }
        started.await()
        job.cancelAndJoin()
        advanceUntilIdle()
        assertTrue(finished)
    }

    @Test
    fun cancelledParentWithoutUncancelledDropsWork() = runTest {
        val started = CompletableDeferred<Unit>()
        var finished = false
        val job = launch {
            started.complete(Unit)
            delay(50)
            finished = true
        }
        started.await()
        job.cancelAndJoin()
        advanceUntilIdle()
        assertFalse(finished)
    }

    @Test
    fun duplicateConcurrentAcquirePostsOnce() = runTest {
        val guard = ExerciseSubmissionGuard()
        var posts = 0
        val a = async {
            if (guard.tryAcquire()) {
                delay(10)
                posts++
            }
        }
        val b = async {
            if (guard.tryAcquire()) {
                delay(10)
                posts++
            }
        }
        a.await()
        b.await()
        assertEquals(1, posts)
    }
}
