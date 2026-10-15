'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  deleteFormCorrectionEvidence,
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
import { Button, buttonVariants } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  AlertCircle,
  Camera,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  RefreshCw,
  Trash2,
  X,
} from 'lucide-react';
import { formErrorLabel } from '@/components/adaptive/form-error-labels';
import {
  attemptRowLabel,
  deleteRequestAllowed,
  evidenceTimestamp,
  formatDate,
  formatExercise,
  formatTime,
  groupAttempts,
  groupEvidenceByTask,
  nextDeletePhase,
  removeEvidenceItem,
  shortId,
  snapshotCountLabel,
  taskRowLabel,
  toggleKey,
  type EvidenceDeletePhase,
} from '@/components/adaptive/evidence-review-model';
import { cn } from '@/lib/utils';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import {
  formCorrectionEvidenceQueryKey,
  teacherStudentViewQueryOptions,
} from '@/lib/query/teacher-student-freshness';

export type EvidenceTaskMeta = {
  taskId: string;
  taskName: string;
};

type DeleteSelection = {
  item: FormCorrectionEvidenceDTO;
  phase: Exclude<EvidenceDeletePhase, 'idle'>;
};

function evidenceError(item: FormCorrectionEvidenceDTO): string {
  return (item.errorLabel || formErrorLabel(item.errorCode) || 'Form error').trim();
}

function evidenceCaption(item: FormCorrectionEvidenceDTO, index: number): string {
  const attempt =
    item.attemptNumber != null ? `Attempt ${item.attemptNumber}` : `Snapshot ${index + 1}`;
  const time = formatTime(item.capturedAt || item.createdAt);
  return [formatExercise(item.exerciseType), attempt, evidenceError(item), time]
    .filter((part) => part && part !== '—')
    .join(' · ');
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

function EvidenceLightbox({
  open,
  items,
  index,
  title,
  classroomName,
  onClose,
  onIndexChange,
}: {
  open: boolean;
  items: FormCorrectionEvidenceDTO[];
  index: number;
  title: string;
  classroomName: string;
  onClose: () => void;
  onIndexChange: (index: number) => void;
}) {
  const [mounted, setMounted] = useState(false);
  const total = items.length;
  const evidence = items[index] ?? null;
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
      if (total <= 1) return;
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
  }, [open, index, total, onClose, onIndexChange]);

  if (!mounted || !open || !evidence) return null;

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
            <h3 className="truncate text-lg font-semibold">{title}</h3>
            <p className="text-sm text-white/75">
              {evidenceCaption(evidence, index)} of {total}
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

        <div className="relative z-10 flex min-h-0 flex-1 items-center justify-center gap-2">
          {total > 1 ? (
            <Button
              type="button"
              variant="secondary"
              size="icon"
              className="h-10 w-10 shrink-0 rounded-full bg-black/45 text-white hover:bg-black/60"
              onClick={() => onIndexChange((index - 1 + total) % total)}
              aria-label="Previous snapshot"
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
              aria-label="Next snapshot"
            >
              <ChevronRight className="h-5 w-5" />
            </Button>
          ) : null}
        </div>

        <div className="relative z-10 mt-3 rounded-2xl border border-white/10 bg-black/50 p-4 text-white">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{formatExercise(evidence.exerciseType)}</span>
            <Badge variant="secondary" className="bg-white/15 text-white hover:bg-white/20">
              {evidenceError(evidence)}
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

function ExpandableRow({
  label,
  open,
  controlsId,
  onToggle,
  children,
  nested = false,
}: {
  label: string;
  open: boolean;
  controlsId: string;
  onToggle: () => void;
  children: React.ReactNode;
  nested?: boolean;
}) {
  const reduceMotion = useReducedMotion();
  return (
    <div className={cn(nested && 'border-t border-border/50')}>
      <button
        type="button"
        className={cn(
          'flex h-12 w-full items-center gap-3 px-3 text-left text-sm font-medium text-foreground transition-colors hover:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset',
          nested && 'h-11 bg-muted/15 pl-6 text-[13px] font-medium'
        )}
        aria-expanded={open}
        aria-controls={controlsId}
        onClick={onToggle}
      >
        <span className="min-w-0 flex-1 truncate">{label}</span>
        <ChevronDown
          className={cn(
            'h-4 w-4 shrink-0 text-muted-foreground',
            !reduceMotion && 'transition-transform duration-200',
            open && 'rotate-180'
          )}
          aria-hidden
        />
      </button>
      <div
        id={controlsId}
        className={cn(
          'grid',
          reduceMotion ? '' : 'transition-[grid-template-rows] duration-200 ease-out',
          open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'
        )}
      >
        <div className="overflow-hidden" inert={open ? undefined : true}>
          {children}
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
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: formCorrectionEvidenceQueryKey(studentId, classroomId),
    queryFn: () => fetchFormCorrectionEvidence(studentId, classroomId),
    enabled: !!studentId && !!classroomId?.trim(),
    retry: 1,
    ...teacherStudentViewQueryOptions,
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

  const [openTasks, setOpenTasks] = useState<string[]>([]);
  const [openAttempts, setOpenAttempts] = useState<string[]>([]);
  const [lightbox, setLightbox] = useState<{
    taskKey: string;
    attemptKey: string;
    index: number;
  } | null>(null);
  const [deleteSelection, setDeleteSelection] = useState<DeleteSelection | null>(null);
  const [notice, setNotice] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);
  const deleteSelectionRef = useRef<DeleteSelection | null>(null);
  const deleteInFlight = useRef(false);
  const advancingDelete = useRef(false);
  deleteSelectionRef.current = deleteSelection;

  useEffect(() => {
    if (deleteSelection?.phase === 'irreversible' || deleteSelection?.phase === 'deleting') {
      advancingDelete.current = false;
    }
  }, [deleteSelection?.phase]);

  const lightboxGroup = lightbox
    ? groups.find((group) => group.taskKey === lightbox.taskKey) ?? null
    : null;
  const lightboxAttempt = lightboxGroup
    ? groupAttempts(lightboxGroup.items).find((attempt) => attempt.attemptKey === lightbox?.attemptKey) ??
      null
    : null;

  function openDelete(item: FormCorrectionEvidenceDTO) {
    if (deleteInFlight.current || deleteSelectionRef.current?.phase === 'deleting') return;
    if (deleteSelectionRef.current) return;
    setDeleteSelection({ item, phase: 'identify' });
  }

  function cancelDelete() {
    const current = deleteSelectionRef.current;
    if (!current || current.phase === 'deleting') return;
    const phase = nextDeletePhase(current.phase, 'cancel');
    setDeleteSelection(phase === 'idle' ? null : { ...current, phase });
  }

  function continueDelete() {
    const current = deleteSelectionRef.current;
    if (!current) return;
    advancingDelete.current = true;
    const phase = nextDeletePhase(current.phase, 'continue');
    const next = phase === 'idle' ? null : { ...current, phase };
    deleteSelectionRef.current = next;
    setDeleteSelection(next);
  }

  async function confirmDelete() {
    const current = deleteSelectionRef.current;
    if (!current || deleteInFlight.current) return;
    if (!deleteRequestAllowed(current.phase, 'confirm')) return;
    deleteInFlight.current = true;
    const phase = nextDeletePhase(current.phase, 'confirm');
    const next = phase === 'idle' ? null : { ...current, phase };
    deleteSelectionRef.current = next;
    setDeleteSelection(next);
    const selected = current.item;
    try {
      await deleteFormCorrectionEvidence(selected.physicalId);
      queryClient.setQueryData<FormCorrectionEvidenceDTO[]>(
        formCorrectionEvidenceQueryKey(studentId, classroomId),
        (existing) => (existing ? removeEvidenceItem(existing, selected.physicalId) : existing)
      );
      void queryClient
        .invalidateQueries({
          queryKey: formCorrectionEvidenceQueryKey(studentId, classroomId),
        })
        .catch(() => undefined);
      setLightbox((open) => {
        if (!open || !lightboxAttempt) return open;
        const viewed = lightboxAttempt.items[open.index];
        return viewed?.physicalId === selected.physicalId ? null : open;
      });
      deleteSelectionRef.current = null;
      setDeleteSelection(null);
      const message = 'Evidence log and snapshot deleted.';
      setNotice({ kind: 'success', text: message });
      toast.success(message);
    } catch (error) {
      const message =
        error instanceof Error && error.message
          ? error.message
          : 'Could not delete the evidence log.';
      const failed = nextDeletePhase('deleting', 'failure');
      const failedSelection = failed === 'idle' ? null : { ...current, phase: failed };
      deleteSelectionRef.current = failedSelection;
      setDeleteSelection(failedSelection);
      setNotice({ kind: 'error', text: message });
      toast.error(message);
    } finally {
      deleteInFlight.current = false;
    }
  }

  const selected = deleteSelection?.item ?? null;
  const selectedExercise = selected ? formatExercise(selected.exerciseType) : '';
  const selectedError = selected ? evidenceError(selected) : '';
  const selectedWhen = selected ? evidenceTimestamp(selected) : '';

  if (!classroomId?.trim()) {
    return (
      <div className="rounded-2xl border border-dashed border-border/70 py-12 text-center text-muted-foreground">
        A classroom is required before evidence can be reviewed.
      </div>
    );
  }

  return (
    <>
      <div className="mb-4 rounded-xl border border-border/50 bg-muted/20 px-3 py-2.5">
        <p className="text-xs text-muted-foreground">Classroom</p>
        <p className="text-sm font-semibold">{classroomName}</p>
        <p className="mt-0.5 text-[11px] text-muted-foreground">
          Classroom → Task → Attempt → Snapshot. Tasks stay collapsed until you expand them.
        </p>
      </div>

      {notice ? (
        <p
          role="status"
          className={cn(
            'mb-3 rounded-xl border px-3 py-2 text-sm',
            notice.kind === 'error'
              ? 'border-destructive/40 bg-destructive/5 text-destructive'
              : 'border-border/60 bg-muted/20 text-foreground'
          )}
        >
          {notice.text}
        </p>
      ) : null}

      {query.isLoading ? (
        <div className="overflow-hidden rounded-xl border border-border/60">
          {Array.from({ length: 3 }).map((_, index) => (
            <Skeleton key={index} className="h-12 w-full rounded-none" />
          ))}
        </div>
      ) : query.isError ? (
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
      ) : groups.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border/70 py-12 text-center text-muted-foreground">
          No form-correction evidence yet
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-border/60 bg-card">
          {groups.map((group, groupIndex) => {
            const taskOpen = openTasks.includes(group.taskKey);
            const attempts = groupAttempts(group.items);
            return (
              <div
                key={group.taskKey}
                className={cn(groupIndex > 0 && 'border-t border-border/60')}
              >
                <ExpandableRow
                  label={taskRowLabel(group)}
                  open={taskOpen}
                  controlsId={`evidence-task-${group.taskIndex}`}
                  onToggle={() => setOpenTasks((current) => toggleKey(current, group.taskKey))}
                >
                  {attempts.map((attempt) => {
                    const attemptId = `${group.taskKey}::${attempt.attemptKey}`;
                    const attemptOpen = openAttempts.includes(attemptId);
                    return (
                      <ExpandableRow
                        key={attemptId}
                        nested
                        label={attemptRowLabel(attempt)}
                        open={attemptOpen}
                        controlsId={`evidence-attempt-${group.taskIndex}-${attempt.attemptKey}`}
                        onToggle={() =>
                          setOpenAttempts((current) => toggleKey(current, attemptId))
                        }
                      >
                        <ul className="space-y-2 border-t border-border/50 bg-muted/10 px-3 py-3 sm:px-4">
                          {attempt.items.map((item, itemIndex) => {
                            const capturedAt = item.capturedAt || item.createdAt;
                            const deletingThis =
                              deleteSelection?.phase === 'deleting' &&
                              deleteSelection.item.physicalId === item.physicalId;
                            return (
                              <li
                                key={item.physicalId}
                                className="rounded-xl border border-border/60 bg-background p-3"
                              >
                                <div className="flex flex-col gap-3 sm:flex-row">
                                  <div className="w-full sm:w-28 sm:shrink-0">
                                    {attemptOpen ? (
                                      <EvidenceImage
                                        evidence={item}
                                        frameClassName="aspect-[3/4] max-h-40 sm:max-h-32"
                                        onClick={() =>
                                          setLightbox({
                                            taskKey: group.taskKey,
                                            attemptKey: attempt.attemptKey,
                                            index: itemIndex,
                                          })
                                        }
                                        priority={group.taskIndex === 1 && itemIndex === 0}
                                      />
                                    ) : null}
                                  </div>
                                  <div className="min-w-0 flex-1 space-y-2">
                                    <div className="flex flex-wrap items-center gap-2">
                                      <span className="text-sm font-medium">
                                        {formatExercise(item.exerciseType)}
                                      </span>
                                      <Badge variant="secondary" className="text-[10px]">
                                        {evidenceError(item)}
                                      </Badge>
                                    </div>
                                    <p className="text-xs text-muted-foreground">
                                      <span className="font-medium text-foreground">Detected: </span>
                                      {item.errorDescription ||
                                        'Incorrect form was confirmed during the attempt.'}
                                    </p>
                                    <p className="text-xs text-muted-foreground">
                                      <span className="font-medium text-foreground">
                                        Correction:{' '}
                                      </span>
                                      {item.correctionText || '—'}
                                    </p>
                                    <p className="text-xs text-muted-foreground">
                                      {formatDate(capturedAt)} · {formatTime(capturedAt)}
                                    </p>
                                    <Button
                                      type="button"
                                      variant="outline"
                                      size="sm"
                                      className="border-destructive/40 text-destructive hover:bg-destructive/10 hover:text-destructive"
                                      disabled={deleteSelection?.phase === 'deleting'}
                                      aria-label={`Delete evidence for ${formatExercise(item.exerciseType)}, ${evidenceError(item)}, ${evidenceTimestamp(item)}`}
                                      onClick={() => openDelete(item)}
                                    >
                                      <Trash2 className="h-3.5 w-3.5" />
                                      {deletingThis ? 'Deleting…' : 'Delete'}
                                    </Button>
                                  </div>
                                </div>
                              </li>
                            );
                          })}
                        </ul>
                      </ExpandableRow>
                    );
                  })}
                  {attempts.length === 0 ? (
                    <p className="px-3 py-3 text-sm text-muted-foreground">
                      No attempts in this task.
                    </p>
                  ) : null}
                </ExpandableRow>
              </div>
            );
          })}
        </div>
      )}

      <AlertDialog
        open={deleteSelection?.phase === 'identify'}
        onOpenChange={(open) => {
          if (open) return;
          if (advancingDelete.current) {
            advancingDelete.current = false;
            return;
          }
          cancelDelete();
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this evidence log?</AlertDialogTitle>
            <AlertDialogDescription>
              {selectedExercise}. Error: {selectedError}. Recorded {selectedWhen}. Continue only if
              this is the snapshot you mean to review for deletion.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <button
              type="button"
              className={cn(buttonVariants())}
              onClick={continueDelete}
            >
              Continue
            </button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={
          deleteSelection?.phase === 'irreversible' || deleteSelection?.phase === 'deleting'
        }
        onOpenChange={(open) => {
          if (open) return;
          cancelDelete();
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>This cannot be recovered</AlertDialogTitle>
            <AlertDialogDescription>
              The evidence log and its associated snapshot will be deleted and cannot be recovered
              through the UI. {selectedExercise}. Error: {selectedError}. Recorded {selectedWhen}.
              Scores, form mastery, repetition counts, and task completion stay on their original
              records.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteSelection?.phase === 'deleting'}>
              Cancel
            </AlertDialogCancel>
            <button
              type="button"
              className={cn(buttonVariants({ variant: 'destructive' }))}
              disabled={deleteSelection?.phase === 'deleting'}
              onClick={() => void confirmDelete()}
            >
              {deleteSelection?.phase === 'deleting' ? 'Deleting…' : 'Delete evidence'}
            </button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <EvidenceLightbox
        open={!!lightboxAttempt && !!lightbox}
        items={lightboxAttempt?.items ?? []}
        index={lightbox?.index ?? 0}
        title={
          lightboxGroup
            ? `${taskRowLabel(lightboxGroup)} · ${lightboxAttempt ? attemptRowLabel(lightboxAttempt) : snapshotCountLabel(0)}`
            : classroomName
        }
        classroomName={classroomName}
        onClose={() => setLightbox(null)}
        onIndexChange={(index) =>
          setLightbox((current) => (current ? { ...current, index } : current))
        }
      />
    </>
  );
}
