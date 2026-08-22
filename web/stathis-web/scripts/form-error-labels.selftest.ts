import assert from 'node:assert/strict';
import {
  formErrorDisplay,
  formErrorLabel,
  formatLearningProgressLabel,
  isInsufficientFormCorrectionData,
} from '../src/components/adaptive/form-error-labels';

assert.equal(formErrorLabel('SAG'), 'Hips sagging');
assert.equal(formErrorLabel('KNEES_IN'), 'Knees moving inward');
assert.equal(formErrorDisplay('LOW_ROM'), 'Incomplete movement');
assert.ok(!formErrorDisplay('SAG').includes('SAG'));
assert.equal(formErrorDisplay('DEPTH_LOW'), 'Not deep enough');

assert.equal(formatLearningProgressLabel(0.35), 'Improving');
assert.equal(isInsufficientFormCorrectionData(3, 0), true);
assert.equal(isInsufficientFormCorrectionData(3, 0.04), true);
assert.equal(isInsufficientFormCorrectionData(0, 0), false);
assert.equal(isInsufficientFormCorrectionData(2, 0.05), false);

console.log('form-error-labels.selftest: ok');
