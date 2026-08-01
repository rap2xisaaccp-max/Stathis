import {
  AdaptiveInsightsDTO,
  ExerciseMasteryDTO,
  ProfileHistoryPointDTO,
} from '@/services/adaptive/api-adaptive-client';

export function formatModalityLabel(modality: string): string {
  return modality.replaceAll('_', ' ');
}

export function buildModalityEffectivenessChartData(
  modalityMeanDelta: Record<string, number> | null | undefined
): Array<{ modality: string; delta: number }> {
  return Object.entries(modalityMeanDelta || {})
    .map(([modality, delta]) => ({
      modality: formatModalityLabel(modality),
      delta: Number(delta) || 0,
    }))
    .sort((a, b) => b.delta - a.delta);
}

export function buildRecurringErrorsChartData(
  topRecurringErrors: Record<string, number> | null | undefined,
  limit = 8
): Array<{ error: string; count: number }> {
  return Object.entries(topRecurringErrors || {})
    .map(([error, count]) => ({
      error: formatModalityLabel(error),
      count: Number(count) || 0,
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, limit);
}

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
      exercise: formatModalityLabel(item.exerciseType || 'UNKNOWN'),
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
    Object.keys(data.modalityMeanDelta || {}).length > 0 ||
    Object.keys(data.topRecurringErrors || {}).length > 0 ||
    (data.mastery || []).length > 0 ||
    (data.profileHistory || []).length > 0
  );
}
