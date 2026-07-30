-- APSLE P0: idempotency + query indexes for feedback tables
-- Manual migration for Supabase/Postgres (prod ddl-auto=validate).
-- Precondition verified 2026-07-30: 0 duplicate intervention_physical_id groups.

-- One response per intervention (closes concurrent insert race)
CREATE UNIQUE INDEX IF NOT EXISTS uq_fr_intervention_physical_id
    ON feedback_response (intervention_physical_id);

-- Teacher/insights and classroom evaluation helpers
CREATE INDEX IF NOT EXISTS idx_fi_student_delivered
    ON feedback_intervention (student_id, delivered_at DESC);

CREATE INDEX IF NOT EXISTS idx_fi_experiment_arm
    ON feedback_intervention (experiment_arm);

CREATE INDEX IF NOT EXISTS idx_fi_classroom
    ON feedback_intervention (classroom_id);

CREATE INDEX IF NOT EXISTS idx_fr_student_created
    ON feedback_response (student_id, created_at DESC);
