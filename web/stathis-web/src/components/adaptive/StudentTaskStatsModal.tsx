'use client';

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  getStudentTaskScores,
  getStudentTaskAttempts,
  ScoreResponseDTO,
  ScoreAttemptResponseDTO,
} from '@/services/scores/api-score-client';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { AlertCircle, Crosshair, Repeat2, Trophy } from 'lucide-react';
import { teacherStudentViewQueryOptions } from '@/lib/query/teacher-student-freshness';

export type ProgressSnapshotItem = {
  taskId: string;
  taskName: string;
  taskType?: string | null;
  completed?: boolean;
  score?: number | null;
  maxScore?: number | null;
  attempts?: number | null;
  reps?: number | null;
  goalReps?: number | null;
};

type StudentTaskStatsModalProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  studentId: string;
  task: ProgressSnapshotItem | null;
};

function formatAccuracy(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  return `${value.toFixed(1)}%`;
}

function averageAttemptAccuracy(attempts: ScoreAttemptResponseDTO[]): number | null {
  const validAccuracies = attempts
    .map((attempt) => attempt.accuracy)
    .filter((value): value is number => value != null && !Number.isNaN(value));

  if (validAccuracies.length === 0) return null;

  return validAccuracies.reduce((sum, accuracy) => sum + accuracy, 0) / validAccuracies.length;
}

function averageAttemptValue(values: number[]): number | null {
  if (values.length === 0) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function formatDate(value?: string | null): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatDuration(ms?: number | null): string {
  if (ms == null || ms <= 0) return '—';
  const totalSec = Math.round(ms / 1000);
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  if (mins <= 0) return `${secs}s`;
  return `${mins}m ${secs}s`;
}

function pickPrimaryScore(scores: ScoreResponseDTO[]): ScoreResponseDTO | null {
  if (!scores.length) return null;
  return (
    scores.find((s) => s.exerciseTemplateId) ||
    scores.find((s) => s.quizTemplateId) ||
    scores[0]
  );
}

function StatChip({
  label,
  value,
  emphasize,
}: {
  label: string;
  value: string;
  emphasize?: boolean;
}) {
  return (
    <div className="rounded-xl border border-border/40 bg-muted/30 px-3 py-2">
      <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className={`mt-0.5 text-sm font-semibold tabular-nums ${emphasize ? 'text-primary' : ''}`}>
        {value}
      </p>
    </div>
  );
}

function AttemptDetails({ attempt }: { attempt: ScoreAttemptResponseDTO }) {
  const isExercise = !!attempt.exerciseTemplateId;
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <StatChip
        label="Accuracy"
        value={formatAccuracy(attempt.accuracy)}
        emphasize
      />
      <StatChip
        label="Score"
        value={`${attempt.score}/${attempt.maxScore || 100}`}
      />
      {isExercise && (
        <>
          <StatChip
            label="Reps"
            value={
              attempt.reps != null
                ? `${attempt.reps}${attempt.goalReps != null ? ` / ${attempt.goalReps}` : ''}`
                : '—'
            }
          />
          <StatChip
            label="Calories"
            value={
              attempt.caloriesBurned != null
                ? `${attempt.caloriesBurned.toFixed(1)} kcal`
                : '—'
            }
          />
        </>
      )}
      <StatChip label="Duration" value={formatDuration(attempt.timeTaken)} />
      <StatChip label="Completed" value={formatDate(attempt.completedAt || attempt.createdAt)} />
    </div>
  );
}

export function StudentTaskStatsModal({
  open,
  onOpenChange,
  studentId,
  task,
}: StudentTaskStatsModalProps) {
  const taskId = task?.taskId || '';

  const scoresQuery = useQuery({
    queryKey: ['student-task-scores', studentId, taskId],
    queryFn: () => getStudentTaskScores(studentId, taskId),
    enabled: open && !!studentId && !!taskId,
    ...teacherStudentViewQueryOptions,
  });

  const attemptsQuery = useQuery({
    queryKey: ['student-task-attempts', studentId, taskId],
    queryFn: () => getStudentTaskAttempts(studentId, taskId),
    enabled: open && !!studentId && !!taskId,
    ...teacherStudentViewQueryOptions,
  });

  const primary = pickPrimaryScore(scoresQuery.data || []);
  const attempts = attemptsQuery.data || [];
  const isLoading = scoresQuery.isLoading || attemptsQuery.isLoading;
  const isError = scoresQuery.isError || attemptsQuery.isError;

  const averageAccuracy = averageAttemptAccuracy(attempts);
  const latestAccuracy =
    averageAccuracy ??
    primary?.accuracy ??
    (attempts.length > 0 ? attempts[attempts.length - 1]?.accuracy : null) ??
    null;

  const averageScore = averageAttemptValue(attempts.map((attempt) => attempt.score));
  const averageMaxScore = averageAttemptValue(
    attempts.map((attempt) => attempt.maxScore ?? task?.maxScore ?? 100)
  );

  const displayScore =
    averageScore != null && averageMaxScore != null
      ? `${averageScore.toFixed(1)}/${averageMaxScore.toFixed(1)}`
      : primary != null
        ? `${primary.score}/${primary.maxScore || task?.maxScore || 100}`
        : task?.score != null
          ? `${task.score}/${task.maxScore ?? 100}`
          : '—';

  const displayAttempts = primary?.attempts ?? task?.attempts ?? attempts.length ?? 0;
  const displayReps =
    primary?.reps ?? task?.reps ?? null;
  const displayGoalReps = primary?.goalReps ?? task?.goalReps ?? null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="pr-6">{task?.taskName || 'Task statistics'}</DialogTitle>
          <DialogDescription>
            General task stats and per-attempt accuracy for this student.
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-20 w-full rounded-xl" />
            <Skeleton className="h-12 w-full rounded-xl" />
            <Skeleton className="h-12 w-full rounded-xl" />
          </div>
        ) : isError ? (
          <div className="flex items-start gap-2 rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <p>Could not load task statistics. Try again in a moment.</p>
          </div>
        ) : (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">
                {(task?.taskType || 'TASK').replaceAll('_', ' ')}
              </Badge>
              {task?.completed || primary?.isCompleted || primary?.status === 'COMPLETED' || primary?.status === 'GRADED' ? (
                <Badge variant="secondary">Done</Badge>
              ) : (
                <Badge variant="outline">In progress</Badge>
              )}
              {primary?.status && (
                <Badge variant="outline">{primary.status}</Badge>
              )}
            </div>

            <div>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                General statistics
              </h4>
              <div className="grid gap-2 sm:grid-cols-3">
                <StatChip label="Score" value={displayScore} />
                <StatChip
                  label="Accuracy"
                  value={formatAccuracy(latestAccuracy)}
                  emphasize
                />
                <StatChip label="Attempts" value={String(displayAttempts)} />
                {(displayReps != null || displayGoalReps != null) && (
                  <StatChip
                    label="Reps"
                    value={
                      displayReps != null
                        ? `${displayReps}${displayGoalReps != null ? ` / ${displayGoalReps}` : ''}`
                        : '—'
                    }
                  />
                )}
                {primary?.caloriesBurned != null && (
                  <StatChip
                    label="Calories"
                    value={`${primary.caloriesBurned.toFixed(1)} kcal`}
                  />
                )}
                <StatChip
                  label="Last submission"
                  value={formatDate(primary?.submissionDate)}
                />
              </div>
            </div>

            <div>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                Attempts
              </h4>
              {attempts.length === 0 ? (
                <p className="rounded-xl border border-border/40 px-3 py-4 text-sm text-muted-foreground">
                  No attempts recorded for this task yet.
                </p>
              ) : (
                <Accordion type="single" collapsible className="rounded-xl border border-border/40 px-3">
                  {attempts.map((attempt) => (
                    <AccordionItem
                      key={attempt.physicalId}
                      value={attempt.physicalId}
                      className="border-border/40"
                    >
                      <AccordionTrigger className="py-3 text-sm hover:no-underline">
                        <div className="flex flex-1 flex-wrap items-center gap-x-3 gap-y-1 text-left">
                          <span className="font-medium">Attempt {attempt.attemptNumber}</span>
                          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                            <Crosshair className="h-3 w-3" />
                            {formatAccuracy(attempt.accuracy)}
                          </span>
                          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                            <Trophy className="h-3 w-3" />
                            {attempt.score}/{attempt.maxScore || 100}
                          </span>
                          {attempt.reps != null && (
                            <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                              <Repeat2 className="h-3 w-3" />
                              {attempt.reps}
                              {attempt.goalReps != null ? `/${attempt.goalReps}` : ''} reps
                            </span>
                          )}
                        </div>
                      </AccordionTrigger>
                      <AccordionContent className="pb-3">
                        <AttemptDetails attempt={attempt} />
                      </AccordionContent>
                    </AccordionItem>
                  ))}
                </Accordion>
              )}
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
