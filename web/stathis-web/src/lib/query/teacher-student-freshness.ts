/**
 * Targeted React Query options for teacher Student Progress, Form Correction Evidence,
 * mastery/insights, and badges. Mobile graded-complete / evidence upload does not push
 * to these pages (live STOMP is exercise-progress/vitals only), so visible teacher
 * student views poll while the tab is in the foreground.
 */
export const STUDENT_PROGRESS_ITEMS_KEY = 'student-progress-items';
export const FORM_CORRECTION_EVIDENCE_KEY = 'form-correction-evidence';
export const STUDENT_BADGES_KEY = 'student-badges';
export const STUDENT_LEADERBOARD_KEY = 'student-leaderboard';
export const ADAPTIVE_INSIGHTS_KEY = 'adaptive-insights';
export const ADAPTIVE_DIFFICULTY_KEY = 'adaptive-difficulty-recommendations';
export const STUDENT_TASK_SCORES_KEY = 'student-task-scores';
export const STUDENT_TASK_ATTEMPTS_KEY = 'student-task-attempts';
export const CLASSROOM_TASKS_KEY = 'classroom-tasks';
export const TASK_SCORES_KEY = 'task-scores';

/** Visibility-bounded poll while the teacher is on the page. Not a full reload. */
export const TEACHER_STUDENT_VIEW_REFETCH_MS = 5_000;
export const TEACHER_STUDENT_VIEW_STALE_TIME_MS = 0;

/** No STOMP topic exists for graded completion, evidence, or teacher task-start. */
export const HAS_STUDENT_SAVE_PUSH = false;
export const HAS_EVIDENCE_PUSH = false;
export const HAS_TASK_START_PUSH = false;

export function studentProgressQueryKey(
  studentId: string,
  classroomId?: string | null
) {
  return [STUDENT_PROGRESS_ITEMS_KEY, studentId, classroomId] as const;
}

export function formCorrectionEvidenceQueryKey(
  studentId: string,
  classroomId?: string | null
) {
  return [FORM_CORRECTION_EVIDENCE_KEY, studentId, classroomId] as const;
}

export function studentBadgesQueryKey(studentId: string) {
  return [STUDENT_BADGES_KEY, studentId] as const;
}

export function studentLeaderboardQueryKey(studentId: string) {
  return [STUDENT_LEADERBOARD_KEY, studentId] as const;
}

export function adaptiveInsightsQueryKey(studentId: string) {
  return [ADAPTIVE_INSIGHTS_KEY, studentId] as const;
}

export const teacherStudentViewQueryOptions = {
  staleTime: TEACHER_STUDENT_VIEW_STALE_TIME_MS,
  refetchOnWindowFocus: true,
  refetchOnMount: 'always' as const,
  refetchInterval: TEACHER_STUDENT_VIEW_REFETCH_MS,
  refetchIntervalInBackground: false,
};

/** Classroom overview / dashboard: refetch on focus and mount, never poll N task-score queries. */
export const teacherOverviewQueryOptions = {
  staleTime: TEACHER_STUDENT_VIEW_STALE_TIME_MS,
  refetchOnWindowFocus: true,
  refetchOnMount: 'always' as const,
  refetchInterval: false as const,
  refetchIntervalInBackground: false,
};

export function pollIntervalWhileVisible(isVisible: boolean): number | false {
  return isVisible ? TEACHER_STUDENT_VIEW_REFETCH_MS : false;
}

export type QueryInvalidator = {
  invalidateQueries: (filters: { queryKey: readonly unknown[] }) => unknown;
};

/** Call after a successful teacher-side mutation that implies new scores or evidence. */
export function invalidateTeacherStudentViews(
  queryClient: QueryInvalidator,
  studentId: string,
  classroomId?: string | null
) {
  queryClient.invalidateQueries({
    queryKey: studentProgressQueryKey(studentId, classroomId),
  });
  queryClient.invalidateQueries({
    queryKey: formCorrectionEvidenceQueryKey(studentId, classroomId),
  });
  queryClient.invalidateQueries({
    queryKey: studentBadgesQueryKey(studentId),
  });
  queryClient.invalidateQueries({
    queryKey: studentLeaderboardQueryKey(studentId),
  });
  queryClient.invalidateQueries({
    queryKey: adaptiveInsightsQueryKey(studentId),
  });
  queryClient.invalidateQueries({
    queryKey: [ADAPTIVE_DIFFICULTY_KEY, studentId],
  });
  queryClient.invalidateQueries({
    queryKey: [STUDENT_TASK_SCORES_KEY, studentId],
  });
  queryClient.invalidateQueries({
    queryKey: [STUDENT_TASK_ATTEMPTS_KEY, studentId],
  });
}

export function invalidateAfterTeacherTaskStart(
  queryClient: QueryInvalidator,
  classroomId: string
) {
  queryClient.invalidateQueries({
    queryKey: [CLASSROOM_TASKS_KEY, classroomId],
  });
  queryClient.invalidateQueries({
    queryKey: ['tasks', classroomId],
  });
}

export function invalidateAfterStudentScoreMutation(
  queryClient: QueryInvalidator,
  taskId: string,
  studentId?: string | null,
  classroomId?: string | null
) {
  queryClient.invalidateQueries({ queryKey: [TASK_SCORES_KEY, taskId] });
  queryClient.invalidateQueries({ queryKey: [STUDENT_PROGRESS_ITEMS_KEY] });
  queryClient.invalidateQueries({ queryKey: [FORM_CORRECTION_EVIDENCE_KEY] });
  if (studentId) {
    invalidateTeacherStudentViews(queryClient, studentId, classroomId);
  }
}
