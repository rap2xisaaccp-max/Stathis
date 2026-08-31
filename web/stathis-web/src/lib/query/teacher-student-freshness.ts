/**
 * Targeted React Query options for teacher Student Progress and Form Correction Evidence Log.
 * Mobile graded-complete / evidence upload does not push to these pages, so they must not
 * sit on a 30s staleTime until the teacher refocuses the window.
 */
export const STUDENT_PROGRESS_ITEMS_KEY = 'student-progress-items';
export const FORM_CORRECTION_EVIDENCE_KEY = 'form-correction-evidence';

/** Visibility-bounded poll while the teacher is on the page. Not a full reload. */
export const TEACHER_STUDENT_VIEW_REFETCH_MS = 5_000;
export const TEACHER_STUDENT_VIEW_STALE_TIME_MS = 0;

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

export const teacherStudentViewQueryOptions = {
  staleTime: TEACHER_STUDENT_VIEW_STALE_TIME_MS,
  refetchOnWindowFocus: true,
  refetchOnMount: 'always' as const,
  refetchInterval: TEACHER_STUDENT_VIEW_REFETCH_MS,
  refetchIntervalInBackground: false,
};

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
}
