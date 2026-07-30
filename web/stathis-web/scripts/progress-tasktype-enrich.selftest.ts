/**
 * Regression: enriching progress items must preserve backend taskType so
 * multi-component tasks (same taskId, QUIZ + EXERCISE) stay distinct.
 */
function enrichProgressNamesOnly(
  progressItems: Array<{ taskId: string; taskName: string; taskType: string }>,
  tasks: Record<string, { name?: string; type?: string }>
) {
  for (const item of progressItems) {
    const taskInfo = tasks[item.taskId];
    if (taskInfo) {
      item.taskName = taskInfo.name || item.taskName;
      // intentionally do NOT overwrite item.taskType
    }
  }
  return progressItems;
}

const items = enrichProgressNamesOnly(
  [
    { taskId: 'TASK-1', taskName: 'Old', taskType: 'QUIZ' },
    { taskId: 'TASK-1', taskName: 'Old', taskType: 'EXERCISE' },
  ],
  { 'TASK-1': { name: 'Pushup Combo', type: 'QUIZ' } }
);

if (items[0].taskName !== 'Pushup Combo' || items[1].taskName !== 'Pushup Combo') {
  throw new Error('expected taskName enrichment');
}
if (items[0].taskType !== 'QUIZ' || items[1].taskType !== 'EXERCISE') {
  throw new Error(`taskType overwritten: ${items[0].taskType}, ${items[1].taskType}`);
}

console.log('progress-tasktype-enrich.selftest: ok');
