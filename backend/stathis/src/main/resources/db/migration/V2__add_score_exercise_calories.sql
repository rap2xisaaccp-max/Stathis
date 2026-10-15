-- Optional manual migration for Supabase/Postgres (prod uses ddl-auto=validate).
-- Dev/local environments with ddl-auto=update apply these automatically via JPA.

ALTER TABLE score ADD COLUMN IF NOT EXISTS reps INTEGER;
ALTER TABLE score ADD COLUMN IF NOT EXISTS goal_reps INTEGER;
ALTER TABLE score ADD COLUMN IF NOT EXISTS calories_burned DOUBLE PRECISION;
