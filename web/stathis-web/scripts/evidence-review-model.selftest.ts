import assert from 'node:assert/strict';
import {
  attemptRowLabel,
  deleteRequestAllowed,
  groupAttempts,
  groupEvidenceByTask,
  nextDeletePhase,
  removeEvidenceItem,
  taskRowLabel,
  toggleKey,
  type EvidenceDeleteAction,
  type EvidenceDeletePhase,
  type EvidenceReviewItem,
} from '../src/components/adaptive/evidence-review-model';

function item(
  physicalId: string,
  attemptNumber: number | null,
  exerciseType: string,
  capturedAt: string
): EvidenceReviewItem {
  return {
    physicalId,
    taskId: 'TASK-1',
    classroomId: 'ROOM-1',
    attemptNumber,
    exerciseType,
    errorCode: 'KNEES_IN',
    errorLabel: 'Knees moving inward',
    correctionText: 'Push your knees out over your toes.',
    capturedAt,
  };
}

const squats = [
  item('FCE-1', 1, 'SQUATS', '2026-08-21T01:00:00Z'),
  item('FCE-2', 1, 'SQUATS', '2026-08-21T01:05:00Z'),
  item('FCE-3', 2, 'SQUATS', '2026-08-21T02:00:00Z'),
];

const groups = groupEvidenceByTask(squats, new Map([['TASK-1', 'Squats']]));
assert.equal(groups.length, 1);
assert.equal(taskRowLabel(groups[0]), 'Task 1 — Squats — 3 Snapshots');

let open = toggleKey([], 'TASK-1');
assert.deepEqual(open, ['TASK-1']);
open = toggleKey(open, 'TASK-1');
assert.deepEqual(open, []);

const attempts = groupAttempts(groups[0].items);
assert.equal(attempts.length, 2);
assert.equal(attempts[0].attemptNumber, 1);
assert.equal(attempts[0].items.map((row) => row.physicalId).join(','), 'FCE-1,FCE-2');
assert.equal(attemptRowLabel(attempts[0]), 'Attempt 1 — Squats — 2 Snapshots');
assert.equal(attempts[1].items[0].physicalId, 'FCE-3');

const remaining = removeEvidenceItem(squats, 'FCE-2');
assert.deepEqual(
  remaining.map((row) => row.physicalId),
  ['FCE-1', 'FCE-3']
);
assert.equal(removeEvidenceItem(remaining, 'FCE-1').filter((row) => row.physicalId === 'FCE-3').length, 1);
assert.equal(removeEvidenceItem([item('FCE-9', 1, 'SQUATS', '2026-08-21T01:00:00Z')], 'FCE-9').length, 0);

function walk(actions: EvidenceDeleteAction[]): EvidenceDeletePhase {
  return actions.reduce<EvidenceDeletePhase>((phase, action) => nextDeletePhase(phase, action), 'idle');
}

assert.equal(walk(['open']), 'identify');
assert.equal(walk(['open', 'continue']), 'irreversible');
assert.equal(walk(['open', 'continue', 'confirm']), 'deleting');
assert.equal(walk(['confirm']), 'idle');
assert.equal(walk(['open', 'confirm']), 'identify');
assert.equal(deleteRequestAllowed('identify', 'confirm'), false);
assert.equal(deleteRequestAllowed('irreversible', 'confirm'), true);
assert.equal(deleteRequestAllowed('deleting', 'confirm'), false);
assert.equal(deleteRequestAllowed('irreversible', 'continue'), false);
assert.equal(deleteRequestAllowed('irreversible', 'cancel'), false);

assert.equal(walk(['open', 'cancel']), 'idle');
assert.equal(walk(['open', 'continue', 'cancel']), 'idle');
assert.equal(walk(['open', 'continue', 'confirm', 'cancel']), 'deleting');
assert.equal(walk(['open', 'continue', 'confirm', 'confirm']), 'deleting');
assert.equal(walk(['open', 'continue', 'confirm', 'success']), 'idle');
assert.equal(walk(['open', 'continue', 'confirm', 'failure']), 'irreversible');
assert.equal(walk(['open', 'continue', 'confirm', 'failure', 'cancel']), 'idle');

const mixed = groupEvidenceByTask(
  [
    item('FCE-A', 1, 'SQUATS', '2026-08-21T03:00:00Z'),
    {
      ...item('FCE-B', 1, 'STATIC_LUNGES', '2026-08-21T03:01:00Z'),
      taskId: 'TASK-2',
    },
  ],
  new Map([
    ['TASK-1', 'Squats'],
    ['TASK-2', 'Lunges'],
  ])
);
assert.equal(mixed.length, 2);
assert.equal(removeEvidenceItem(
  mixed.flatMap((group) => group.items),
  'FCE-A'
).some((row) => row.physicalId === 'FCE-B'), true);

console.log('evidence-review-model.selftest: ok');
