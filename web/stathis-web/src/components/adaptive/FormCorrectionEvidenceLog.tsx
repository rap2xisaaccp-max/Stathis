'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery } from '@tanstack/react-query';
import {
  fetchFormCorrectionEvidence,
  FormCorrectionEvidenceDTO,
} from '@/services/adaptive/api-adaptive-client';
import { API_BASE_URL } from '@/lib/api/server-client';
import { getClassroomById } from '@/services/api-classroom';
import { getClassroomTasks } from '@/services/tasks/api-task-client';
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
import {
  AlertCircle,
  Camera,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  X,
} from 'lucide-react';
import { formErrorLabel } from '@/components/adaptive/form-error-labels';
import { cn } from '@/lib/utils';

export type EvidenceTaskMeta = {
  taskId: string;
  taskName: string;
};

type TaskEvidenceGroup = {
  taskKey: string;
  taskId: string | null;
  taskName: string;
  taskIndex: number;
  classroomId: string | null;
  items: FormCorrectionEvidenceDTO[];
};

function parseCapturedDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

function formatDate(iso: string | null | undefined): string {
  const d = parseCapturedDate(iso);
  if (!d) return '—';
  return d.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatTime(iso: string | null | undefined): string {
  const d = parseCapturedDate(iso);
  if (!d) return '—';
  return d.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatExercise(value: string | null | undefined): string {
  if (!value) return 'Exercise';
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function shortId(value: string): string {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value;
}

function evidenceTime(item: FormCorrectionEvidenceDTO): number {
  return parseCapturedDate(item.capturedAt || item.createdAt)?.getTime() ?? 0;
}

function attemptLabel(item: FormCorrectionEvidenceDTO, fallbackIndex: number): string {
  return item.attemptNumber != null ? `Attempt ${item.attemptNumber}` : `Attempt ${fallbackIndex}`;
}

function groupEvidenceByTask(
  items: FormCorrectionEvidenceDTO[],
  taskMetaById: Map<string, string>
): TaskEvidenceGroup[] {
  const buckets = new Map<string, FormCorrectionEvidenceDTO[]>();

  for (const item of items) {
    const key = item.taskId?.trim() ? item.taskId.trim() : '__practice__';
    const list = buckets.get(key) ?? [];
    list.push(item);
    buckets.set(key, list);
  }

  const groups: TaskEvidenceGroup[] = [];
  for (const [taskKey, groupItems] of buckets) {
    const sorted = [...groupItems].sort((a, b) => {
      const attemptA = a.attemptNumber ?? Number.MAX_SAFE_INTEGER;
      const attemptB = b.attemptNumber ?? Number.MAX_SAFE_INTEGER;
      if (attemptA !== attemptB) return attemptA - attemptB;
      return evidenceTime(a) - evidenceTime(b);
    });
    const taskId = taskKey === '__practice__' ? null : taskKey;
    const named = taskId ? taskMetaById.get(taskId) : null;
    const classroomId =
      sorted.find((row) => row.classroomId)?.classroomId?.trim() || null;

    groups.push({
      taskKey,
      taskId,
      taskName: taskId ? named || `Task ${shortId(taskId)}` : 'Practice session',
      taskIndex: 0,
      classroomId,
      items: sorted,
    });
  }

  return groups
    .sort((a, b) => {
      const latestA = Math.max(...a.items.map(evidenceTime), 0);
      const latestB = Math.max(...b.items.map(evidenceTime), 0);
      return latestB - latestA;
    })
    .map((group, index) => ({ ...group, taskIndex: index + 1 }));
}

function useEvidenceImage(evidence: FormCorrectionEvidenceDTO | null) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    if (!evidence) {
      setSrc(null);
      setFailed(false);
      setLoading(false);
      return;
    }

    async function load() {
      setLoading(true);
      setFailed(false);
      setSrc(null);
      try {
        const token =
          typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null;
        const path =
          evidence!.imageUrl ||
          `/adaptive/evidence/${encodeURIComponent(evidence!.physicalId)}/image`;
        const url = `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
        const response = await fetch(url, {
          headers: token
            ? { Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}` }
            : {},
        });
        if (!response.ok) {
          throw new Error(`image ${response.status}`);
        }
        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) setSrc(objectUrl);
      } catch {
        if (!cancelled) setFailed(true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [evidence?.imageUrl, evidence?.physicalId]);

  return { src, failed, loading };
}

function EvidenceImage({
  evidence,
  className,
  frameClassName,
  onClick,
  priority = false,
}: {
  evidence: FormCorrectionEvidenceDTO;
  className?: string;
  frameClassName?: string;
  onClick?: () => void;
  priority?: boolean;
}) {
  const { src, failed, loading } = useEvidenceImage(evidence);
  const alt = `${formatExercise(evidence.exerciseType)} form correction`;

  if (failed) {
    return (
      <div
        className={cn(
          'flex items-center justify-center rounded-xl bg-muted text-muted-foreground',
          frameClassName,
          className
        )}
      >
        <Camera className="mr-2 h-4 w-4" />
        Unavailable
      </div>
    );
  }

  if (loading || !src) {
    return <Skeleton className={cn('rounded-xl', frameClassName, className)} />;
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'group relative flex w-full items-center justify-center overflow-hidden rounded-xl bg-muted/70 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        onClick ? 'cursor-zoom-in' : 'cursor-default',
        frameClassName,
        className
      )}
      aria-label={onClick ? `Enlarge snapshot: ${alt}` : alt}
    >
      <img
        src={src}
        alt={alt}
        loading={priority ? 'eager' : 'lazy'}
        className="max-h-full max-w-full object-contain"
      />
      {onClick ? (
        <span className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/50 to-transparent px-2 py-1.5 text-[10px] text-white opacity-0 transition group-hover:opacity-100">
          Click to enlarge
        </span>
      ) : null}
    </button>
  );
}

function TaskEvidenceCard({
  group,
  onOpenLightbox,
}: {
  group: TaskEvidenceGroup;
  onOpenLightbox: (index: number) => void;
}) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const total = group.items.length;
  const safeIndex = Math.min(selectedIndex, Math.max(total - 1, 0));
  const current = group.items[safeIndex];
  const capturedAt = current?.capturedAt || current?.createdAt;

  useEffect(() => {
    setSelectedIndex(0);
  }, [group.taskKey]);

  if (!current) return null;

  return (
    <Card className="flex h-full flex-col overflow-hidden border-border/60 shadow-sm">
      <CardHeader className="space-y-1 border-b border-border/40 bg-muted/20 px-3 py-3">
        <CardTitle className="line-clamp-2 text-sm font-semibold leading-snug">
          Task {group.taskIndex} – {group.taskName}
        </CardTitle>
        <CardDescription className="text-xs">
          {total} attempt{total === 1 ? '' : 's'} with evidence
        </CardDescription>
      </CardHeader>

      <CardContent className="flex flex-1 flex-col gap-3 p-3">
        <div className="flex flex-wrap gap-1.5">
          {group.items.map((item, index) => {
            const active = index === safeIndex;
            return (
              <button
                key={item.physicalId}
                type="button"
                onClick={() => setSelectedIndex(index)}
                className={cn(
                  'rounded-md border px-2 py-1 text-[11px] font-medium transition',
                  active
                    ? 'border-primary/40 bg-primary/10 text-primary'
                    : 'border-border/60 bg-background text-muted-foreground hover:bg-muted/50'
                )}
              >
                {attemptLabel(item, index + 1)}
              </button>
            );
          })}
        </div>

        <EvidenceImage
          evidence={current}
          frameClassName="aspect-[3/4] max-h-56"
          onClick={() => onOpenLightbox(safeIndex)}
          priority={group.taskIndex <= 3}
        />

        <div className="mt-auto space-y-1.5 text-xs">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="font-medium">{formatExercise(current.exerciseType)}</span>
            <Badge variant="secondary" className="text-[10px]">
              {current.errorLabel || formErrorLabel(current.errorCode)}
            </Badge>
          </div>
          <p className="line-clamp-2 text-muted-foreground">
            {current.correctionText ||
              current.errorDescription ||
              'Form correction recorded for this attempt.'}
          </p>
          <div className="rounded-lg border border-border/50 bg-muted/25 px-2.5 py-2">
            <p>
              <span className="font-medium">Date:</span> {formatDate(capturedAt)}
            </p>
            <p>
              <span className="font-medium">Time:</span> {formatTime(capturedAt)}
            </p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function EvidenceLightbox({
  open,
  group,
  index,
  classroomName,
  onClose,
  onIndexChange,
}: {
  open: boolean;
  group: TaskEvidenceGroup | null;
  index: number;
  classroomName: string;
  onClose: () => void;
  onIndexChange: (index: number) => void;
}) {
  const [mounted, setMounted] = useState(false);
  const total = group?.items.length ?? 0;
  const evidence = group?.items[index] ?? null;
  const { src, failed, loading } = useEvidenceImage(evidence);
  const capturedAt = evidence?.capturedAt || evidence?.createdAt;

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (!group || total <= 1) return;
      if (event.key === 'ArrowLeft') {
        event.preventDefault();
        onIndexChange((index - 1 + total) % total);
      }
      if (event.key === 'ArrowRight') {
        event.preventDefault();
        onIndexChange((index + 1) % total);
      }
    };

    const previousOverflow = document.body.style.overflow;
    const previousPaddingRight = document.body.style.paddingRight;
    const scrollbarGap = window.innerWidth - document.documentElement.clientWidth;
    document.body.style.overflow = 'hidden';
    if (scrollbarGap > 0) {
      document.body.style.paddingRight = `${scrollbarGap}px`;
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      document.body.style.paddingRight = previousPaddingRight;
    };
  }, [open, group, index, total, onClose, onIndexChange]);

  if (!mounted || !open || !group || !evidence) return null;

  return createPortal(
    <div
      className="fixed inset-y-0 right-0 z-[45] flex w-full flex-col bg-black/75 backdrop-blur-[1px] md:left-64"
      role="dialog"
      aria-modal="true"
      aria-label="Form correction snapshot"
    >
      <button
        type="button"
        className="absolute inset-0 cursor-default"
        aria-label="Close enlarged snapshot"
        onClick={onClose}
      />

      <div className="relative z-10 flex h-full min-h-0 flex-col p-4 md:p-6">
        <div className="mb-3 flex items-start justify-between gap-3 text-white">
          <div className="min-w-0 space-y-1">
            <p className="text-xs text-white/70">
              <span className="font-medium text-white/90">Classroom:</span> {classroomName}
            </p>
            <h3 className="truncate text-lg font-semibold">
              Task {group.taskIndex} – {group.taskName}
            </h3>
            <p className="text-sm text-white/75">
              {attemptLabel(evidence, index + 1)} of {total}
            </p>
          </div>
          <Button
            type="button"
            variant="secondary"
            size="icon"
            className="h-9 w-9 shrink-0 bg-white/15 text-white hover:bg-white/25"
            onClick={onClose}
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>

        {total > 1 ? (
          <div className="relative z-10 mb-3 flex flex-wrap gap-1.5">
            {group.items.map((item, itemIndex) => {
              const active = itemIndex === index;
              return (
                <button
                  key={item.physicalId}
                  type="button"
                  onClick={() => onIndexChange(itemIndex)}
                  className={cn(
                    'rounded-md border px-2.5 py-1 text-xs font-medium transition',
                    active
                      ? 'border-white/50 bg-white/20 text-white'
                      : 'border-white/20 bg-black/30 text-white/75 hover:bg-white/10'
                  )}
                >
                  {attemptLabel(item, itemIndex + 1)}
                </button>
              );
            })}
          </div>
        ) : null}

        <div className="relative z-10 flex min-h-0 flex-1 items-center justify-center gap-2">
          {total > 1 ? (
            <Button
              type="button"
              variant="secondary"
              size="icon"
              className="h-10 w-10 shrink-0 rounded-full bg-black/45 text-white hover:bg-black/60"
              onClick={() => onIndexChange((index - 1 + total) % total)}
              aria-label="Previous attempt"
            >
              <ChevronLeft className="h-5 w-5" />
            </Button>
          ) : null}

          <div className="flex h-full max-h-full w-full max-w-md items-center justify-center overflow-hidden rounded-2xl bg-black/25 p-2">
            {failed ? (
              <div className="flex h-64 w-full items-center justify-center text-white/80">
                <Camera className="mr-2 h-4 w-4" />
                Snapshot unavailable
              </div>
            ) : loading || !src ? (
              <Skeleton className="h-[70vh] w-full max-w-sm rounded-2xl" />
            ) : (
              <img
                src={src}
                alt={`${formatExercise(evidence.exerciseType)} form correction`}
                className="max-h-[70vh] w-auto max-w-full object-contain"
              />
            )}
          </div>

          {total > 1 ? (
            <Button
              type="button"
              variant="secondary"
              size="icon"
              className="h-10 w-10 shrink-0 rounded-full bg-black/45 text-white hover:bg-black/60"
              onClick={() => onIndexChange((index + 1) % total)}
              aria-label="Next attempt"
            >
              <ChevronRight className="h-5 w-5" />
            </Button>
          ) : null}
        </div>

        <div className="relative z-10 mt-3 rounded-2xl border border-white/10 bg-black/50 p-4 text-white">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{formatExercise(evidence.exerciseType)}</span>
            <Badge variant="secondary" className="bg-white/15 text-white hover:bg-white/20">
              {evidence.errorLabel || formErrorLabel(evidence.errorCode)}
            </Badge>
          </div>
          <p className="mt-2 text-sm text-white/80">
            {evidence.errorDescription || 'Incorrect form was confirmed during the attempt.'}
          </p>
          <p className="mt-1 text-sm text-white/90">
            <span className="font-medium text-white">Correction delivered: </span>
            {evidence.correctionText || '—'}
          </p>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm text-white/80">
            <span>
              <span className="font-medium text-white">Date:</span> {formatDate(capturedAt)}
            </span>
            <span>
              <span className="font-medium text-white">Time:</span> {formatTime(capturedAt)}
            </span>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}

export function FormCorrectionEvidenceLog({
  studentId,
  classroomId,
  progressItems,
}: {
  studentId: string;
  classroomId?: string;
  progressItems?: EvidenceTaskMeta[];
}) {
  const query = useQuery({
    queryKey: ['form-correction-evidence', studentId, classroomId],
    queryFn: () => fetchFormCorrectionEvidence(studentId, classroomId),
    enabled: !!studentId,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });

  const classroomQuery = useQuery({
    queryKey: ['classroom-name-for-evidence', classroomId],
    queryFn: async () => {
      if (!classroomId) return null;
      const classroom = await getClassroomById(classroomId);
      return classroom?.name ?? null;
    },
    enabled: !!classroomId,
    staleTime: 1000 * 60 * 5,
  });

  const classroomTasksQuery = useQuery({
    queryKey: ['classroom-tasks-for-evidence', classroomId],
    queryFn: async () => {
      if (!classroomId) return [];
      return getClassroomTasks(classroomId);
    },
    enabled: !!classroomId,
    staleTime: 1000 * 60 * 5,
    retry: 1,
  });

  const taskMetaById = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of progressItems ?? []) {
      if (item.taskId && item.taskName) {
        map.set(item.taskId, item.taskName);
      }
    }
    for (const task of classroomTasksQuery.data ?? []) {
      const id = task.physicalId;
      const name = task.name?.trim();
      if (id && name && !map.has(id)) {
        map.set(id, name);
      }
    }
    return map;
  }, [progressItems, classroomTasksQuery.data]);

  const groups = useMemo(
    () => groupEvidenceByTask(query.data ?? [], taskMetaById),
    [query.data, taskMetaById]
  );

  const classroomName =
    classroomQuery.data ||
    (classroomId ? `Classroom ${shortId(classroomId)}` : 'Classroom');

  const [lightbox, setLightbox] = useState<{
    taskKey: string;
    index: number;
  } | null>(null);

  const lightboxGroup = lightbox
    ? groups.find((group) => group.taskKey === lightbox.taskKey) ?? null
    : null;

  if (query.isLoading) {
    return (
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-80 w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (query.isError) {
    return (
      <Card className="border-destructive/30">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <AlertCircle className="h-4 w-4" />
            Could not load form-correction evidence
          </CardTitle>
          <CardDescription>This is an API failure, not an empty log.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" size="sm" variant="outline" onClick={() => query.refetch()}>
            <RefreshCw className="mr-1 h-3.5 w-3.5" />
            Retry
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (groups.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border/70 py-12 text-center text-muted-foreground">
        No form-correction evidence yet
      </div>
    );
  }

  return (
    <>
      <div className="mb-4 rounded-xl border border-border/50 bg-muted/20 px-3 py-2.5">
        <p className="text-xs text-muted-foreground">Classroom</p>
        <p className="text-sm font-semibold">{classroomName}</p>
        <p className="mt-0.5 text-[11px] text-muted-foreground">
          Classroom → Task → Attempt → Snapshot. Up to 3 tasks per row.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {groups.map((group) => (
          <TaskEvidenceCard
            key={group.taskKey}
            group={group}
            onOpenLightbox={(index) => setLightbox({ taskKey: group.taskKey, index })}
          />
        ))}
      </div>

      <EvidenceLightbox
        open={!!lightboxGroup}
        group={lightboxGroup}
        index={lightbox?.index ?? 0}
        classroomName={classroomName}
        onClose={() => setLightbox(null)}
        onIndexChange={(index) =>
          setLightbox((current) => (current ? { ...current, index } : current))
        }
      />
    </>
  );
}
