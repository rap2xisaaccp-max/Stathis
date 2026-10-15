package citu.edu.stathis.mobile.features.tasks.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGoalCompletionTest {

    @Test
    fun completesExactlyAtRepGoal() {
        assertFalse(
            ExerciseGoalCompletion.shouldAutoComplete(
                actualReps = 9,
                goalReps = 10,
                actualTimeSeconds = 5,
                goalTimeSeconds = 600
            )
        )
        assertTrue(
            ExerciseGoalCompletion.shouldAutoComplete(
                actualReps = 10,
                goalReps = 10,
                actualTimeSeconds = 5,
                goalTimeSeconds = 600
            )
        )
    }

    @Test
    fun completesWhenTimeGoalReached() {
        assertTrue(
            ExerciseGoalCompletion.shouldAutoComplete(
                actualReps = 3,
                goalReps = 10,
                actualTimeSeconds = 60,
                goalTimeSeconds = 60
            )
        )
    }

    @Test
    fun ignoresUnsetGoals() {
        assertFalse(
            ExerciseGoalCompletion.shouldAutoComplete(
                actualReps = 50,
                goalReps = 0,
                actualTimeSeconds = 50,
                goalTimeSeconds = 0
            )
        )
    }

    @Test
    fun submissionGuardPreventsDuplicate() {
        val guard = ExerciseSubmissionGuard()
        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
        assertTrue(guard.isSubmitted())
        guard.reset()
        assertTrue(guard.tryAcquire())
    }

    @Test
    fun doesNotClearCountersWhenParentAlreadyHasReps() {
        // Legacy helper — live Start path always resets via beginCountingAttempt().
        assertTrue(ExerciseGoalCompletion.shouldClearCountersOnStart(0))
        assertFalse(ExerciseGoalCompletion.shouldClearCountersOnStart(1))
        assertFalse(ExerciseGoalCompletion.shouldClearCountersOnStart(6))
    }

    @Test
    fun autoCompleteIdempotentWithSubmissionGuard() {
        val guard = ExerciseSubmissionGuard()
        var completes = 0
        fun tryComplete(reps: Int, goal: Int) {
            if (ExerciseGoalCompletion.shouldAutoComplete(reps, goal, 1, 600) && guard.tryAcquire()) {
                completes++
            }
        }
        tryComplete(10, 10)
        tryComplete(11, 10)
        tryComplete(12, 10)
        assertTrue(completes == 1)
    }
}
