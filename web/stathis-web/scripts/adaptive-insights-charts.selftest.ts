import assert from 'node:assert/strict';
import {
  buildMasteryByExerciseChartData,
  buildRecurringErrorsChartData,
  hasChartableInsights,
  MASTERY_CATEGORY_NAMES,
  MASTERY_CHART_Y_DOMAIN,
} from '../src/components/adaptive/adaptive-insights-charts';

assert.equal(buildRecurringErrorsChartData({ DEPTH_LOW: 3, SAG: 1 }, 1).length, 1);
assert.equal(
  buildRecurringErrorsChartData({ DEPTH_LOW: 3, SAG: 1 }, 1)[0].error,
  'Not deep enough'
);
assert.equal(
  buildRecurringErrorsChartData({ SAG: 2, LOW_ROM: 1 }, 2)
    .map((r) => r.error)
    .join('|'),
  'Hips sagging|Incomplete movement'
);
assert.equal(
  buildMasteryByExerciseChartData([
    { physicalId: 'm1', studentId: 's', exerciseType: 'SQUAT', masteryLevel: 0.42 },
  ])[0].masteryPct,
  42
);

const zeroMastery = buildMasteryByExerciseChartData([
  { physicalId: 'm1', studentId: 's', exerciseType: 'SQUATS', masteryLevel: 0 },
  { physicalId: 'm2', studentId: 's', exerciseType: 'LUNGES', masteryLevel: 0 },
]);
assert.equal(zeroMastery.length, 2);
assert.equal(zeroMastery[0].masteryPct, 0);
assert.deepEqual(MASTERY_CHART_Y_DOMAIN, [0, 100]);
assert.equal(MASTERY_CATEGORY_NAMES.masteryPct, 'APSLE Form Mastery');

assert.equal(
  hasChartableInsights({
    studentId: 's',
    totalInterventions: 1,
    successfulInterventions: 0,
    overallSuccessRate: 0,
    mastery: [{ physicalId: 'm1', studentId: 's', exerciseType: 'SQUAT', masteryLevel: 0.5 }],
  }),
  true
);

console.log('adaptive-insights-charts.selftest: ok');
