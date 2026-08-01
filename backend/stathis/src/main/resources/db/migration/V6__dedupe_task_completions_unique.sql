-- Manual migration for Supabase/Postgres (prod ddl-auto=validate).
-- Root cause: multiple task_completions rows per (student_id, task_id) make
-- Spring Data Optional single-result queries throw and roll back completeExercise.

-- Merge OR-flags from duplicate rows onto the oldest keeper, without writing NULLs.
WITH ranked AS (
    SELECT
        physical_id,
        student_id,
        task_id,
        COALESCE(lesson_completed, false) AS lesson_completed,
        COALESCE(quiz_completed, false) AS quiz_completed,
        COALESCE(exercise_completed, false) AS exercise_completed,
        COALESCE(is_fully_completed, false) AS is_fully_completed,
        COALESCE(submitted_for_review, false) AS submitted_for_review,
        ROW_NUMBER() OVER (
            PARTITION BY student_id, task_id
            ORDER BY started_at ASC NULLS LAST, physical_id ASC
        ) AS rn
    FROM task_completions
),
agg AS (
    SELECT
        student_id,
        task_id,
        BOOL_OR(lesson_completed) AS lesson_completed,
        BOOL_OR(quiz_completed) AS quiz_completed,
        BOOL_OR(exercise_completed) AS exercise_completed,
        BOOL_OR(is_fully_completed) AS is_fully_completed,
        BOOL_OR(submitted_for_review) AS submitted_for_review
    FROM ranked
    GROUP BY student_id, task_id
),
keepers AS (
    SELECT physical_id, student_id, task_id
    FROM ranked
    WHERE rn = 1
)
UPDATE task_completions tc
SET
    lesson_completed = a.lesson_completed,
    quiz_completed = a.quiz_completed,
    exercise_completed = a.exercise_completed,
    is_fully_completed = a.is_fully_completed,
    submitted_for_review = a.submitted_for_review
FROM keepers k
JOIN agg a ON a.student_id = k.student_id AND a.task_id = k.task_id
WHERE tc.physical_id = k.physical_id;

DELETE FROM task_completions tc
WHERE tc.physical_id IN (
    SELECT physical_id
    FROM (
        SELECT
            physical_id,
            ROW_NUMBER() OVER (
                PARTITION BY student_id, task_id
                ORDER BY started_at ASC NULLS LAST, physical_id ASC
            ) AS rn
        FROM task_completions
    ) x
    WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_completions_student_task
    ON task_completions (student_id, task_id);
