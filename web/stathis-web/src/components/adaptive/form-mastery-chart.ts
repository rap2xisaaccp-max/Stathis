export type FormMasteryChartItem = {
  studentId?: string;
  exerciseType?: string;
  formMasteryLevel: number;
  formMasteryPercent?: number;
  eligibleAttemptCount?: number;
  lastAttemptAt?: string | null;
};

export function formMasteryDisplayPercent(formMasteryLevel: number): number {
  return Math.round(Math.max(0, Math.min(1, formMasteryLevel)) * 100);
}

/**
 * Teacher/mobile Form Mastery bars from dedicated FormMasteryDTO rows.
 * Coaching-frequency masteryLevel must never be passed here.
 */
export function buildFormMasteryByExerciseChartData(
  formMastery: FormMasteryChartItem[] | null | undefined
): Array<{ exercise: string; masteryPct: number; attemptCount: number }> {
  return [...(formMastery || [])]
    .filter((item) => item != null && Number.isFinite(item.formMasteryLevel))
    .sort((a, b) => {
      const ta = a.lastAttemptAt ? new Date(a.lastAttemptAt).getTime() : 0;
      const tb = b.lastAttemptAt ? new Date(b.lastAttemptAt).getTime() : 0;
      if (tb !== ta) return tb - ta;
      return (b.formMasteryLevel || 0) - (a.formMasteryLevel || 0);
    })
    .map((item) => ({
      exercise: (item.exerciseType || 'UNKNOWN').replaceAll('_', ' '),
      masteryPct: formMasteryDisplayPercent(item.formMasteryLevel),
      attemptCount: item.eligibleAttemptCount || 0,
    }));
}
