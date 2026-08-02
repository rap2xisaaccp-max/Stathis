'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
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
  formatExerciseDifficulty,
  normalizeExerciseDifficulty,
  snapGoalRepsToTemplateOptions,
} from '@/lib/exercise-difficulty';
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
  formatModalityLabel,
  MASTERY_CATEGORY_NAMES,
  MASTERY_CHART_Y_DOMAIN,
  TIMELINE_CATEGORY_NAMES,
} from '@/components/adaptive/adaptive-insights-charts';
import {
  closedLoopSuccessCopy,
  formErrorLabel,
  formatImprovementDeltaTooltip,
  formatLearningProgressLabel,
  formatLearningProgressTooltip,
  isInsufficientFormCorrectionData,
  learningProgressDescription,
  MORE_COACHING_DATA_NEEDED,
  preferredModalityCopy,
} from '@/components/adaptive/form-error-labels';

function formatPct(value: number | null | undefined, n?: number): string {
  if (n === 0) return 'Insufficient data';
  if (value == null || Number.isNaN(value)) return '—';
  return `${Math.round(value * 100)}%`;
}

function showApsleResearchUi(searchParams: URLSearchParams | null): boolean {
  if (process.env.NEXT_PUBLIC_APSLE_SHOW_RCT === 'true') return true;
  return searchParams?.get('research') === '1';
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
  recommendationRationale?: string | null;
  sessionsCount?: number | null;
  masteryLevel?: number;
}): string {
  if (isInsufficientFormCorrectionData(item.sessionsCount, item.masteryLevel)) {
    return `${item.exerciseType}: More Coaching Data Needed`;
  }
  const bits: string[] = [];
  if (item.recommendedDifficulty) {
    bits.push(`difficulty ${item.recommendedDifficulty}`);
  }
  if (item.recommendedGoalReps != null) {
    bits.push(`goalReps ${item.recommendedGoalReps}`);
  }
  return bits.length > 0
    ? `${item.exerciseType}: ${bits.join(', ')}`
    : item.exerciseType;
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
    topErrors?: string[];
    lastSessionAt?: string | null;
    medianTimeToCorrectionMs?: number | null;
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

  const lastSessionLabel = item.lastSessionAt
    ? new Date(item.lastSessionAt).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : null;

  const insufficient = isInsufficientFormCorrectionData(
    item.sessionsCount,
    item.masteryLevel
  );

  return (
    <div className="space-y-2 rounded-xl border border-border/50 bg-background/40 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-medium">{item.exerciseType.replaceAll('_', ' ')}</span>
        <div className="flex flex-wrap items-center gap-2">
          {insufficient ? (
            <Badge variant="outline">More Coaching Data Needed</Badge>
          ) : item.recommendedDifficulty ? (
            <Badge variant="secondary">
              Suggest {formatExerciseDifficulty(item.recommendedDifficulty)}
            </Badge>
          ) : (
            <Badge variant="outline">Difficulty —</Badge>
          )}
          {!insufficient && item.recommendedGoalReps != null ? (
            <Badge variant="outline">
              ~{snapGoalRepsToTemplateOptions(item.recommendedGoalReps)} reps
            </Badge>
          ) : null}
          {!insufficient && (item.requiresTeacherApproval ?? true) && (
            <Badge variant="outline">Teacher approval</Badge>
          )}
        </div>
      </div>
      {item.masteryLevel != null && (
        <>
          <Progress value={Math.round((item.masteryLevel || 0) * 100)} />
          <p className="text-xs text-muted-foreground">
            APSLE Form Mastery {Math.round((item.masteryLevel || 0) * 100)}% · Sessions{' '}
            {item.sessionsCount ?? 0}
            {item.medianTimeToCorrectionMs != null &&
              ` · Median correction ${Math.round(item.medianTimeToCorrectionMs / 1000)}s`}
            {lastSessionLabel && ` · Last ${lastSessionLabel}`}
          </p>
        </>
      )}
      {(item.recommendationRationale || (insufficient && MORE_COACHING_DATA_NEEDED)) && (
        <p className="text-xs text-muted-foreground leading-relaxed">
          {insufficient
            ? MORE_COACHING_DATA_NEEDED
            : item.recommendationRationale}
        </p>
      )}
      {(item.topErrors || []).length > 0 && (
        <div className="flex flex-wrap gap-1">
          {(item.topErrors || []).map((err) => (
            <Badge key={err} variant="outline" className="text-[10px]">
              {formErrorLabel(err)}
            </Badge>
          ))}
        </div>
      )}
      {!insufficient && (
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
      )}
      <p className="text-[11px] text-muted-foreground">
        Soft recommendation only — never auto-applied to exercise templates. APSLE Form
        Mastery is separate from classroom task scores.
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
  progressItems,
}: {
  studentId: string;
  classroomId?: string;
  progressItems?: Array<{
    taskId: string;
    taskName: string;
    taskType?: string | null;
    completed?: boolean;
    score?: number | null;
    maxScore?: number | null;
    attempts?: number | null;
    reps?: number | null;
    goalReps?: number | null;
  }>;
}) {
  const searchParams = useSearchParams();
  const showResearch = showApsleResearchUi(searchParams);

  const insightsQuery = useQuery({
    queryKey: ['adaptive-insights', studentId],
    queryFn: () => fetchAdaptiveInsights(studentId),
    enabled: !!studentId,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });
  const evaluationQuery = useQuery({
    queryKey: ['adaptive-evaluation', studentId],
    queryFn: () => fetchAdaptiveEvaluation(studentId),
    enabled: !!studentId && showResearch,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });
  const classroomQuery = useQuery({
    queryKey: ['adaptive-classroom-evaluation', classroomId],
    queryFn: () => fetchClassroomEvaluation(classroomId!),
    enabled: !!classroomId && showResearch,
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

  return (
    <div className="space-y-4">
      <StudentProgressSnapshotCard progressItems={progressItems} />

      {showResearch && (
        <>
          <EvaluationSummaryCard
            evaluation={evaluationQuery.data}
            isError={evaluationQuery.isError}
            isLoading={evaluationQuery.isLoading}
            onRetry={() => evaluationQuery.refetch()}
          />

          <FeedbackEffectivenessCard
            evaluation={evaluationQuery.data}
            isLoading={evaluationQuery.isLoading}
            isError={evaluationQuery.isError}
            onRetry={() => evaluationQuery.refetch()}
          />

          <ClassroomAblationCard
            classroomId={classroomId}
            classroomEvaluation={classroomQuery.data}
            isLoading={classroomQuery.isLoading}
            isError={classroomQuery.isError}
            onRetry={() => classroomQuery.refetch()}
          />
        </>
      )}

      {insightsQuery.isLoading && (
        <>
          <Skeleton className="h-28 w-full rounded-2xl" />
          <Skeleton className="h-64 w-full rounded-2xl" />
        </>
      )}

      {insightsQuery.isError && (
        <QueryErrorCard
          title="Could not load adaptive insights"
          message={
            insightsQuery.error instanceof Error
              ? insightsQuery.error.message
              : 'The adaptive API request failed. This is not an empty profile — try again.'
          }
          onRetry={() => insightsQuery.refetch()}
        />
      )}

      {!insightsQuery.isLoading && !insightsQuery.isError && !insightsQuery.data && (
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
      )}

      {!insightsQuery.isLoading && !insightsQuery.isError && insightsQuery.data && (
        <InsightsChartsSection data={insightsQuery.data} />
      )}

      <AdaptiveRecommendationsCard
        mastery={insightsQuery.data?.mastery}
        difficultyRecommendations={difficultyQuery.data}
        difficultyError={difficultyQuery.isError}
        insightsLoading={insightsQuery.isLoading}
      />
    </div>
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
        <div>
          <p className="text-muted-foreground">Classroom success rate</p>
          <p className="font-medium">{formatPct(classroomEvaluation.overallSuccessRate)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Mean mastery</p>
          <p className="font-medium">{formatPct(classroomEvaluation.meanMasteryLevel)}</p>
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
        {(classroomEvaluation.students || []).length > 0 && (
          <div className="sm:col-span-2 lg:col-span-3 space-y-2 border-t border-border/40 pt-3">
            <p className="text-sm font-medium">Students in this classroom evaluation</p>
            <div className="flex flex-wrap gap-2">
              {(classroomEvaluation.students || []).slice(0, 12).map((s) => (
                <Button key={s.studentId} type="button" size="sm" variant="outline" asChild>
                  <Link
                    href={`/student-progress/${encodeURIComponent(s.studentId)}?classroomId=${encodeURIComponent(classroomId!)}&tab=adaptive`}
                  >
                    {s.studentId}
                    {s.experimentArm ? ` · ${s.experimentArm}` : ''}
                    {` · Δ ${formatDelta(s.meanDelta)}`}
                  </Link>
                </Button>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function EvaluationSummaryCard({
  evaluation,
  isError,
  isLoading,
  onRetry,
}: {
  evaluation: AdaptiveEvaluationSummaryDTO | null | undefined;
  isError?: boolean;
  isLoading?: boolean;
  onRetry?: () => void;
}) {
  if (isLoading) {
    return <Skeleton className="h-40 w-full rounded-2xl" />;
  }
  if (isError) {
    return (
      <QueryErrorCard
        title="RCT summary failed"
        message="Could not load this student's Adaptive vs Static evaluation summary."
        onRetry={onRetry}
      />
    );
  }
  if (!evaluation) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="text-base">Adaptive vs Static evaluation</CardTitle>
          <CardDescription>
            No study-arm summary yet. Evaluation appears after this student has
            closed-loop interventions with Adaptive and/or Static feedback.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }
  const armEntries = Object.entries(evaluation.interventionsByArm || {});

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Adaptive vs Static evaluation</CardTitle>
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
          <p className="text-muted-foreground">Feedback success rate</p>
          <p className="font-medium">{formatPct(evaluation.successRate)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Sessions tracked</p>
          <p className="font-medium">{evaluation.sessionsTracked ?? '—'}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Mean mastery</p>
          <p className="font-medium">{formatPct(evaluation.meanMasteryLevel)}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Task coaching events</p>
          <p className="font-medium">{evaluation.taskInterventions ?? 0}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Practice coaching events</p>
          <p className="font-medium">{evaluation.practiceInterventions ?? 0}</p>
        </div>
        <div>
          <p className="text-muted-foreground">Closed-loop success</p>
          <p className="font-medium">
            {evaluation.successfulInterventions}/{evaluation.totalInterventions}
          </p>
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

function FeedbackEffectivenessCard({
  evaluation,
  isLoading,
  isError,
  onRetry,
}: {
  evaluation?: AdaptiveEvaluationSummaryDTO | null;
  isLoading?: boolean;
  isError?: boolean;
  onRetry?: () => void;
}) {
  const modalityData = useMemo(
    () => buildModalityEffectivenessChartData(evaluation?.meanDeltaByModality),
    [evaluation?.meanDeltaByModality]
  );

  if (isLoading) {
    return <Skeleton className="h-48 w-full rounded-2xl" />;
  }

  if (isError) {
    return (
      <QueryErrorCard
        title="Feedback effectiveness failed"
        message="Could not load closed-loop evaluation metrics for this student."
        onRetry={onRetry}
      />
    );
  }

  if (!evaluation) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="text-base">Feedback effectiveness</CardTitle>
          <CardDescription>
            Effectiveness metrics appear after feedback responses are recorded.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Feedback effectiveness</CardTitle>
        <CardDescription>
          Closed-loop responses only — coaching reduced form severity overall and by
          modality.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-3 text-sm">
          <div>
            <p className="text-muted-foreground">Success rate</p>
            <p className="text-lg font-medium">{formatPct(evaluation.successRate)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">Mean improvement (Δ)</p>
            <p className="text-lg font-medium">{formatDelta(evaluation.meanDelta)}</p>
          </div>
          <div>
            <p className="text-muted-foreground">Closed pairs</p>
            <p className="text-lg font-medium">
              {evaluation.successfulInterventions}/{evaluation.totalInterventions}
            </p>
          </div>
        </div>
        {modalityData.length > 0 ? (
          <div className="min-h-[240px]">
            <BarChart
              title="Effectiveness by modality"
              description="Mean Δ from closed-loop responses (higher = more severity reduction)"
              data={modalityData}
              index="modality"
              categories={['delta']}
              colors={['var(--chart-3)']}
              className="h-full"
            />
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            Per-modality effectiveness will appear once multiple modalities have
            response windows.
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function StudentProgressSnapshotCard({
  progressItems,
}: {
  progressItems?: Array<{
    taskId: string;
    taskName: string;
    taskType?: string | null;
    completed?: boolean;
    score?: number | null;
    maxScore?: number | null;
    attempts?: number | null;
    reps?: number | null;
    goalReps?: number | null;
  }>;
}) {
  const items = (progressItems || [])
    .filter((item) => (item.taskType || '').toUpperCase() !== 'LESSON')
    .slice(0, 6);

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Student progress snapshot</CardTitle>
        <CardDescription>
          Classroom task scores and attempts (same live backend data as the Scores tab).
          These are separate from APSLE Form Mastery below.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No scored quiz/exercise progress yet for this classroom.
          </p>
        ) : (
          <div className="space-y-2">
            {items.map((item) => (
              <div
                key={`${item.taskId}-${item.taskType}`}
                className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/40 px-3 py-2 text-sm"
              >
                <div className="min-w-0">
                  <p className="font-medium truncate">{item.taskName}</p>
                  <p className="text-xs text-muted-foreground">
                    {(item.taskType || 'TASK').replaceAll('_', ' ')}
                    {item.attempts != null ? ` · ${item.attempts} attempts` : ''}
                    {item.reps != null
                      ? ` · ${item.reps}${item.goalReps != null ? `/${item.goalReps}` : ''} reps`
                      : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {item.completed ? (
                    <Badge variant="secondary">Done</Badge>
                  ) : (
                    <Badge variant="outline">Open</Badge>
                  )}
                  <span className="font-medium">
                    {item.score != null
                      ? `${item.score}/${item.maxScore ?? 100}`
                      : '—'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function PreferredModalityByExerciseCard({ data }: { data: AdaptiveInsightsDTO }) {
  const byExercise =
    data.preferredModalityByExercise ||
    data.profile?.preferredModalityByExercise ||
    {};
  const entries = Object.entries(byExercise);
  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Preferred Modality by Exercise</CardTitle>
        <CardDescription>
          Shows which coaching method helped the student improve the most for each exercise.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {entries.length === 0 ? (
          <p className="text-sm text-muted-foreground">Insufficient data</p>
        ) : (
          entries.map(([exercise, row]) => {
            const copy = preferredModalityCopy({
              modality: row?.modality,
              source: row?.source,
              n: row?.n,
            });
            return (
              <div
                key={exercise}
                className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/40 px-3 py-2 text-sm"
              >
                <span className="font-medium">{exercise.replaceAll('_', ' ')}</span>
                <div className="min-w-0 text-right">
                  <Badge variant="secondary">{copy.title}</Badge>
                  <p className="mt-1 text-[11px] text-muted-foreground">{copy.detail}</p>
                </div>
              </div>
            );
          })
        )}
      </CardContent>
    </Card>
  );
}

function RecentInterventionsCard({ data }: { data: AdaptiveInsightsDTO }) {
  const recent = data.recentInterventions || [];
  if (recent.length === 0) {
    return (
      <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
        <CardHeader>
          <CardTitle className="text-base">Recent Adaptive Interventions</CardTitle>
          <CardDescription>
            No recent coaching cues for this student yet.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="text-base">Recent Adaptive Interventions</CardTitle>
        <CardDescription>
          Recent coaching cues for this student ({recent.length})
        </CardDescription>
      </CardHeader>
      <CardContent className="max-h-80 space-y-2 overflow-y-auto pr-1">
        {recent.map((item) => {
          const when = item.deliveredAt
            ? new Date(item.deliveredAt).toLocaleString(undefined, {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })
            : null;
          const improved =
            item.responseSuccess == null
              ? 'Pending'
              : item.responseSuccess
                ? 'Improved'
                : 'No change';
          const deltaTooltip = formatImprovementDeltaTooltip(item.responseDelta);
          return (
            <div
              key={item.physicalId}
              className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border/40 px-3 py-2 text-sm"
            >
              <div className="min-w-0">
                <p className="font-medium truncate">
                  {item.exerciseType.replaceAll('_', ' ')} ·{' '}
                  {formErrorLabel(item.errorCode)}
                </p>
                <p className="text-xs text-muted-foreground truncate">
                  {item.messageText || item.correctionDelivered || 'Coaching cue delivered'}
                  {when ? ` · ${when}` : ''}
                </p>
              </div>
              <div className="flex flex-wrap gap-1">
                <Badge variant="secondary">{formatModalityLabel(item.modality)}</Badge>
                <Badge
                  variant="outline"
                  title={
                    item.responseSuccess && deltaTooltip ? deltaTooltip : undefined
                  }
                >
                  {improved}
                </Badge>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function InsightsChartsSection({ data }: { data: AdaptiveInsightsDTO }) {
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

  return (
    <>
      <div>
        <h3 className="mb-3 text-sm font-semibold tracking-wide text-muted-foreground">
          Learning profile summary
        </h3>
        <div className="grid gap-4 md:grid-cols-3">
          <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader className="pb-2">
              <CardDescription>Preferred modality (overall)</CardDescription>
              <CardTitle className="text-lg">
                {(data.profile?.totalInterventions ?? 0) < 1
                  ? 'Insufficient data'
                  : formatModalityLabel(data.profile?.preferredModality) || 'Learning'}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              Shows the coaching method that helped this student improve most overall.
              {data.profile?.updatedAt && (
                <span className="mt-1 block text-[11px]">
                  Updated{' '}
                  {new Date(data.profile.updatedAt).toLocaleDateString(undefined, {
                    month: 'short',
                    day: 'numeric',
                  })}
                </span>
              )}
            </CardContent>
          </Card>
          <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader className="pb-2">
              <CardDescription>Closed-loop success</CardDescription>
              <CardTitle className="text-lg">
                {formatPct(data.overallSuccessRate, data.totalInterventions)}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {closedLoopSuccessCopy(
                data.successfulInterventions,
                data.totalInterventions
              )}
            </CardContent>
          </Card>
          <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader className="pb-2">
              <CardDescription>Learning Progress</CardDescription>
              <CardTitle
                className="text-lg"
                title={formatLearningProgressTooltip(data.profile?.learningRateEstimate)}
              >
                {formatLearningProgressLabel(data.profile?.learningRateEstimate)}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {learningProgressDescription(data.profile?.learningRateEstimate)}
            </CardContent>
          </Card>
        </div>
      </div>

      <PreferredModalityByExerciseCard data={data} />
      <RecentInterventionsCard data={data} />

      <div className="grid gap-4 lg:grid-cols-2">
        {modalityData.length > 0 ? (
          <div className="min-h-[300px]">
            <BarChart
              title="Feedback Effectiveness"
              description="Average form improvement for each coaching method. Higher is better."
              data={modalityData}
              index="modality"
              categories={['delta']}
              categoryNames={{ delta: 'Average form improvement' }}
              colors={['var(--primary)']}
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader>
              <CardTitle className="text-base">Feedback Effectiveness</CardTitle>
              <CardDescription>
                No coaching-method comparison yet. This chart fills in after successful
                coaching responses during practice.
              </CardDescription>
            </CardHeader>
          </Card>
        )}

        {errorData.length > 0 ? (
          <div className="min-h-[300px]">
            <BarChart
              title="Most Common Form Errors"
              description="Shows the form mistakes that appeared most often during the student’s practice sessions."
              data={errorData}
              index="error"
              categories={['count']}
              colors={['var(--destructive)']}
              formatTooltipValue={(value) =>
                value === 1
                  ? 'Observed in 1 practice session'
                  : `Observed in ${value} practice sessions`
              }
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader>
              <CardTitle className="text-base">Most Common Form Errors</CardTitle>
              <CardDescription>
                No form mistakes have been recorded for this student yet. This chart fills in
                after practice sessions where coaching addresses form.
              </CardDescription>
            </CardHeader>
          </Card>
        )}

        {timelineData.length > 0 ? (
          <div className="min-h-[300px]">
            <LineChart
              title="Learning Progress Over Time"
              description="APSLE Form Mastery and coaching success over time."
              data={timelineData}
              index="date"
              categories={['masteryPct', 'consistencyPct']}
              categoryNames={TIMELINE_CATEGORY_NAMES}
              colors={['var(--primary)', 'var(--secondary)']}
              yDomain={MASTERY_CHART_Y_DOMAIN}
              showLegend
              valueSuffix="%"
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader>
              <CardTitle className="text-base">Learning Progress Over Time</CardTitle>
              <CardDescription>
                Progress over time will appear after the student completes practice with
                successful coaching responses.
              </CardDescription>
            </CardHeader>
          </Card>
        )}

        {masteryBars.length > 0 ? (
          <div className="min-h-[300px]">
            <BarChart
              title="APSLE Form Mastery by Exercise"
              description="APSLE Form Mastery (not classroom task score). Axis is 0–100%; 0% still lists the exercise."
              data={masteryBars}
              index="exercise"
              categories={['masteryPct']}
              categoryNames={MASTERY_CATEGORY_NAMES}
              colors={['var(--chart-2)']}
              yDomain={MASTERY_CHART_Y_DOMAIN}
              showValueLabels
              valueSuffix="%"
              className="h-full"
            />
          </div>
        ) : (
          <Card className="min-h-[200px] rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
            <CardHeader>
              <CardTitle className="text-base">APSLE Form Mastery by Exercise</CardTitle>
              <CardDescription>
                No APSLE Form Mastery records yet for this student. Classroom task scores
                alone do not create mastery — successful coaching responses do.
              </CardDescription>
            </CardHeader>
          </Card>
        )}
      </div>
    </>
  );
}

function AdaptiveRecommendationsCard({
  mastery,
  difficultyRecommendations,
  difficultyError,
  insightsLoading,
}: {
  mastery?: AdaptiveInsightsDTO['mastery'];
  difficultyRecommendations?: DifficultyRecommendationDTO[];
  difficultyError?: boolean;
  insightsLoading?: boolean;
}) {
  const difficultyByType = useMemo(() => {
    const map = new Map<string, DifficultyRecommendationDTO>();
    for (const rec of difficultyRecommendations || []) {
      map.set(rec.exerciseType, rec);
    }
    return map;
  }, [difficultyRecommendations]);

  const softRecs = useMemo(() => {
    const masteryRows = [...(mastery || [])].sort((a, b) => {
      const ta = a.lastSessionAt ? new Date(a.lastSessionAt).getTime() : 0;
      const tb = b.lastSessionAt ? new Date(b.lastSessionAt).getTime() : 0;
      if (tb !== ta) return tb - ta;
      return (b.masteryLevel || 0) - (a.masteryLevel || 0);
    });

    if (masteryRows.length > 0) {
      return masteryRows.map((item) => {
        const rec = difficultyByType.get(item.exerciseType);
        const insufficient = isInsufficientFormCorrectionData(
          item.sessionsCount,
          item.masteryLevel
        );
        return {
          physicalId: item.physicalId,
          exerciseType: item.exerciseType,
          masteryLevel: item.masteryLevel,
          sessionsCount: item.sessionsCount,
          recommendedDifficulty: insufficient
            ? null
            : (() => {
                const raw =
                  item.recommendedDifficulty || rec?.recommendedDifficulty || null;
                return raw ? normalizeExerciseDifficulty(raw) : null;
              })(),
          recommendedGoalReps: insufficient
            ? null
            : snapGoalRepsToTemplateOptions(
                item.recommendedGoalReps ?? rec?.recommendedGoalReps ?? null
              ),
          recommendationRationale: insufficient
            ? MORE_COACHING_DATA_NEEDED
            : item.recommendationRationale || rec?.rationale || null,
          requiresTeacherApproval:
            item.requiresTeacherApproval ?? rec?.requiresTeacherApproval ?? true,
          topErrors: rec?.topErrors || [],
          lastSessionAt: item.lastSessionAt,
          medianTimeToCorrectionMs: item.medianTimeToCorrectionMs,
        };
      });
    }

    return (difficultyRecommendations || []).map((rec) => ({
      physicalId: `${rec.studentId}-${rec.exerciseType}`,
      exerciseType: rec.exerciseType,
      masteryLevel: rec.masteryLevel,
      sessionsCount: rec.sessionsCount,
      recommendedDifficulty: rec.recommendedDifficulty
        ? normalizeExerciseDifficulty(rec.recommendedDifficulty)
        : null,
      recommendedGoalReps: snapGoalRepsToTemplateOptions(rec.recommendedGoalReps ?? null),
      recommendationRationale: rec.rationale,
      requiresTeacherApproval: rec.requiresTeacherApproval,
      topErrors: rec.topErrors || [],
      lastSessionAt: null as string | null,
      medianTimeToCorrectionMs: null as number | null,
    }));
  }, [mastery, difficultyRecommendations, difficultyByType]);

  if (insightsLoading && softRecs.length === 0) {
    return <Skeleton className="h-40 w-full rounded-2xl" />;
  }

  return (
    <Card className="rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Target className="h-4 w-4" />
          Adaptive recommendations
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
  );
}
