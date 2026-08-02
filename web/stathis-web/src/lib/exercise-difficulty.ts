/** Teacher-facing exercise difficulty (3 bands). Expert is legacy-only. */
export const EXERCISE_DIFFICULTIES = [
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
] as const;

export type ExerciseDifficultyValue = (typeof EXERCISE_DIFFICULTIES)[number]['value'];

/** Template goal-rep options shown when creating exercise templates. */
export const EXERCISE_GOAL_REPS_OPTIONS = [
  { value: '10', label: '10 repetitions' },
  { value: '20', label: '20 repetitions' },
  { value: '30', label: '30 repetitions' },
] as const;

export type ExerciseGoalRepsValue = (typeof EXERCISE_GOAL_REPS_OPTIONS)[number]['value'];

/** Normalize API/legacy values (including EXPERT) to the 3 teacher bands. */
export function normalizeExerciseDifficulty(
  difficulty: string | null | undefined
): ExerciseDifficultyValue {
  const d = (difficulty || '').trim().toUpperCase();
  if (d === 'EXPERT' || d === 'ADVANCED') return 'ADVANCED';
  if (d === 'INTERMEDIATE') return 'INTERMEDIATE';
  return 'BEGINNER';
}

export function formatExerciseDifficulty(difficulty: string | null | undefined): string {
  const normalized = normalizeExerciseDifficulty(difficulty);
  return EXERCISE_DIFFICULTIES.find((x) => x.value === normalized)?.label ?? 'Beginner';
}

/** Soft adaptive goal-reps snap to template options. */
export function snapGoalRepsToTemplateOptions(reps: number | null | undefined): number | null {
  if (reps == null || Number.isNaN(reps)) return null;
  if (reps < 15) return 10;
  if (reps < 25) return 20;
  return 30;
}
