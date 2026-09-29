-- One optional demonstration video per assigned exercise.
-- Keyed by (task_id, exercise_template_id), not task_exercise_id.
-- task_exercise rows are deleted and recreated on reorder, so a foreign key
-- to task_exercise_id would drop the video. Do not apply this file automatically.
-- Do not apply V9.

CREATE TABLE IF NOT EXISTS exercise_demonstration (
    exercise_demonstration_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    exercise_template_id VARCHAR(255) NOT NULL,
    uploaded_by VARCHAR(11) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ,
    CONSTRAINT uq_exercise_demonstration_physical_id UNIQUE (physical_id),
    CONSTRAINT uq_exercise_demonstration_task_template UNIQUE (task_id, exercise_template_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_exercise_demonstration_physical_id'
    ) THEN
        ALTER TABLE exercise_demonstration
            ADD CONSTRAINT uq_exercise_demonstration_physical_id UNIQUE (physical_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_exercise_demonstration_task_template'
    ) THEN
        ALTER TABLE exercise_demonstration
            ADD CONSTRAINT uq_exercise_demonstration_task_template UNIQUE (task_id, exercise_template_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_exercise_demonstration_task'
    ) THEN
        ALTER TABLE exercise_demonstration
            ADD CONSTRAINT fk_exercise_demonstration_task
            FOREIGN KEY (task_id) REFERENCES task (physical_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_exercise_demonstration_template'
    ) THEN
        ALTER TABLE exercise_demonstration
            ADD CONSTRAINT fk_exercise_demonstration_template
            FOREIGN KEY (exercise_template_id) REFERENCES exercise_template (physical_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_exercise_demonstration_uploader'
    ) THEN
        ALTER TABLE exercise_demonstration
            ADD CONSTRAINT fk_exercise_demonstration_uploader
            FOREIGN KEY (uploaded_by) REFERENCES users (physical_id);
    END IF;
END $$;
