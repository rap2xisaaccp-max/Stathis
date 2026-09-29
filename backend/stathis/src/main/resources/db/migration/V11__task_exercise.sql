-- Ordered exercises on one task. Manual migration (Flyway is not a backend dependency).
-- Does not drop task.exercise_template_id. Do not apply V9. Do not rename V7 scripts.
-- Multi-exercise tasks need the Android build that reads exercises[].
-- Legacy exerciseCompleted / exerciseAttempts stay unset until every assigned exercise qualifies,
-- so an older app does not treat the whole task as done after exercise 1.

CREATE TABLE IF NOT EXISTS task_exercise (
    task_exercise_id UUID PRIMARY KEY,
    physical_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    exercise_template_id VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ,
    CONSTRAINT uq_task_exercise_physical_id UNIQUE (physical_id),
    CONSTRAINT uq_task_exercise_task_template UNIQUE (task_id, exercise_template_id),
    CONSTRAINT uq_task_exercise_task_order UNIQUE (task_id, sort_order),
    CONSTRAINT chk_task_exercise_sort_order CHECK (sort_order >= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_physical_id ON task (physical_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_exercise_template_physical_id ON exercise_template (physical_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_task_exercise_sort_order') THEN
        ALTER TABLE task_exercise
            ADD CONSTRAINT chk_task_exercise_sort_order CHECK (sort_order >= 1);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_task_exercise_task_template') THEN
        ALTER TABLE task_exercise
            ADD CONSTRAINT uq_task_exercise_task_template UNIQUE (task_id, exercise_template_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_task_exercise_task_order') THEN
        ALTER TABLE task_exercise
            ADD CONSTRAINT uq_task_exercise_task_order UNIQUE (task_id, sort_order);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_task_exercise_task') THEN
        ALTER TABLE task_exercise
            ADD CONSTRAINT fk_task_exercise_task
            FOREIGN KEY (task_id) REFERENCES task (physical_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_task_exercise_template') THEN
        ALTER TABLE task_exercise
            ADD CONSTRAINT fk_task_exercise_template
            FOREIGN KEY (exercise_template_id) REFERENCES exercise_template (physical_id);
    END IF;
END $$;

INSERT INTO task_exercise (
    task_exercise_id,
    physical_id,
    task_id,
    exercise_template_id,
    sort_order,
    created_at
)
SELECT
    gen_random_uuid(),
    'TEX-' || upper(replace(gen_random_uuid()::text, '-', '')),
    t.physical_id,
    t.exercise_template_id,
    1,
    COALESCE(t.created_at, NOW())
FROM task t
WHERE t.physical_id IS NOT NULL
  AND t.exercise_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM task_exercise te WHERE te.task_id = t.physical_id
  );
