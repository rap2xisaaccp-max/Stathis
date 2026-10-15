import {
  AdaptiveInsightsDTO,
  ExerciseMasteryDTO,
  ProfileHistoryPointDTO,
} from '@/services/adaptive/api-adaptive-client';
import {
  formErrorDisplay,
  formErrorLabel,
} from '@/components/adaptive/form-error-labels';
import {
  buildFormMasteryByExerciseChartData,
  formMasteryDisplayPercent,
} from '@/components/adaptive/form-mastery-chart';

export { buildFormMasteryByExerciseChartData, formMasteryDisplayPercent };

export const MASTERY_CHART_Y_DOMAIN: [number, number] = [0, 100];

export const TIMELINE_CATEGORY_NAMES: Record<string, string> = {
  masteryPct: 'Coaching-frequency (legacy)',
  consistencyPct: 'Coaching success',
};

export const MASTERY_CATEGORY_NAMES: Record<string, string> = {
  masteryPct: 'Form quality',
};

export function buildRecurringErrorsChartData(
  topRecurringErrors: Record<string, number> | null | undefined,
  limit = 8
): Array<{ error: string; count: number; code: string }> {
  return Object.entries(topRecurringErrors || {})
    .map(([error, count]) => ({
      code: error,
      error: formErrorLabel(error),
      count: Number(count) || 0,
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, limit);
}

export function recurringErrorTeacherLabel(code: string): string {
  return formErrorDisplay(code);
}

/** @deprecated Coaching-frequency chart. Do not use for Form Mastery. */
export function buildMasteryByExerciseChartData(
  mastery: ExerciseMasteryDTO[] | null | undefined
): Array<{ exercise: string; masteryPct: number }> {
  return [...(mastery || [])]
    .sort((a, b) => {
      const ta = a.lastSessionAt ? new Date(a.lastSessionAt).getTime() : 0;
      const tb = b.lastSessionAt ? new Date(b.lastSessionAt).getTime() : 0;
      if (tb !== ta) return tb - ta;
      return (b.masteryLevel || 0) - (a.masteryLevel || 0);
    })
    .map((item) => ({
      exercise: (item.exerciseType || 'UNKNOWN').replaceAll('_', ' '),
      masteryPct: Math.round((item.masteryLevel || 0) * 100),
    }));
}

function shortDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso.slice(0, 10);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export function buildMasteryTimelineChartData(
  history: ProfileHistoryPointDTO[] | null | undefined
): Array<{ date: string; masteryPct: number; consistencyPct: number }> {
  // Uses LearningProfileHistory.meanMasteryLevel (coaching-frequency snapshots).
  // Not Form Mastery. Do not wire this into Form Mastery by Exercise.
  return [...(history || [])]
    .sort((a, b) => {
      const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return ta - tb;
    })
    .map((point) => ({
      date: shortDate(point.createdAt),
      masteryPct: Math.round((point.meanMasteryLevel ?? 0) * 100),
      consistencyPct: Math.round((point.consistencyScore ?? 0) * 100),
    }));
}

export function hasChartableInsights(data: AdaptiveInsightsDTO | null | undefined): boolean {
  if (!data) return false;
  return (
    Object.keys(data.topRecurringErrors || {}).length > 0 ||
    (data.formMastery || []).length > 0
  );
}
