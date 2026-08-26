'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchFormCorrectionEvidence,
  FormCorrectionEvidenceDTO,
} from '@/services/adaptive/api-adaptive-client';
import { API_BASE_URL } from '@/lib/api/server-client';
import { getClassroomById } from '@/services/api-classroom';
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
  classroomId: string | null;
  items: FormCorrectionEvidenceDTO[];
};

function formatWhen(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatExercise(value: string | null | undefined): string {
  if (!value) return 'Exercise';
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function shortTaskId(taskId: string): string {
  return taskId.length > 12 ? `${taskId.slice(0, 8)}…` : taskId;
}

function evidenceTime(item: FormCorrectionEvidenceDTO): number {
  const raw = item.capturedAt || item.createdAt;
  if (!raw) return 0;
  const t = new Date(raw).getTime();
  return Number.isNaN(t) ? 0 : t;
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
      taskName: taskId
        ? named || `Task ${shortTaskId(taskId)}`
        : 'Practice session',
      classroomId,
      items: sorted,
    });
  }

  return groups.sort((a, b) => {
    const latestA = Math.max(...a.items.map(evidenceTime), 0);
    const latestB = Math.max(...b.items.map(evidenceTime), 0);
    return latestB - latestA;
  });
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
  onClick,
  priority = false,
}: {
  evidence: FormCorrectionEvidenceDTO;
  className?: string;
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
          className
        )}
      >
        <Camera className="mr-2 h-4 w-4" />
        Snapshot unavailable
      </div>
    );
  }

  if (loading || !src) {
    return <Skeleton className={cn('rounded-xl', className)} />;
  }

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'group relative block w-full overflow-hidden rounded-xl bg-muted text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        onClick ? 'cursor-zoom-in' : 'cursor-default',
        className
      )}
      aria-label={onClick ? `Enlarge snapshot: ${alt}` : alt}
    >
      <img
        src={src}
        alt={alt}
        loading={priority ? 'eager' : 'lazy'}
        className="h-full w-full object-cover transition duration-200 group-hover:scale-[1.01]"
      />
      {onClick ? (
        <span className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/55 to-transparent px-3 py-2 text-xs text-white opacity-0 transition group-hover:opacity-100">
          Click to enlarge
        </span>
      ) : null}
    </button>
  );
}

function EvidenceDetails({ evidence }: { evidence: FormCorrectionEvidenceDTO }) {
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <h4 className="text-base font-semibold">{formatExercise(evidence.exerciseType)}</h4>
        <Badge variant="secondary">
          {evidence.errorLabel || formErrorLabel(evidence.errorCode)}
        </Badge>
        {evidence.attemptNumber != null ? (
          <Badge variant="outline">Attempt {evidence.attemptNumber}</Badge>
        ) : null}
      </div>
      <p className="text-sm text-muted-foreground">
        {evidence.errorDescription || 'Incorrect form was confirmed during the attempt.'}
      </p>
      <p className="text-sm">
        <span className="font-medium">Correction delivered: </span>
        {evidence.correctionText || '—'}
      </p>
      <p className="text-xs text-muted-foreground">
        {formatWhen(evidence.capturedAt || evidence.createdAt)}
      </p>
    </div>
  );
}

function TaskEvidenceCarousel({
  group,
  classroomLabel,
  onOpenLightbox,
}: {
  group: TaskEvidenceGroup;
  classroomLabel: string;
  onOpenLightbox: (index: number) => void;
}) {
  const [index, setIndex] = useState(0);
  const total = group.items.length;
  const safeIndex = Math.min(index, Math.max(total - 1, 0));
  const current = group.items[safeIndex];

  useEffect(() => {
    setIndex(0);
  }, [group.taskKey]);

  if (!current) return null;

  const goPrev = () => setIndex((value) => (value - 1 + total) % total);
  const goNext = () => setIndex((value) => (value + 1) % total);

  return (
    <Card className="overflow-hidden border-border/50">
      <CardHeader className="space-y-2 border-b border-border/40 bg-muted/20 pb-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {classroomLabel}
            </p>
            <CardTitle className="text-lg leading-snug">{group.taskName}</CardTitle>
            <CardDescription>
              {total} snapshot{total === 1 ? '' : 's'}
              {(() => {
                const latest = [...group.items].sort(
                  (a, b) => evidenceTime(b) - evidenceTime(a)
                )[0];
                return latest
                  ? ` · Latest ${formatWhen(latest.capturedAt || latest.createdAt)}`
                  : '';
              })()}
            </CardDescription>
          </div>
          <Badge variant="outline" className="shrink-0">
            Snapshot {safeIndex + 1} of {total}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="grid gap-4 p-4 md:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="h-9 w-9 shrink-0"
              onClick={goPrev}
              disabled={total <= 1}
              aria-label="Previous snapshot"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <p className="text-sm text-muted-foreground">
              Snapshot {safeIndex + 1} of {total}
            </p>
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="h-9 w-9 shrink-0"
              onClick={goNext}
              disabled={total <= 1}
              aria-label="Next snapshot"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
          <EvidenceImage
            evidence={current}
            className="aspect-[4/3] max-h-72"
            onClick={() => onOpenLightbox(safeIndex)}
            priority
          />
        </div>
        <EvidenceDetails evidence={current} />
      </CardContent>
    </Card>
  );
}

function EvidenceLightbox({
  open,
  group,
  index,
  classroomLabel,
  onClose,
  onIndexChange,
}: {
  open: boolean;
  group: TaskEvidenceGroup | null;
  index: number;
  classroomLabel: string;
  onClose: () => void;
  onIndexChange: (index: number) => void;
}) {
  const total = group?.items.length ?? 0;
  const evidence = group?.items[index] ?? null;
  const { src, failed, loading } = useEvidenceImage(evidence);

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
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, group, index, total, onClose, onIndexChange]);

  if (!open || !group || !evidence) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Form correction snapshot"
    >
      <button
        type="button"
        className="absolute inset-0 bg-black/80 backdrop-blur-[2px]"
        aria-label="Close enlarged snapshot"
        onClick={onClose}
      />
      <div className="relative z-10 flex max-h-[92vh] w-full max-w-5xl flex-col gap-3">
        <div className="flex items-start justify-between gap-3 text-white">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-white/70">{classroomLabel}</p>
            <h3 className="truncate text-lg font-semibold">{group.taskName}</h3>
            <p className="text-sm text-white/75">
              Snapshot {index + 1} of {total} · {formatWhen(evidence.capturedAt || evidence.createdAt)}
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

        <div className="relative flex min-h-0 flex-1 items-center justify-center">
          {total > 1 ? (
            <Button
              type="button"
              variant="secondary"
              size="icon"
              className="absolute left-0 z-10 h-10 w-10 rounded-full bg-black/45 text-white hover:bg-black/60 md:-left-2"
              onClick={() => onIndexChange((index - 1 + total) % total)}
              aria-label="Previous snapshot"
            >
              <ChevronLeft className="h-5 w-5" />
            </Button>
          ) : null}

          <div className="mx-auto flex max-h-[70vh] w-full items-center justify-center overflow-hidden rounded-2xl bg-black/30">
            {failed ? (
              <div className="flex h-64 w-full items-center justify-center text-white/80">
                <Camera className="mr-2 h-4 w-4" />
                Snapshot unavailable
              </div>
            ) : loading || !src ? (
              <Skeleton className="h-64 w-full max-w-3xl rounded-2xl" />
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
              className="absolute right-0 z-10 h-10 w-10 rounded-full bg-black/45 text-white hover:bg-black/60 md:-right-2"
              onClick={() => onIndexChange((index + 1) % total)}
              aria-label="Next snapshot"
            >
              <ChevronRight className="h-5 w-5" />
            </Button>
          ) : null}
        </div>

        <div className="rounded-2xl border border-white/10 bg-black/45 p-4 text-white backdrop-blur-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{formatExercise(evidence.exerciseType)}</span>
            <Badge variant="secondary" className="bg-white/15 text-white hover:bg-white/20">
              {evidence.errorLabel || formErrorLabel(evidence.errorCode)}
            </Badge>
            {evidence.attemptNumber != null ? (
              <Badge variant="outline" className="border-white/30 text-white">
                Attempt {evidence.attemptNumber}
              </Badge>
            ) : null}
          </div>
          <p className="mt-2 text-sm text-white/80">
            {evidence.errorDescription || 'Incorrect form was confirmed during the attempt.'}
          </p>
          <p className="mt-1 text-sm text-white/90">
            <span className="font-medium text-white">Correction delivered: </span>
            {evidence.correctionText || '—'}
          </p>
        </div>
      </div>
    </div>
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

  const taskMetaById = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of progressItems ?? []) {
      if (item.taskId && item.taskName) {
        map.set(item.taskId, item.taskName);
      }
    }
    return map;
  }, [progressItems]);

  const groups = useMemo(
    () => groupEvidenceByTask(query.data ?? [], taskMetaById),
    [query.data, taskMetaById]
  );

  const classroomLabel =
    classroomQuery.data ||
    (classroomId ? `Classroom ${shortTaskId(classroomId)}` : 'Classroom');

  const [lightbox, setLightbox] = useState<{
    taskKey: string;
    index: number;
  } | null>(null);

  const lightboxGroup = lightbox
    ? groups.find((group) => group.taskKey === lightbox.taskKey) ?? null
    : null;

  if (query.isLoading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 2 }).map((_, i) => (
          <Skeleton key={i} className="h-64 w-full rounded-2xl" />
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
      <div className="space-y-5">
        {groups.map((group) => (
          <TaskEvidenceCarousel
            key={group.taskKey}
            group={group}
            classroomLabel={
              group.classroomId && classroomId && group.classroomId !== classroomId
                ? `Classroom ${shortTaskId(group.classroomId)}`
                : classroomLabel
            }
            onOpenLightbox={(index) => setLightbox({ taskKey: group.taskKey, index })}
          />
        ))}
      </div>

      <EvidenceLightbox
        open={!!lightboxGroup}
        group={lightboxGroup}
        index={lightbox?.index ?? 0}
        classroomLabel={
          lightboxGroup?.classroomId &&
          classroomId &&
          lightboxGroup.classroomId !== classroomId
            ? `Classroom ${shortTaskId(lightboxGroup.classroomId)}`
            : classroomLabel
        }
        onClose={() => setLightbox(null)}
        onIndexChange={(index) =>
          setLightbox((current) => (current ? { ...current, index } : current))
        }
      />
    </>
  );
}
