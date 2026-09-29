import assert from 'node:assert/strict';
import {
  buildFormMasteryByExerciseChartData,
  formMasteryDisplayPercent,
} from '../src/components/adaptive/form-mastery-chart';

assert.equal(formMasteryDisplayPercent(0), 0);
assert.equal(formMasteryDisplayPercent(0.5), 50);
assert.equal(formMasteryDisplayPercent(1), 100);
assert.equal(
  formMasteryDisplayPercent((40 + 60) / 2 / 100),
  50,
  'display percent rounds a 0.50 level to 50'
);

assert.equal(
  buildFormMasteryByExerciseChartData([
    {
      studentId: 's',
      exerciseType: 'SQUATS',
      formMasteryLevel: 0.5,
      formMasteryPercent: 50,
      eligibleAttemptCount: 2,
    },
  ])[0].masteryPct,
  50
);

const withoutState = buildFormMasteryByExerciseChartData([
  {
    exerciseType: 'SQUATS',
    formMasteryLevel: 0.47,
    eligibleAttemptCount: 2,
  },
])[0];
assert.equal(withoutState.exercise, 'SQUATS');
assert.equal(withoutState.state, '');
assert.equal(withoutState.masteryPct, 47);

const withStates = buildFormMasteryByExerciseChartData([
  { exerciseType: 'PUSH_UP', formMasteryLevel: 0.9, state: 'MASTERED', eligibleAttemptCount: 4 },
  { exerciseType: 'STATIC_LUNGES', formMasteryLevel: 0.72, state: 'IMPROVING', eligibleAttemptCount: 3 },
  { exerciseType: 'LYING_LEG_RAISES', formMasteryLevel: 0.4, state: 'LEARNING', eligibleAttemptCount: 1 },
]);
assert.deepEqual(
  withStates.map((row) => row.exercise),
  ['PUSH UP · Mastered', 'STATIC LUNGES · Improving', 'LYING LEG RAISES · Learning']
);
assert.deepEqual(
  withStates.map((row) => row.masteryPct),
  [90, 72, 40]
);

assert.equal(
  buildFormMasteryByExerciseChartData([]).length,
  0,
  'no-data must not render 0% bars'
);

assert.equal(
  buildFormMasteryByExerciseChartData(undefined).length,
  0,
  'missing Form Mastery payload cannot invent bars from coaching-frequency'
);

const measuredZero = buildFormMasteryByExerciseChartData([
  {
    studentId: 's',
    exerciseType: 'SQUATS',
    formMasteryLevel: 0,
    formMasteryPercent: 0,
    eligibleAttemptCount: 1,
  },
]);
assert.equal(measuredZero.length, 1);
assert.equal(measuredZero[0].masteryPct, 0);

const measuredHundred = buildFormMasteryByExerciseChartData([
  {
    studentId: 's',
    exerciseType: 'PUSH_UP',
    formMasteryLevel: 1,
    formMasteryPercent: 100,
    eligibleAttemptCount: 1,
  },
]);
assert.equal(measuredHundred[0].masteryPct, 100);

function hasChartableFormMastery(data: {
  topRecurringErrors?: Record<string, number>;
  mastery?: unknown[];
  formMastery?: unknown[];
}): boolean {
  return (
    Object.keys(data.topRecurringErrors || {}).length > 0 ||
    (data.formMastery || []).length > 0
  );
}
assert.equal(
  hasChartableFormMastery({
    mastery: [{ masteryLevel: 1 }],
  }),
  false,
  'old mastery_level rows must not make Form Mastery chartable'
);
assert.equal(
  hasChartableFormMastery({
    formMastery: [{ formMasteryLevel: 0 }],
  }),
  true
);

console.log('adaptive-insights-charts.selftest: ok');
