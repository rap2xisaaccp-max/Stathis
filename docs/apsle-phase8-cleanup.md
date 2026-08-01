# APSLE Phase 8 — safe cleanup checklist

| Item | Action | Reason |
| --- | --- | --- |
| `AgentDebugLog` + StudentTaskService folded logs | Removed | Temporary debug instrumentation |
| Teacher RCT cards on default Adaptive UI | Hidden (`research=1` / `NEXT_PUBLIC_APSLE_SHOW_RCT`) | Misleading for classroom teachers |
| Chart `hsl(var(--…))` against oklch tokens | Fixed to `var(--…)` | Invalid CSS colors |
| `hasChartableInsights` | Kept (selftest reference) | Still used by scripts |
| `RctEvaluationMetrics`, evaluation endpoints, rollup tables | Kept | Research backends |
| `RealtimeInterventionGate` | Kept | Backward-compatible tests; engine uses `InterventionLifecycle` |
| `DEMONSTRATION` modality | Kept in enum | Unused in active policy set by design |

No research API or table was deleted.
