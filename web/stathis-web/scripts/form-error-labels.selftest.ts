import assert from 'node:assert/strict';
import {
  closedLoopSuccessCopy,
  formErrorDisplay,
  formErrorLabel,
  formatLearningProgressLabel,
  formatLearningProgressTooltip,
  formatModalityLabel,
  isInsufficientFormCorrectionData,
  preferredModalityCopy,
} from '../src/components/adaptive/form-error-labels';

assert.equal(formErrorLabel('SAG'), 'Hips sagging');
assert.equal(formErrorLabel('KNEES_IN'), 'Knees moving inward');
assert.equal(formErrorDisplay('LOW_ROM'), 'Incomplete movement');
assert.ok(!formErrorDisplay('SAG').includes('SAG'));

assert.equal(formatModalityLabel('VERBAL_TTS'), 'Voice Coaching');
assert.equal(formatModalityLabel('VERBAL_TEXT'), 'Text Coaching');
assert.equal(formatModalityLabel('VISUAL_HIGHLIGHT'), 'Visual Guidance');

assert.equal(formatLearningProgressLabel(0.35), 'Improving');
assert.equal(formatLearningProgressLabel(-0.1), 'Needs attention');
assert.equal(formatLearningProgressLabel(0), 'Stable');
assert.match(formatLearningProgressTooltip(0.35), /\+0\.35/);

assert.equal(
  closedLoopSuccessCopy(18, 22),
  "18 of 22 coaching sessions resulted in measurable improvements to the student's form."
);

const learned = preferredModalityCopy({
  modality: 'VISUAL_HIGHLIGHT',
  source: 'LEARNED',
  n: 5,
});
assert.equal(learned.title, 'Preferred: Visual Guidance');
assert.equal(learned.detail, 'Based on 5 successful coaching responses.');

const exploring = preferredModalityCopy({
  modality: 'VERBAL_TEXT',
  source: 'EXPLORING',
  n: 2,
});
assert.match(exploring.title, /Still learning/);
assert.equal(exploring.detail, 'Based on 2 successful coaching responses.');

assert.equal(isInsufficientFormCorrectionData(3, 0), true);
assert.equal(isInsufficientFormCorrectionData(3, 0.04), true);
assert.equal(isInsufficientFormCorrectionData(0, 0), false);
assert.equal(isInsufficientFormCorrectionData(2, 0.05), false);

console.log('form-error-labels.selftest: ok');
