'use client';

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchAdaptiveInsights,
  fetchDifficultyRecommendations,
} from '@/services/adaptive/api-adaptive-client';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { BarChart } from '@/components/dashboard/bar-chart';
import { AlertCircle, RefreshCw } from 'lucide-react';
import {
  buildMasteryByExerciseChartData,
  buildRecurringErrorsChartData,
} from '@/components/adaptive/adaptive-insights-charts';
import {
  StudentTaskStatsModal,
  ProgressSnapshotItem,
} from '@/components/adaptive/StudentTaskStatsModal';
import { FormCorrectionEvidenceLog } from '@/components/adaptive/FormCorrectionEvidenceLog';

export function AdaptiveLearningInsights({
  studentId,
  classroomId,
  progressItems,
}: {
  studentId: string;
  classroomId?: string;
  progressItems?: ProgressSnapshotItem[];
}) {
  const insightsQuery = useQuery({
    queryKey: ['adaptive-insights', studentId],
    queryFn: () => fetchAdaptiveInsights(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });
  const difficultyQuery = useQuery({
    queryKey: ['adaptive-difficulty-recommendations', studentId],
    queryFn: () => fetchDifficultyRecommendations(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });

  const insights = insightsQuery.data;
  const recurring = buildRecurringErrorsChartData(insights?.topRecurringErrors);
  const masteryChart = buildMasteryByExerciseChartData(insights?.mastery);

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-lg font-semibold">Form Correction Evidence Log</h3>
        <p className="mb-3 text-sm text-muted-foreground">
          Classroom → Task → Snapshot gallery. Each task keeps its own carousel so snapshots are never mixed.
        </p>
        <FormCorrectionEvidenceLog
          studentId={studentId}
          classroomId={classroomId}
          progressItems={progressItems}
        />
      </div>

      {progressItems && progressItems.length > 0 ? (
        <StudentProgressSnapshotCard studentId={studentId} progressItems={progressItems} />
      ) : null}

      {insightsQuery.isLoading ? (
        <Skeleton className="h-40 w-full rounded-2xl" />
      ) : insightsQuery.isError ? (
        <Card className="border-destructive/30">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <AlertCircle className="h-4 w-4" />
              Could not load coaching summary
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Button type="button" size="sm" variant="outline" onClick={() => insightsQuery.refetch()}>
              <RefreshCw className="mr-1 h-3.5 w-3.5" />
              Retry
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          {recurring.length > 0 ? (
            <BarChart
              title="Recurring physical form errors"
              description="Coachable errors only. Camera and model issues are excluded."
              data={recurring.map((row) => ({ error: row.error, count: row.count }))}
              categories={['count']}
              index="error"
            />
          ) : null}

          {masteryChart.length > 0 ? (
            <Card className="rounded-2xl border-border/50">
              <CardContent className="pt-6">
                <BarChart
                  title="Form mastery by exercise"
                  description="Based on sessions and how often form corrections were needed."
                  data={masteryChart.map((row) => ({
                    exercise: row.exercise,
                    masteryPct: row.masteryPct,
                  }))}
                  categories={['masteryPct']}
                  index="exercise"
                  yDomain={[0, 100]}
                  valueSuffix="%"
                />
                <div className="mt-3 flex flex-wrap gap-2">
                  {(difficultyQuery.data || []).map((item) => (
                    <Badge key={item.exerciseType} variant="outline">
                      {item.exerciseType.replaceAll('_', ' ')}
                      {item.recommendedDifficulty ? ` · ${item.recommendedDifficulty}` : ''}
                    </Badge>
                  ))}
                </div>
              </CardContent>
            </Card>
          ) : null}
        </>
      )}
    </div>
  );
}

function StudentProgressSnapshotCard({
  studentId,
  progressItems,
}: {
  studentId: string;
  progressItems: ProgressSnapshotItem[];
}) {
  const [openTaskId, setOpenTaskId] = React.useState<string | null>(null);
  const selected = progressItems.find((item) => item.taskId === openTaskId) ?? null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Classroom task snapshot</CardTitle>
        <CardDescription>Task scores remain on the Scores tab. This is context only.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {progressItems.slice(0, 6).map((item) => (
          <button
            key={item.taskId}
            type="button"
            className="flex w-full items-center justify-between rounded-lg border border-border/50 px-3 py-2 text-left text-sm hover:bg-muted/40"
            onClick={() => setOpenTaskId(item.taskId)}
          >
            <span>{item.taskName}</span>
            <span className="text-muted-foreground">
              {item.score != null ? `${item.score}${item.maxScore != null ? `/${item.maxScore}` : ''}` : '—'}
            </span>
          </button>
        ))}
        <StudentTaskStatsModal
          open={!!selected}
          onOpenChange={(open) => {
            if (!open) setOpenTaskId(null);
          }}
          studentId={studentId}
          task={selected}
        />
      </CardContent>
    </Card>
  );
}
