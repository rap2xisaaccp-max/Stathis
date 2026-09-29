import assert from 'node:assert/strict';
import {
  assignmentError,
  exerciseStatus,
  moveExercise,
  normalizeExerciseType,
  studentExerciseBoards,
  toExerciseRequests,
  type AssignedExercise,
} from '../src/lib/tasks/task-exercises.ts';

const push: AssignedExercise = {
  physicalId: 'EXERCISE-PUSH',
  title: 'Push-ups',
  exerciseType: 'PUSH_UPS',
  goalReps: 10,
  goalAccuracy: 80,
  goalTime: 60,
};
const squat: AssignedExercise = {
  physicalId: 'EXERCISE-SQUAT',
  title: 'Squats',
  exerciseType: 'SQUATS',
  goalReps: 15,
  goalAccuracy: 80,
  goalTime: 60,
};

assert.equal(normalizeExerciseType('PUSHUP'), 'PUSH_UP');
assert.equal(assignmentError([push], { ...push }), 'That exercise template is already in this task');
assert.equal(
  assignmentError([push], { ...squat, physicalId: 'EXERCISE-PUSH-2', exerciseType: 'PUSH_UP' }),
  'This task already includes that exercise type'
);
assert.equal(assignmentError([push], squat), null);

const moved = moveExercise([push, squat], 1, -1);
assert.equal(moved[0].physicalId, 'EXERCISE-SQUAT');
assert.deepEqual(toExerciseRequests(moved), [
  { exerciseTemplateId: 'EXERCISE-SQUAT', sortOrder: 1 },
  { exerciseTemplateId: 'EXERCISE-PUSH', sortOrder: 2 },
]);

assert.equal(exerciseStatus({}), 'Not Started');
assert.equal(exerciseStatus({ reps: 4 }), 'In Progress');
assert.equal(exerciseStatus({ attempts: 1, reps: 12 }), 'Completed');

const boards = studentExerciseBoards(
  [
    { exerciseTemplateId: 'EXERCISE-PUSH', title: 'Push-ups', sortOrder: 1, goalReps: 10 },
    { exerciseTemplateId: 'EXERCISE-SQUAT', title: 'Squats', sortOrder: 2, goalReps: 15 },
    { exerciseTemplateId: 'EXERCISE-GLUTE', title: 'Glute Bridge', sortOrder: 3, goalReps: 12 },
  ],
  [
    {
      studentId: 'STUDENT-1',
      exerciseTemplateId: 'EXERCISE-PUSH',
      reps: 10,
      attempts: 1,
      isCompleted: true,
      score: 100,
    },
  ]
);
assert.equal(boards.length, 1);
assert.equal(boards[0].completed, 1);
assert.equal(boards[0].required, 3);
assert.equal(boards[0].lines[0].status, 'Completed');
assert.equal(boards[0].lines[1].status, 'Not Started');
assert.equal(boards[0].lines[1].reps, 0);
assert.equal(boards[0].lines[2].title, 'Glute Bridge');
const scores = boards[0].lines.map((line) => line.score);
assert.deepEqual(scores, [100, null, null]);

console.log('task-exercises.selftest: ok');
