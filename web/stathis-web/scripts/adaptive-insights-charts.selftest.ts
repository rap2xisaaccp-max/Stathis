import assert from 'node:assert/strict';
import {
  buildMasteryByExerciseChartData,
  buildModalityEffectivenessChartData,
  buildRecurringErrorsChartData,
  hasChartableInsights,
} from '../src/components/adaptive/adaptive-insights-charts';

assert.deepEqual(
  buildModalityEffectivenessChartData({ VERBAL_TEXT: 0.2, VISUAL_HIGHLIGHT: 0.5 }),
  [
    { modality: 'VISUAL HIGHLIGHT', delta: 0.5 },
    { modality: 'VERBAL TEXT', delta: 0.2 },
  ]
);

assert.equal(buildRecurringErrorsChartData({ DEPTH_LOW: 3, SAG: 1 }, 1).length, 1);
assert.equal(buildMasteryByExerciseChartData([{ physicalId: 'm1', studentId: 's', exerciseType: 'SQUAT', masteryLevel: 0.42 }])[0].masteryPct, 42);
assert.equal(
  hasChartableInsights({
    studentId: 's',
    totalInterventions: 1,
    successfulInterventions: 1,
    overallSuccessRate: 1,
    mastery: [{ physicalId: 'm1', studentId: 's', exerciseType: 'SQUAT', masteryLevel: 0.5 }],
  }),
  true
);

console.log('adaptive-insights-charts.selftest: ok');
