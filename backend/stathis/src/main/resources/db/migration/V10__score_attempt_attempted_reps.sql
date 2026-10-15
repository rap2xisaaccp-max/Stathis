-- Valid reps stay in score_attempt.reps. attempted_reps counts every completed movement,
-- including reps rejected for a supported form error. Null on rows saved before this column.

ALTER TABLE score_attempt ADD COLUMN IF NOT EXISTS attempted_reps INTEGER;
