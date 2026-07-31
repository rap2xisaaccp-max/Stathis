'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  fetchAdaptiveInsights,
  fetchAdaptiveEvaluation,
  fetchClassroomEvaluation,
  fetchDifficultyRecommendations,
  AdaptiveInsightsDTO,
  AdaptiveEvaluationSummaryDTO,
  ClassroomEvaluationDTO,
  DifficultyRecommendationDTO,
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
  AlertCircle,
  Brain,
  Target,
  Copy,
  Check,
  ExternalLink,
  RefreshCw,
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

function formatLift(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(3)}`;
}

function formatSuccessLift(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${(value * 100).toFixed(1)} pp`;
}

function suggestionText(item: {
  exerciseType: string;
  recommendedDifficulty?: string | null;
  recommendedGoalReps?: number | null;
}): string {
  const difficulty = item.recommendedDifficulty || 'BEGINNER';
  const reps = item.recommendedGoalReps ?? 8;
  return `${item.exerciseType}: difficulty ${difficulty}, goalReps ${reps}`;
}

function MasteryRecommendationRow({
  item,
}: {
  item: {
    physicalId?: string;
    exerciseType: string;
    masteryLevel?: number;
    sessionsCount?: number | null;
    recommendedDifficulty?: string | null;
    recommendedGoalReps?: number | null;
    recommendationRationale?: string | null;
    requiresTeacherApproval?: boolean;
  };
}) {
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
    <div className="space-y-2 rounded-xl border border-border/50 bg-background/40 p-3">
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
      {item.masteryLevel != null && (
        <>
          <Progress value={Math.round((item.masteryLevel || 0) * 100)} />
          <p className="text-xs text-muted-foreground">
            Mastery {Math.round((item.masteryLevel || 0) * 100)}% · Sessions{' '}
            {item.sessionsCount ?? 0}
          </p>
        </>
      )}
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
            Open classrooms to apply
          </Link>
        </Button>
      </div>
      <p className="text-[11px] text-muted-foreground">
        Soft recommendation only — never auto-applied to exercise templates.
      </p>
    </div>
  );
}

function QueryErrorCard({
  title,
  message,
  onRetry,
}: {
  title: string;
  message: string;
  onRetry?: () => void;
}) {
  return (
    <Card className="rounded-2xl border-destructive/30 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <AlertCircle className="h-4 w-4 text-destructive" />
          {title}
        </CardTitle>
        <CardDescription>{message}</CardDescription>
      </CardHeader>
      {onRetry && (
        <CardContent>
          <Button type="button" size="sm" variant="outline" onClick={onRetry}>
            <RefreshCw className="mr-1 h-3.5 w-3.5" />
            Retry
          </Button>
        </CardContent>
      )}
    </Card>
  );
}

export function AdaptiveLearningInsights({
  studentId,
  classroomId,
}: {
  studentId: string;
  classroomId?: string;
}) {
  const insightsQuery = useQuery({
    queryKey: ['adaptive-insights', studentId],
    queryFn: () => fetchAdaptiveInsights(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 60 * 2,
    retry: 1,
  });
  const evaluationQuery = useQuery({
    queryKey: ['adaptive-evaluation', studentId],
    queryFn: () => fetchAdaptiveEvaluation(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 60 * 2,
    retry: 1,
  });
  const classroomQuery = useQuery({
    queryKey: ['adaptive-classroom-evaluation', classroomId],
    queryFn: () => fetchClassroomEvaluation(classroomId!),
    enabled: !!classroomId,
    staleTime: 1000 * 60 * 2,
    retry: 1,
  });
  const difficultyQuery = useQuery({
    queryKey: ['adaptive-difficulty-recommendations', studentId],
    queryFn: () => fetchDifficultyRecommendations(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 60 * 2,
    retry: 1,
  });

  if (insightsQuery.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-28 w-full rounded-2xl" />
        <Skeleton className="h-64 w-full rounded-2xl" />
        <Skeleton className="h-64 w-full rounded-2xl" />
      </div>
    );
  }

  if (insightsQuery.isError) {
    return (
      <QueryErrorCard
        title="Could not load adaptive insights"
        message={
          insightsQuery.error instanceof Error
            ? insightsQuery.error.message
            : 'The adaptive API request failed. This is not an empty profile — try again.'
        }
        onRetry={() => insightsQuery.refetch()}
      />
    );
  }

  if (!insightsQuery.data) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
      data={insightsQuery.data}
      evaluation={evaluationQuery.data}
      evaluationError={evaluationQuery.isError}
      onRetryEvaluation={() => evaluationQuery.refetch()}
      classroomId={classroomId}
      classroomEvaluation={classroomQuery.data}
      classroomLoading={classroomQuery.isLoading}
      classroomError={classroomQuery.isError}
      onRetryClassroom={() => classroomQuery.refetch()}
      difficultyRecommendations={difficultyQuery.data}
      difficultyError={difficultyQuery.isError}
    />
  );
}

function ClassroomAblationCard({
  classroomId,
  classroomEvaluation,
  isLoading,
  isError,
  onRetry,
}: {
  classroomId?: string;
  classroomEvaluation: ClassroomEvaluationDTO | null | undefined;
  isLoading?: boolean;
  isError?: boolean;
  onRetry?: () => void;
}) {
  if (!classroomId) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="text-base">Classroom comparison</CardTitle>
          <CardDescription>
            Open this student from Student Progress with a classroom selected to
            compare Adaptive vs Static coaching for the whole class.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" size="sm" variant="outline" asChild>
            <Link href="/student-progress?focus=adaptive">Go to Student Progress</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (isLoading) {
    return <Skeleton className="h-40 w-full rounded-2xl" />;
  }

  if (isError) {
    return (
      <QueryErrorCard
        title="Classroom comparison failed"
        message="Could not load classroom Adaptive vs Static metrics."
        onRetry={onRetry}
      />
    );
  }

  if (!classroomEvaluation) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="text-base">Classroom comparison</CardTitle>
          <CardDescription>
            No classroom coaching comparison yet. Both Adaptive and Static arms need
            logged interventions before lift metrics appear.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  const bothArmsHaveData =
    classroomEvaluation.adaptiveMeanDelta != null &&
    classroomEvaluation.staticMeanDelta != null;

  if (!bothArmsHaveData) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="text-base">Classroom comparison (Adaptive vs Static)</CardTitle>
          <CardDescription>
            Insufficient data for Adaptive vs Static comparison. Both study arms require
            completed sessions with logged interventions before lift, success-rate lift, and
            Cohen&apos;s d are shown.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 text-sm">
          <div>
            <p className="text-muted-foreground">Adaptive improvement (mean Δ)</p>
            <p className="font-medium">
              {formatDelta(classroomEvaluation.adaptiveMeanDelta)}
            </p>
          </div>
          <div>
            <p className="text-muted-foreground">Static improvement (mean Δ)</p>
            <p className="font-medium">{formatDelta(classroomEvaluation.staticMeanDelta)}</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Classroom comparison (Adaptive vs Static)</CardTitle>
        <CardDescription>
          How personalized coaching compares to fixed text coaching across{' '}
          {classroomEvaluation.studentCount} students. Research notes live in{' '}
          <code className="rounded bg-muted px-1 py-0.5 text-xs">
            docs/apsle-rct-protocol.md
          </code>{' '}
          in the repo (not a web route).
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 text-sm">
        <div>
          <p className="text-muted-foreground">Adaptive improvement (mean Δ)</p>
          <p className="font-medium">{formatDelta(classroomEvaluation.adaptiveMeanDelta)}</p>
          <p className="text-[11px] text-muted-foreground">
            Average form severity reduction after Adaptive feedback
          </p>
        </div>
        <div>
          <p className="text-muted-foreground">Static improvement (mean Δ)</p>
          <p className="font-medium">{formatDelta(classroomEvaluation.staticMeanDelta)}</p>
          <p className="text-[11px] text-muted-foreground">
            Average reduction after fixed text-only feedback
          </p>
        </div>
        <div>
          <p className="text-muted-foreground">Improvement lift (Δ)</p>
          <p className="font-medium">{formatLift(classroomEvaluation.meanDeltaLift)}</p>
          <p className="text-[11px] text-muted-foreground">
            Adaptive mean Δ minus Static mean Δ
          </p>
        </div>
        <div>
          <p className="text-muted-foreground">Success-rate lift</p>
          <p className="font-medium">
            {formatSuccessLift(classroomEvaluation.successRateLift)}
          </p>
          <p className="text-[11px] text-muted-foreground">
            Extra percentage points of successful corrections for Adaptive
          </p>
        </div>
        <div>
          <p className="text-muted-foreground">Effect size (Cohen&apos;s d)</p>
          <p className="font-medium">
            {classroomEvaluation.cohensD == null
              ? '—'
              : classroomEvaluation.cohensD.toFixed(2)}
          </p>
          <p className="text-[11px] text-muted-foreground">
            ~0.2 small · ~0.5 medium · ~0.8 large standardized difference
          </p>
        </div>
        <div className="sm:col-span-2 lg:col-span-3">
          <Badge
            variant={
              classroomEvaluation.adaptiveOutperformsOnDelta ? 'default' : 'secondary'
            }
          >
            {classroomEvaluation.adaptiveOutperformsOnDelta
              ? 'Adaptive outperforms Static on mean improvement'
              : 'No Adaptive mean-improvement advantage yet'}
          </Badge>
        </div>
      </CardContent>
    </Card>
  );
}

function EvaluationSummaryCard({
  evaluation,
  isError,
  onRetry,
}: {
  evaluation: AdaptiveEvaluationSummaryDTO | null | undefined;
  isError?: boolean;
  onRetry?: () => void;
}) {
  if (isError) {
    return (
      <QueryErrorCard
        title="RCT summary failed"
        message="Could not load this student's Adaptive vs Static evaluation summary."
        onRetry={onRetry}
      />
    );
  }
  if (!evaluation) return null;
  const armEntries = Object.entries(evaluation.interventionsByArm || {});

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Study arm summary</CardTitle>
        <CardDescription>
          Adaptive = personalized coaching modality. Static = fixed text feedback
          (control). Includes practice sessions logged for research volume.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-sm">
        <div>
          <p className="text-muted-foreground">Primary arm</p>
          <p className="font-medium">{evaluation.experimentArm || '—'}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Mean form improvement (Δ)</p>
          <p className="font-medium">{formatDelta(evaluation.meanDelta)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Task coaching events</p>
          <p className="font-medium">{evaluation.taskInterventions ?? 0}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Practice coaching events</p>
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

function RecentInterventionsCard({ data }: { data: AdaptiveInsightsDTO }) {
  const recent = (data.recentInterventions || []).slice(0, 8);
  if (recent.length === 0) return null;

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Recent coaching events</CardTitle>
        <CardDescription>Latest closed-loop interventions for this student</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {recent.map((item) => (
          <div
            key={item.physicalId}
            className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/40 px-3 py-2 text-sm"
          >
            <div className="min-w-0">
              <p className="font-medium truncate">
                {item.exerciseType.replaceAll('_', ' ')} ·{' '}
                {item.errorCode.replaceAll('_', ' ')}
              </p>
              <p className="text-xs text-muted-foreground truncate">
                {item.messageText || 'Coaching cue delivered'}
              </p>
            </div>
            <div className="flex flex-wrap gap-1">
              <Badge variant="secondary">{item.modality.replaceAll('_', ' ')}</Badge>
              {item.experimentArm && <Badge variant="outline">{item.experimentArm}</Badge>}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function AdaptiveInsightsBody({
  data,
  evaluation,
  evaluationError,
  onRetryEvaluation,
  classroomId,
  classroomEvaluation,
  classroomLoading,
  classroomError,
  onRetryClassroom,
  difficultyRecommendations,
  difficultyError,
}: {
  data: AdaptiveInsightsDTO;
  evaluation?: AdaptiveEvaluationSummaryDTO | null;
  evaluationError?: boolean;
  onRetryEvaluation?: () => void;
  classroomId?: string;
  classroomEvaluation?: ClassroomEvaluationDTO | null;
  classroomLoading?: boolean;
  classroomError?: boolean;
  onRetryClassroom?: () => void;
  difficultyRecommendations?: DifficultyRecommendationDTO[];
  difficultyError?: boolean;
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
  const softRecs: Array<{
    physicalId?: string;
    exerciseType: string;
    masteryLevel?: number;
    sessionsCount?: number | null;
    recommendedDifficulty?: string | null;
    recommendedGoalReps?: number | null;
    recommendationRationale?: string | null;
    requiresTeacherApproval?: boolean;
  }> =
    mastery.length > 0
      ? mastery
      : (difficultyRecommendations || []).map((rec) => ({
          physicalId: `${rec.studentId}-${rec.exerciseType}`,
          exerciseType: rec.exerciseType,
          masteryLevel: rec.masteryLevel,
          sessionsCount: rec.sessionsCount,
          recommendedDifficulty: rec.recommendedDifficulty,
          recommendedGoalReps: rec.recommendedGoalReps,
          recommendationRationale: rec.rationale,
          requiresTeacherApproval: rec.requiresTeacherApproval,
        }));

  return (
    <div className="space-y-4">
      <EvaluationSummaryCard
        evaluation={evaluation}
        isError={evaluationError}
        onRetry={onRetryEvaluation}
      />
      <ClassroomAblationCard
        classroomId={classroomId}
        classroomEvaluation={classroomEvaluation}
        isLoading={classroomLoading}
        isError={classroomError}
        onRetry={onRetryClassroom}
      />
      <RecentInterventionsCard data={data} />

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
        <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
        <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
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
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader>
              <CardTitle className="text-base">Mastery by exercise</CardTitle>
              <CardDescription>No mastery records yet.</CardDescription>
            </CardHeader>
          </Card>
        )}
      </div>

      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Target className="h-4 w-4" />
            Exercise mastery & difficulty
          </CardTitle>
          <CardDescription>
            Soft goalReps / difficulty suggestions — copy and apply manually in classroom
            templates. Never auto-applied.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {softRecs.length === 0 && (
            <p className="text-sm text-muted-foreground">
              {difficultyError
                ? 'Difficulty recommendations could not be loaded, and no mastery records exist yet.'
                : 'No mastery or difficulty recommendations yet.'}
            </p>
          )}
          {softRecs.map((item) => (
            <MasteryRecommendationRow
              key={item.physicalId || item.exerciseType}
              item={item}
            />
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
