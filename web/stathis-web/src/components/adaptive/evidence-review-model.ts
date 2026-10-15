/**
 * Pure Classroom → Task → Attempt → Evidence review state.
 * The teacher dashboard and the node selftest both use these functions.
 */

export type EvidenceReviewItem = {
  physicalId: string;
  taskId?: string | null;
  classroomId?: string | null;
  attemptNumber?: number | null;
  exerciseType?: string | null;
  errorCode?: string | null;
  errorLabel?: string | null;
  errorDescription?: string | null;
  correctionText?: string | null;
  capturedAt?: string | null;
  createdAt?: string | null;
};

export type TaskEvidenceGroup<T extends EvidenceReviewItem = EvidenceReviewItem> = {
  taskKey: string;
  taskId: string | null;
  taskName: string;
  taskIndex: number;
  classroomId: string | null;
  items: T[];
};

export type AttemptEvidenceGroup<T extends EvidenceReviewItem = EvidenceReviewItem> = {
  attemptKey: string;
  attemptNumber: number | null;
  items: T[];
};

export type EvidenceDeletePhase = 'idle' | 'identify' | 'irreversible' | 'deleting';

export type EvidenceDeleteAction =
  | 'open'
  | 'continue'
  | 'confirm'
  | 'cancel'
  | 'success'
  | 'failure';

export function parseCapturedDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatDate(iso: string | null | undefined): string {
  const d = parseCapturedDate(iso);
  if (!d) return '—';
  return d.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

export function formatTime(iso: string | null | undefined): string {
  const d = parseCapturedDate(iso);
  if (!d) return '—';
  return d.toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function formatExercise(value: string | null | undefined): string {
  if (!value) return 'Exercise';
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

export function shortId(value: string): string {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value;
}

export function evidenceTime(item: EvidenceReviewItem): number {
  return parseCapturedDate(item.capturedAt || item.createdAt)?.getTime() ?? 0;
}

export function snapshotCountLabel(count: number): string {
  return `${count} ${count === 1 ? 'Snapshot' : 'Snapshots'}`;
}

export function uniqueExerciseNames(items: readonly EvidenceReviewItem[]): string[] {
  const names: string[] = [];
  for (const item of items) {
    const name = formatExercise(item.exerciseType);
    if (!names.includes(name)) names.push(name);
  }
  return names;
}

export function taskRowLabel(group: {
  taskIndex: number;
  taskName: string;
  items: readonly EvidenceReviewItem[];
}): string {
  const exercises = uniqueExerciseNames(group.items);
  const exercisePart =
    exercises.length === 0
      ? null
      : exercises.length === 1
        ? exercises[0]
        : `${exercises.length} exercises`;
  const name =
    exercisePart && exercisePart.toLowerCase() !== group.taskName.trim().toLowerCase()
      ? `${group.taskName} — ${exercisePart}`
      : group.taskName;
  return `Task ${group.taskIndex} — ${name} — ${snapshotCountLabel(group.items.length)}`;
}

export function attemptRowLabel(group: AttemptEvidenceGroup): string {
  const exercises = uniqueExerciseNames(group.items);
  const attempt =
    group.attemptNumber == null ? 'Attempt' : `Attempt ${group.attemptNumber}`;
  const exercisePart = exercises.length === 1 ? exercises[0] : null;
  const head = exercisePart ? `${attempt} — ${exercisePart}` : attempt;
  return `${head} — ${snapshotCountLabel(group.items.length)}`;
}

export function evidenceTimestamp(item: EvidenceReviewItem): string {
  const iso = item.capturedAt || item.createdAt;
  return `${formatDate(iso)} ${formatTime(iso)}`.trim();
}

export function groupEvidenceByTask<T extends EvidenceReviewItem>(
  items: readonly T[],
  taskMetaById: ReadonlyMap<string, string>
): TaskEvidenceGroup<T>[] {
  const buckets = new Map<string, T[]>();

  for (const item of items) {
    const key = item.taskId?.trim() ? item.taskId.trim() : '__practice__';
    const list = buckets.get(key) ?? [];
    list.push(item);
    buckets.set(key, list);
  }

  const groups: TaskEvidenceGroup<T>[] = [];
  for (const [taskKey, groupItems] of buckets) {
    const sorted = [...groupItems].sort((a, b) => {
      const attemptA = a.attemptNumber ?? Number.MAX_SAFE_INTEGER;
      const attemptB = b.attemptNumber ?? Number.MAX_SAFE_INTEGER;
      if (attemptA !== attemptB) return attemptA - attemptB;
      return evidenceTime(a) - evidenceTime(b);
    });
    const taskId = taskKey === '__practice__' ? null : taskKey;
    const named = taskId ? taskMetaById.get(taskId) : null;
    const classroomId = sorted.find((row) => row.classroomId)?.classroomId?.trim() || null;

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

export function groupAttempts<T extends EvidenceReviewItem>(
  items: readonly T[]
): AttemptEvidenceGroup<T>[] {
  const buckets = new Map<string, T[]>();
  for (const item of items) {
    const key = item.attemptNumber == null ? 'unnumbered' : String(item.attemptNumber);
    const list = buckets.get(key) ?? [];
    list.push(item);
    buckets.set(key, list);
  }

  return [...buckets.entries()]
    .sort((a, b) => {
      if (a[0] === 'unnumbered') return 1;
      if (b[0] === 'unnumbered') return -1;
      return Number(a[0]) - Number(b[0]);
    })
    .map(([attemptKey, groupItems]) => ({
      attemptKey,
      attemptNumber: attemptKey === 'unnumbered' ? null : Number(attemptKey),
      items: [...groupItems].sort((a, b) => evidenceTime(a) - evidenceTime(b)),
    }));
}

export function toggleKey(open: readonly string[], key: string): string[] {
  return open.includes(key) ? open.filter((item) => item !== key) : [...open, key];
}

export function nextDeletePhase(
  phase: EvidenceDeletePhase,
  action: EvidenceDeleteAction
): EvidenceDeletePhase {
  if (
    phase === 'deleting' &&
    (action === 'open' || action === 'continue' || action === 'confirm' || action === 'cancel')
  ) {
    return 'deleting';
  }
  switch (action) {
    case 'open':
      return phase === 'idle' ? 'identify' : phase;
    case 'continue':
      return phase === 'identify' ? 'irreversible' : phase;
    case 'confirm':
      return phase === 'irreversible' ? 'deleting' : phase;
    case 'cancel':
      return phase === 'deleting' ? 'deleting' : 'idle';
    case 'success':
      return phase === 'deleting' ? 'idle' : phase;
    case 'failure':
      return phase === 'deleting' ? 'irreversible' : phase;
    default:
      return phase;
  }
}

/** The network delete is allowed only on the second confirmation, and only once. */
export function deleteRequestAllowed(
  phase: EvidenceDeletePhase,
  action: EvidenceDeleteAction
): boolean {
  return action === 'confirm' && phase === 'irreversible' && nextDeletePhase(phase, action) === 'deleting';
}

export function removeEvidenceItem<T extends EvidenceReviewItem>(
  items: readonly T[],
  physicalId: string
): T[] {
  return items.filter((item) => item.physicalId !== physicalId);
}
