package edu.cit.stathis.task;

/**
 * Required-component completion: a task is fully complete when every template it
 * actually has is done. Missing lesson/quiz/exercise slots are not required.
 */
public final class TaskComponentCompletion {

    private TaskComponentCompletion() {}

    public static boolean isFullyComplete(
            boolean hasLesson,
            boolean hasQuiz,
            boolean hasExercise,
            boolean lessonCompleted,
            boolean quizCompleted,
            boolean exerciseCompleted) {
        if (!hasLesson && !hasQuiz && !hasExercise) {
            return false;
        }
        if (hasLesson && !lessonCompleted) {
            return false;
        }
        if (hasQuiz && !quizCompleted) {
            return false;
        }
        if (hasExercise && !exerciseCompleted) {
            return false;
        }
        return true;
    }
}
