import assert from 'node:assert/strict';
import {
  closedLoopSuccessCopy,
  formErrorDisplay,
  formErrorLabel,
  formatLearningTrend,
  isInsufficientFormCorrectionData,
  preferredModalityCopy,
} from '../src/components/adaptive/form-error-labels';

assert.equal(formErrorLabel('SAG'), 'Hips sagging');
assert.equal(formErrorLabel('KNEES_IN'), 'Knees moving inward');
assert.equal(formErrorDisplay('LOW_ROM'), 'Incomplete movement (LOW_ROM)');
assert.equal(formatLearningTrend(0.24), '+0.24 severity');
assert.equal(formatLearningTrend(-0.1), '-0.10 severity');
assert.equal(
  closedLoopSuccessCopy(2, 5),
  '2 of 5 measured coaching interventions improved the student’s form.'
);

const learned = preferredModalityCopy({
  modality: 'VISUAL_HIGHLIGHT',
  source: 'LEARNED',
  n: 5,
});
assert.equal(learned.title, 'Preferred: VISUAL HIGHLIGHT');
assert.equal(learned.detail, 'Based on 5 measured coaching responses');

const exploring = preferredModalityCopy({
  modality: 'VERBAL_TEXT',
  source: 'EXPLORING',
  n: 2,
});
assert.match(exploring.title, /Still learning/);

assert.equal(isInsufficientFormCorrectionData(3, 0), true);
assert.equal(isInsufficientFormCorrectionData(3, 0.04), true);
assert.equal(isInsufficientFormCorrectionData(0, 0), false);
assert.equal(isInsufficientFormCorrectionData(2, 0.05), false);

console.log('form-error-labels.selftest: ok');
