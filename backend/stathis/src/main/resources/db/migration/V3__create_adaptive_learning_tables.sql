-- Adaptive Physical Skill Learning Engine tables
-- Optional manual migration for Supabase/Postgres (prod may use ddl-auto=update/validate).

CREATE TABLE IF NOT EXISTS feedback_intervention (
    feedback_intervention_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ,
    student_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255),
    classroom_id VARCHAR(255),
    exercise_type VARCHAR(255) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    modality VARCHAR(64) NOT NULL,
    message_code VARCHAR(255),
    message_text TEXT,
    delivered_at TIMESTAMPTZ NOT NULL,
    baseline_severity DOUBLE PRECISION NOT NULL,
    policy_source VARCHAR(64) NOT NULL,
    experiment_arm VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_fi_student ON feedback_intervention (student_id);
CREATE INDEX IF NOT EXISTS idx_fi_session ON feedback_intervention (session_id);
CREATE INDEX IF NOT EXISTS idx_fi_student_exercise_error
    ON feedback_intervention (student_id, exercise_type, error_code);

CREATE TABLE IF NOT EXISTS feedback_response (
    feedback_response_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ,
    student_id VARCHAR(255) NOT NULL,
    intervention_physical_id VARCHAR(255) NOT NULL,
    window_end_at TIMESTAMPTZ NOT NULL,
    post_severity DOUBLE PRECISION NOT NULL,
    delta DOUBLE PRECISION NOT NULL,
    reps_in_window INTEGER,
    success BOOLEAN NOT NULL,
    confounders_json JSONB
);

CREATE INDEX IF NOT EXISTS idx_fr_intervention ON feedback_response (intervention_physical_id);
CREATE INDEX IF NOT EXISTS idx_fr_student ON feedback_response (student_id);

CREATE TABLE IF NOT EXISTS student_learning_profile (
    student_learning_profile_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    student_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    preferred_modality VARCHAR(64),
    modality_effectiveness_json JSONB,
    learning_rate_estimate DOUBLE PRECISION,
    consistency_score DOUBLE PRECISION,
    fatigue_sensitivity DOUBLE PRECISION,
    total_interventions INTEGER,
    total_successful_interventions INTEGER,
    version BIGINT
);

CREATE TABLE IF NOT EXISTS exercise_mastery (
    exercise_mastery_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    exercise_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    mastery_level DOUBLE PRECISION NOT NULL,
    common_errors_json JSONB,
    sessions_count INTEGER,
    median_time_to_correction_ms BIGINT,
    recommended_difficulty VARCHAR(64),
    last_session_at TIMESTAMPTZ,
    UNIQUE (student_id, exercise_type)
);

CREATE TABLE IF NOT EXISTS learning_profile_history (
    learning_profile_history_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ,
    snapshot_json JSONB NOT NULL,
    reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_lph_student ON learning_profile_history (student_id);
