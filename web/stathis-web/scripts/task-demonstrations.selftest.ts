import assert from 'node:assert/strict';
import {
  acceptDemonstrationFile,
  canUploadDemonstration,
  DEMONSTRATION_MAX_BYTES,
  demonstrationFor,
  demonstrationPath,
  demonstrationRequestError,
  removeDemonstration,
  upsertDemonstration,
  type DemonstrationRecord,
} from '../src/lib/tasks/task-demonstrations';

assert.equal(canUploadDemonstration(null), false);
assert.equal(canUploadDemonstration(''), false);
assert.equal(canUploadDemonstration('TASK-A'), true);

const push: DemonstrationRecord = {
  exerciseTemplateId: 'EXERCISE-PUSH',
  originalFilename: 'push.mp4',
  previewUrl: 'blob:push',
  uploading: false,
  error: null,
};
const added = upsertDemonstration([], push);
assert.equal(demonstrationFor(added, 'EXERCISE-PUSH')?.originalFilename, 'push.mp4');
assert.equal(demonstrationFor(added, 'EXERCISE-SQUAT'), null);

const replaced = upsertDemonstration(added, { ...push, originalFilename: 'push-new.mp4', previewUrl: 'blob:new' });
assert.equal(replaced.length, 1);
assert.equal(replaced[0].originalFilename, 'push-new.mp4');
assert.equal(replaced[0].previewUrl, 'blob:new');

const withSquat = upsertDemonstration(replaced, {
  exerciseTemplateId: 'EXERCISE-SQUAT',
  originalFilename: 'squat.webm',
  previewUrl: 'blob:squat',
  uploading: false,
  error: null,
});
assert.equal(withSquat.length, 2);
assert.equal(demonstrationFor(withSquat, 'EXERCISE-PUSH')?.originalFilename, 'push-new.mp4');
assert.equal(demonstrationFor(withSquat, 'EXERCISE-SQUAT')?.originalFilename, 'squat.webm');

const removed = removeDemonstration(withSquat, 'EXERCISE-SQUAT');
assert.equal(removed.length, 1);
assert.equal(demonstrationFor(removed, 'EXERCISE-SQUAT'), null);
assert.equal(demonstrationFor(removed, 'EXERCISE-PUSH')?.originalFilename, 'push-new.mp4');

assert.equal(DEMONSTRATION_MAX_BYTES, 50 * 1024 * 1024);
assert.equal(acceptDemonstrationFile({ type: 'video/mp4', size: 20, name: 'a.mp4' }), null);
assert.equal(acceptDemonstrationFile({ type: 'video/webm', size: 20, name: 'a.webm' }), null);
assert.equal(acceptDemonstrationFile({ type: 'video/mp4', size: DEMONSTRATION_MAX_BYTES, name: 'a.mp4' }), null);
assert.equal(
  acceptDemonstrationFile({ type: 'video/mp4', size: DEMONSTRATION_MAX_BYTES + 1, name: 'a.mp4' }),
  'Demonstration videos must be 50 MB or smaller'
);
assert.equal(acceptDemonstrationFile({ type: '', size: 0, name: 'a.mp4' }), 'Choose a video file');
assert.equal(demonstrationRequestError(403, 'upload'), 'You are not allowed to change this demonstration');
assert.equal(
  demonstrationRequestError(413, 'upload'),
  'The server rejected this video because it is larger than the current upload limit'
);
assert.equal(demonstrationRequestError(0, 'upload'), 'Network error. Try again.');
assert.equal(demonstrationRequestError(500, 'remove'), 'Could not remove demonstration');
assert.equal(
  acceptDemonstrationFile({ type: 'application/x-msdownload', size: 20, name: 'a.exe' }),
  'Use an MP4 or WebM video'
);
assert.equal(
  demonstrationPath('TASK-A', 'EXERCISE-PUSH'),
  '/tasks/TASK-A/exercises/EXERCISE-PUSH/demonstration'
);
assert.notEqual(
  demonstrationPath('TASK-A', 'EXERCISE-PUSH'),
  demonstrationPath('TASK-A', 'EXERCISE-SQUAT')
);

console.log('task-demonstrations.selftest: ok');
