-- Migration: add reps_performed column to task_completions
-- Applies to Postgres (Supabase)

BEGIN;

-- Add column to store reps performed on exercise completion
ALTER TABLE IF EXISTS public.task_completions
  ADD COLUMN IF NOT EXISTS reps_performed INTEGER DEFAULT 0;

COMMIT;

-- Rollback (manual):
-- ALTER TABLE IF EXISTS public.task_completions DROP COLUMN IF EXISTS reps_performed;