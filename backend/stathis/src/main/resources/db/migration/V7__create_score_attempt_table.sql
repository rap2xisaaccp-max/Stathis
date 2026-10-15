-- Per-attempt history for student task scores (accuracy, score, reps per submission).
-- Optional manual migration for Supabase/Postgres (prod may use ddl-auto=validate).

CREATE TABLE IF NOT EXISTS score_attempt (
    score_attempt_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ,
    score_physical_id VARCHAR(255) NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    quiz_template_id VARCHAR(255),
    exercise_template_id VARCHAR(255),
    attempt_number INTEGER NOT NULL,
    score INTEGER,
    max_score INTEGER,
    accuracy DOUBLE PRECISION,
    reps INTEGER,
    goal_reps INTEGER,
    calories_burned DOUBLE PRECISION,
    time_taken BIGINT,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_score_attempt_student_task
    ON score_attempt (student_id, task_id);

CREATE INDEX IF NOT EXISTS idx_score_attempt_score
    ON score_attempt (score_physical_id);
