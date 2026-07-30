'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  fetchAdaptiveInsights,
  fetchAdaptiveEvaluation,
  fetchClassroomEvaluation,
  AdaptiveInsightsDTO,
  AdaptiveEvaluationSummaryDTO,
  ClassroomEvaluationDTO,
  ExerciseMasteryDTO,
} from '@/services/adaptive/api-adaptive-client';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { BarChart } from '@/components/dashboard/bar-chart';
import { LineChart } from '@/components/dashboard/line-chart';
import {
  Brain,
  Target,
  Copy,
  Check,
  ExternalLink,
} from 'lucide-react';
import {
  buildMasteryByExerciseChartData,
  buildMasteryTimelineChartData,
  buildModalityEffectivenessChartData,
  buildRecurringErrorsChartData,
} from '@/components/adaptive/adaptive-insights-charts';

function formatPct(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  return `${Math.round(value * 100)}%`;
}

function formatDelta(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}`;
}

function suggestionText(item: ExerciseMasteryDTO): string {
  const difficulty = item.recommendedDifficulty || 'BEGINNER';
  const reps = item.recommendedGoalReps ?? 8;
  return `${item.exerciseType}: difficulty ${difficulty}, goalReps ${reps}`;
}

function MasteryRecommendationRow({ item }: { item: ExerciseMasteryDTO }) {
  const [copied, setCopied] = useState(false);

  const onCopy = async () => {
    try {
      await navigator.clipboard.writeText(suggestionText(item));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="space-y-2 rounded-md border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-medium">{item.exerciseType.replaceAll('_', ' ')}</span>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">
            Suggest {item.recommendedDifficulty || 'BEGINNER'}
          </Badge>
          <Badge variant="outline">~{item.recommendedGoalReps ?? 8} reps</Badge>
          {(item.requiresTeacherApproval ?? true) && (
            <Badge variant="outline">Teacher approval</Badge>
          )}
        </div>
      </div>
      <Progress value={Math.round((item.masteryLevel || 0) * 100)} />
      <p className="text-xs text-muted-foreground">
        Mastery {Math.round((item.masteryLevel || 0) * 100)}% · Sessions{' '}
        {item.sessionsCount ?? 0}
      </p>
      {item.recommendationRationale && (
        <p className="text-xs text-muted-foreground leading-relaxed">
          {item.recommendationRationale}
        </p>
      )}
      <div className="flex flex-wrap gap-2 pt-1">
        <Button type="button" size="sm" variant="outline" onClick={onCopy}>
          {copied ? (
            <>
              <Check className="mr-1 h-3.5 w-3.5" />
              Copied
            </>
          ) : (
            <>
              <Copy className="mr-1 h-3.5 w-3.5" />
              Copy suggestion
            </>
          )}
        </Button>
        <Button type="button" size="sm" variant="ghost" asChild>
          <Link href="/classroom">
            <ExternalLink className="mr-1 h-3.5 w-3.5" />
            Open classrooms
          </Link>
        </Button>
      </div>
    </div>
  );
}

export function AdaptiveLearningInsights({
  studentId,
  classroomId,
}: {
  studentId: string;
  classroomId?: string;
}) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['adaptive-insights', studentId],
    queryFn: () => fetchAdaptiveInsights(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 60 * 2,
  });
  const { data: evaluation } = useQuery({
    queryKey: ['adaptive-evaluation', studentId],
    queryFn: () => fetchAdaptiveEvaluation(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 60 * 2,
  });
  const { data: classroomEvaluation } = useQuery({
    queryKey: ['adaptive-classroom-evaluation', classroomId],
    queryFn: () => fetchClassroomEvaluation(classroomId!),
    enabled: !!classroomId,
    staleTime: 1000 * 60 * 2,
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-64 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Brain className="h-4 w-4" />
            Adaptive learning
          </CardTitle>
          <CardDescription>
            No adaptive coaching data yet for this student. Insights appear after
            exercise sessions with closed-loop feedback.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <AdaptiveInsightsBody
      data={data}
      evaluation={evaluation}
      classroomEvaluation={classroomEvaluation}
    />
  );
}

function formatLift(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(3)}`;
}

function ClassroomAblationCard({
  classroomEvaluation,
}: {
  classroomEvaluation: ClassroomEvaluationDTO | null | undefined;
}) {
  if (!classroomEvaluation) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Classroom ablation (ADAPTIVE vs STATIC)</CardTitle>
        <CardDescription>
          Aggregated RCT contrast for {classroomEvaluation.studentCount} students — see{' '}
          <code className="text-xs">docs/apsle-rct-protocol.md</code>
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-sm">
        <div>
          <p className="text-muted-foreground">Adaptive mean Δ</p>
          <p className="font-medium">{formatDelta(classroomEvaluation.adaptiveMeanDelta)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Static mean Δ</p>
          <p className="font-medium">{formatDelta(classroomEvaluation.staticMeanDelta)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Δ lift</p>
          <p className="font-medium">{formatLift(classroomEvaluation.meanDeltaLift)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Cohen&apos;s d</p>
          <p className="font-medium">
            {classroomEvaluation.cohensD == null
              ? '—'
              : classroomEvaluation.cohensD.toFixed(2)}
          </p>
        </div>
        <div className="sm:col-span-2 lg:col-span-4">
          <Badge
            variant={
              classroomEvaluation.adaptiveOutperformsOnDelta ? 'default' : 'secondary'
            }
          >
            {classroomEvaluation.adaptiveOutperformsOnDelta
              ? 'Adaptive outperforms on mean Δ'
              : 'No adaptive mean-Δ advantage yet'}
          </Badge>
        </div>
      </CardContent>
    </Card>
  );
}

function EvaluationSummaryCard({
  evaluation,
}: {
  evaluation: AdaptiveEvaluationSummaryDTO | null | undefined;
}) {
  if (!evaluation) return null;
  const armEntries = Object.entries(evaluation.interventionsByArm || {});

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">RCT evaluation summary</CardTitle>
        <CardDescription>
          Adaptive vs static arms, including practice-session research volume
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-sm">
        <div>
          <p className="text-muted-foreground">Primary arm</p>
          <p className="font-medium">{evaluation.experimentArm || '—'}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Mean Δ</p>
          <p className="font-medium">{formatDelta(evaluation.meanDelta)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Task interventions</p>
          <p className="font-medium">{evaluation.taskInterventions ?? 0}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Practice interventions</p>
          <p className="font-medium">{evaluation.practiceInterventions ?? 0}</p>
        </div>
        {armEntries.length > 0 && (
          <div className="sm:col-span-2 lg:col-span-4 flex flex-wrap gap-2 pt-1">
            {armEntries.map(([arm, count]) => (
              <Badge key={arm} variant="outline">
                {arm}: {count}
              </Badge>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function AdaptiveInsightsBody({
  data,
  evaluation,
  classroomEvaluation,
}: {
  data: AdaptiveInsightsDTO;
  evaluation?: AdaptiveEvaluationSummaryDTO | null;
  classroomEvaluation?: ClassroomEvaluationDTO | null;
}) {
  const modalityData = useMemo(
    () => buildModalityEffectivenessChartData(data.modalityMeanDelta),
    [data.modalityMeanDelta]
  );
  const errorData = useMemo(
    () => buildRecurringErrorsChartData(data.topRecurringErrors),
    [data.topRecurringErrors]
  );
  const masteryBars = useMemo(
    () => buildMasteryByExerciseChartData(data.mastery),
    [data.mastery]
  );
  const timelineData = useMemo(
    () => buildMasteryTimelineChartData(data.profileHistory),
    [data.profileHistory]
  );
  const mastery = data.mastery || [];

  return (
    <div className="space-y-4">
      <EvaluationSummaryCard evaluation={evaluation} />
      <ClassroomAblationCard classroomEvaluation={classroomEvaluation} />

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Preferred modality</CardDescription>
            <CardTitle className="text-lg">
              {data.profile?.preferredModality?.replaceAll('_', ' ') || 'Building…'}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Derived from measured post-feedback improvement.
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Intervention success</CardDescription>
            <CardTitle className="text-lg">
              {formatPct(data.overallSuccessRate)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {data.successfulInterventions}/{data.totalInterventions} successful
            corrections
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Learning rate (EWMA Δ)</CardDescription>
            <CardTitle className="text-lg">
              {formatDelta(data.profile?.learningRateEstimate)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Consistency {formatPct(data.profile?.consistencyScore)}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {modalityData.length > 0 ? (
          <div className="min-h-[300px]">
            <BarChart
              title="Modality effectiveness"
              description="Mean severity reduction after each feedback channel"
              data={modalityData}
              index="modality"
              categories={['delta']}
              colors={['hsl(var(--primary))']}
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px]">
            <CardHeader>
              <CardTitle className="text-base">Modality effectiveness</CardTitle>
              <CardDescription>No modality evidence yet.</CardDescription>
            </CardHeader>
          </Card>
        )}

        {errorData.length > 0 ? (
          <div className="min-h-[300px]">
            <BarChart
              title="Recurring form errors"
              description="Most frequent targeted corrections"
              data={errorData}
              index="error"
              categories={['count']}
              colors={['hsl(var(--destructive))']}
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px]">
            <CardHeader>
              <CardTitle className="text-base">Recurring form errors</CardTitle>
              <CardDescription>No recurring errors logged.</CardDescription>
            </CardHeader>
          </Card>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {timelineData.length > 0 ? (
          <div className="min-h-[300px]">
            <LineChart
              title="Mastery timeline"
              description="Mean mastery and consistency across profile snapshots"
              data={timelineData}
              index="date"
              categories={['masteryPct', 'consistencyPct']}
              colors={['hsl(var(--primary))', 'hsl(var(--secondary))']}
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px]">
            <CardHeader>
              <CardTitle className="text-base">Mastery timeline</CardTitle>
              <CardDescription>
                Timeline appears after adaptive sessions create profile snapshots.
              </CardDescription>
            </CardHeader>
          </Card>
        )}

        {masteryBars.length > 0 ? (
          <div className="min-h-[300px]">
            <BarChart
              title="Mastery by exercise"
              description="Current mastery level per exercise type"
              data={masteryBars}
              index="exercise"
              categories={['masteryPct']}
              colors={['hsl(var(--chart-2, var(--primary)))']}
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px]">
            <CardHeader>
              <CardTitle className="text-base">Mastery by exercise</CardTitle>
              <CardDescription>No mastery records yet.</CardDescription>
            </CardHeader>
          </Card>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Target className="h-4 w-4" />
            Exercise mastery & difficulty
          </CardTitle>
          <CardDescription>
            Soft goalReps / difficulty suggestions — never auto-applied to templates
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {mastery.length === 0 && (
            <p className="text-sm text-muted-foreground">No mastery records yet.</p>
          )}
          {mastery.map((item) => (
            <MasteryRecommendationRow key={item.physicalId} item={item} />
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
