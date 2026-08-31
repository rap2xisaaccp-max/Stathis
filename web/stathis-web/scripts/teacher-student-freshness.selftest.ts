import assert from 'node:assert/strict';
import {
  ADAPTIVE_DIFFICULTY_KEY,
  ADAPTIVE_INSIGHTS_KEY,
  CLASSROOM_TASKS_KEY,
  FORM_CORRECTION_EVIDENCE_KEY,
  HAS_EVIDENCE_PUSH,
  HAS_STUDENT_SAVE_PUSH,
  HAS_TASK_START_PUSH,
  STUDENT_BADGES_KEY,
  STUDENT_LEADERBOARD_KEY,
  STUDENT_PROGRESS_ITEMS_KEY,
  STUDENT_TASK_ATTEMPTS_KEY,
  STUDENT_TASK_SCORES_KEY,
  TASK_SCORES_KEY,
  TEACHER_STUDENT_VIEW_REFETCH_MS,
  TEACHER_STUDENT_VIEW_STALE_TIME_MS,
  formCorrectionEvidenceQueryKey,
  invalidateAfterStudentScoreMutation,
  invalidateAfterTeacherTaskStart,
  invalidateTeacherStudentViews,
  pollIntervalWhileVisible,
  studentProgressQueryKey,
  teacherOverviewQueryOptions,
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

assert.equal(HAS_STUDENT_SAVE_PUSH, false);
assert.equal(HAS_EVIDENCE_PUSH, false);
assert.equal(HAS_TASK_START_PUSH, false);
assert.equal(pollIntervalWhileVisible(true), TEACHER_STUDENT_VIEW_REFETCH_MS);
assert.equal(pollIntervalWhileVisible(false), false);
assert.equal(teacherOverviewQueryOptions.refetchInterval, false);
assert.equal(teacherOverviewQueryOptions.refetchIntervalInBackground, false);

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

assert.ok(invalidated.length >= 6);
assert.deepEqual(invalidated[0], [STUDENT_PROGRESS_ITEMS_KEY, 'STU-1', 'ROOM-1']);
assert.deepEqual(invalidated[1], [FORM_CORRECTION_EVIDENCE_KEY, 'STU-1', 'ROOM-1']);
assert.deepEqual(invalidated[2], [STUDENT_BADGES_KEY, 'STU-1']);
assert.deepEqual(invalidated[3], [STUDENT_LEADERBOARD_KEY, 'STU-1']);
assert.deepEqual(invalidated[4], [ADAPTIVE_INSIGHTS_KEY, 'STU-1']);
assert.deepEqual(invalidated[5], [ADAPTIVE_DIFFICULTY_KEY, 'STU-1']);
assert.ok(
  !JSON.stringify(teacherStudentViewQueryOptions).includes('websocket'),
  'Student Progress freshness must not depend on live-rep WebSockets'
);

const afterStart: unknown[][] = [];
invalidateAfterTeacherTaskStart(
  {
    invalidateQueries: (filters) => {
      afterStart.push([...filters.queryKey]);
    },
  },
  'ROOM-1'
);
assert.deepEqual(afterStart[0], [CLASSROOM_TASKS_KEY, 'ROOM-1']);
assert.deepEqual(afterStart[1], ['tasks', 'ROOM-1']);

const afterSave: unknown[][] = [];
invalidateAfterStudentScoreMutation(
  {
    invalidateQueries: (filters) => {
      afterSave.push([...filters.queryKey]);
    },
  },
  'TASK-1',
  'STU-1',
  'ROOM-1'
);
assert.ok(afterSave.some((key) => key[0] === TASK_SCORES_KEY && key[1] === 'TASK-1'));
assert.ok(afterSave.some((key) => key[0] === STUDENT_PROGRESS_ITEMS_KEY));
assert.ok(afterSave.some((key) => key[0] === FORM_CORRECTION_EVIDENCE_KEY));
assert.ok(afterSave.some((key) => key[0] === STUDENT_TASK_SCORES_KEY));
assert.ok(afterSave.some((key) => key[0] === STUDENT_TASK_ATTEMPTS_KEY));

const overviewJson = JSON.stringify(teacherOverviewQueryOptions);
assert.ok(!overviewJson.includes('5000') && teacherOverviewQueryOptions.refetchInterval === false);
assert.equal(teacherStudentViewQueryOptions.refetchIntervalInBackground, false);

console.log('teacher-student-freshness.selftest: ok');
