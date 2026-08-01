-- APSLE P1: variance-preserving arm/session rollups for RCT without permanent raw FI/FR.
-- Manual migration for Supabase/Postgres (prod ddl-auto=validate).

CREATE TABLE IF NOT EXISTS adaptive_arm_session_rollup (
    adaptive_arm_session_rollup_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    student_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    classroom_id VARCHAR(255),
    base_arm VARCHAR(32) NOT NULL,
    n_interventions INTEGER NOT NULL DEFAULT 0,
    n_responses INTEGER NOT NULL DEFAULT 0,
    successes INTEGER NOT NULL DEFAULT 0,
    sum_delta DOUBLE PRECISION NOT NULL DEFAULT 0,
    sum_delta_sq DOUBLE PRECISION NOT NULL DEFAULT 0,
    UNIQUE (student_id, session_id, base_arm)
);

CREATE INDEX IF NOT EXISTS idx_aasr_student ON adaptive_arm_session_rollup (student_id);
CREATE INDEX IF NOT EXISTS idx_aasr_classroom ON adaptive_arm_session_rollup (classroom_id);
CREATE INDEX IF NOT EXISTS idx_aasr_base_arm ON adaptive_arm_session_rollup (base_arm);
