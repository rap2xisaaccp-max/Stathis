-- Preferred modality by exercise (JSON map) on student_learning_profile.
-- Manual migration for Supabase/Postgres (prod ddl-auto=validate).

ALTER TABLE student_learning_profile
    ADD COLUMN IF NOT EXISTS preferred_modality_by_exercise_json JSONB;
