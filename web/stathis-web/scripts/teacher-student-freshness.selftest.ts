import assert from 'node:assert/strict';
import {
  FORM_CORRECTION_EVIDENCE_KEY,
  STUDENT_PROGRESS_ITEMS_KEY,
  TEACHER_STUDENT_VIEW_REFETCH_MS,
  TEACHER_STUDENT_VIEW_STALE_TIME_MS,
  formCorrectionEvidenceQueryKey,
  invalidateTeacherStudentViews,
  studentProgressQueryKey,
  teacherStudentViewQueryOptions,
} from '../src/lib/query/teacher-student-freshness';

assert.equal(TEACHER_STUDENT_VIEW_STALE_TIME_MS, 0);
assert.equal(teacherStudentViewQueryOptions.staleTime, 0);
assert.equal(teacherStudentViewQueryOptions.refetchOnWindowFocus, true);
assert.equal(teacherStudentViewQueryOptions.refetchOnMount, 'always');
assert.equal(teacherStudentViewQueryOptions.refetchIntervalInBackground, false);
assert.ok(
  TEACHER_STUDENT_VIEW_REFETCH_MS > 0 && TEACHER_STUDENT_VIEW_REFETCH_MS <= 10_000,
  'poll interval must be bounded and faster than the previous 30s stale window'
);
assert.equal(teacherStudentViewQueryOptions.refetchInterval, TEACHER_STUDENT_VIEW_REFETCH_MS);

assert.deepEqual(studentProgressQueryKey('STU-1', 'ROOM-1'), [
  STUDENT_PROGRESS_ITEMS_KEY,
  'STU-1',
  'ROOM-1',
]);
assert.deepEqual(formCorrectionEvidenceQueryKey('STU-1', 'ROOM-1'), [
  FORM_CORRECTION_EVIDENCE_KEY,
  'STU-1',
  'ROOM-1',
]);

const invalidated: unknown[][] = [];
invalidateTeacherStudentViews(
  {
    invalidateQueries: (filters) => {
      invalidated.push([...filters.queryKey]);
    },
  },
  'STU-1',
  'ROOM-1'
);

assert.equal(invalidated.length, 2);
assert.deepEqual(invalidated[0], [STUDENT_PROGRESS_ITEMS_KEY, 'STU-1', 'ROOM-1']);
assert.deepEqual(invalidated[1], [FORM_CORRECTION_EVIDENCE_KEY, 'STU-1', 'ROOM-1']);
assert.ok(
  !JSON.stringify(teacherStudentViewQueryOptions).includes('websocket'),
  'Student Progress freshness must not depend on live-rep WebSockets'
);

console.log('teacher-student-freshness.selftest: ok');
