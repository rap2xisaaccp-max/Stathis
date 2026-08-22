-- Form-correction evidence snapshots (one JPEG per confirmed coaching event).
-- Additive. Do not drop modality/RCT columns here.

CREATE TABLE IF NOT EXISTS form_correction_evidence (
    form_correction_evidence_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ,
    intervention_physical_id VARCHAR(255) UNIQUE NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255),
    classroom_id VARCHAR(255),
    attempt_number INTEGER,
    exercise_type VARCHAR(255) NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    error_description TEXT,
    correction_text TEXT,
    captured_at TIMESTAMPTZ NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL DEFAULT 'image/jpeg',
    byte_size INTEGER NOT NULL,
    sha256 VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_fce_student_captured
    ON form_correction_evidence (student_id, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_fce_classroom_captured
    ON form_correction_evidence (classroom_id, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_fce_task
    ON form_correction_evidence (task_id);
