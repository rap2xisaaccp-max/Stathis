-- Optional manual migration for Supabase/Postgres (prod uses ddl-auto=validate).
-- Dev/local environments with ddl-auto=update apply these automatically via JPA.

ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS age INTEGER;
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS face_embedding TEXT;
ALTER TABLE user_profile ADD COLUMN IF NOT EXISTS face_registered BOOLEAN DEFAULT FALSE;
