export type AssignedExercise = {
  physicalId: string;
  title: string;
  exerciseType: string;
  goalReps: number;
  goalAccuracy: number;
  goalTime: number;
};

export type ExerciseRequest = {
  exerciseTemplateId: string;
  sortOrder: number;
};

export type ExerciseScoreRow = {
  studentId: string;
  exerciseTemplateId?: string;
  reps?: number;
  goalReps?: number;
  attempts?: number;
  isCompleted?: boolean;
  score?: number;
  maxScore?: number;
};

export type StudentExerciseLine = {
  exerciseTemplateId: string;
  title: string;
  reps: number;
  goalReps: number | null;
  status: 'Not Started' | 'In Progress' | 'Completed';
  score: number | null;
};

export type StudentExerciseBoard = {
  studentId: string;
  lines: StudentExerciseLine[];
  completed: number;
  required: number;
};

export function normalizeExerciseType(raw: string | null | undefined): string {
  const value = (raw ?? '').trim().toUpperCase().replaceAll('-', '_').replaceAll(' ', '_');
  switch (value) {
    case 'PUSH_UP':
    case 'PUSH_UPS':
    case 'PUSHUP':
    case 'PUSHUPS':
      return 'PUSH_UP';
    case 'SQUAT':
    case 'SQUATS':
      return 'SQUATS';
    case 'GLUTE_BRIDGE':
    case 'GLUTE_BRIDGES':
      return 'GLUTE_BRIDGE';
    case 'STATIC_LUNGE':
    case 'STATIC_LUNGES':
    case 'LUNGE':
    case 'LUNGES':
      return 'STATIC_LUNGES';
    case 'LYING_LEG_RAISE':
    case 'LYING_LEG_RAISES':
    case 'LEG_RAISE':
    case 'LEG_RAISES':
      return 'LYING_LEG_RAISES';
    default:
      return value || 'UNKNOWN';
  }
}

export function assignmentError(
  existing: AssignedExercise[],
  candidate: AssignedExercise
): string | null {
  if (existing.some((item) => item.physicalId === candidate.physicalId)) {
    return 'That exercise template is already in this task';
  }
  const type = normalizeExerciseType(candidate.exerciseType);
  if (existing.some((item) => normalizeExerciseType(item.exerciseType) === type)) {
    return 'This task already includes that exercise type';
  }
  return null;
}

export function moveExercise<T>(items: T[], index: number, direction: -1 | 1): T[] {
  const next = index + direction;
  if (index < 0 || next < 0 || index >= items.length || next >= items.length) {
    return items;
  }
  const copy = items.slice();
  const [item] = copy.splice(index, 1);
  copy.splice(next, 0, item);
  return copy;
}

export function toExerciseRequests(items: AssignedExercise[]): ExerciseRequest[] {
  return items.map((item, index) => ({
    exerciseTemplateId: item.physicalId.toUpperCase(),
    sortOrder: index + 1,
  }));
}

export function exerciseStatus(input: {
  attempts?: number;
  isCompleted?: boolean;
  reps?: number;
}): 'Not Started' | 'In Progress' | 'Completed' {
  const attempts = input.attempts ?? 0;
  if (input.isCompleted || attempts > 0) {
    return 'Completed';
  }
  if ((input.reps ?? 0) > 0) {
    return 'In Progress';
  }
  return 'Not Started';
}

export function studentExerciseBoards(
  slots: Array<{
    exerciseTemplateId: string;
    title?: string;
    sortOrder?: number;
    goalReps?: number;
  }>,
  scores: ExerciseScoreRow[]
): StudentExerciseBoard[] {
  const ordered = [...slots].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  const studentIds: string[] = [];
  for (const score of scores) {
    if (score.exerciseTemplateId && !studentIds.includes(score.studentId)) {
      studentIds.push(score.studentId);
    }
  }
  return studentIds.map((studentId) => {
    const lines = ordered.map((slot) => {
      const score = scores.find(
        (row) => row.studentId === studentId && row.exerciseTemplateId === slot.exerciseTemplateId
      );
      const status = exerciseStatus({
        attempts: score?.attempts,
        isCompleted: score?.isCompleted,
        reps: score?.reps,
      });
      return {
        exerciseTemplateId: slot.exerciseTemplateId,
        title: slot.title || slot.exerciseTemplateId,
        reps: score?.reps ?? 0,
        goalReps: slot.goalReps ?? score?.goalReps ?? null,
        status,
        score: score ? score.score ?? 0 : null,
      };
    });
    return {
      studentId,
      lines,
      completed: lines.filter((line) => line.status === 'Completed').length,
      required: ordered.length,
    };
  });
}

export function formatGoalTime(seconds: number): string {
  if (!seconds) return '—';
  if (seconds % 60 === 0 && seconds >= 60) {
    const minutes = seconds / 60;
    return `${minutes} minute${minutes === 1 ? '' : 's'}`;
  }
  return `${seconds} seconds`;
}
