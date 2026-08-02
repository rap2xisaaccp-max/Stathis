# APSLE Phase 11 — Regression / readiness report

Date: 2026-08-02 · Branch work: APSLE stabilization Phases 0–11

| Area | Item | Status | Notes |
| --- | --- | --- | --- |
| Auth | Student adaptive APIs (own profile/batch) | PASS (code) | Existing role guards unchanged |
| Auth | Teacher insights/evaluation | PASS (code) | Evaluation UI gated; APIs kept |
| Catalog | Five exercises distinct coaching copy | PASS | `CoachingInstructionCatalog` + unit tests |
| Lifecycle | Intervention state machine + anti-spam | PASS (code) | Mobile `InterventionLifecycle` |
| Preferred | By-exercise JSON + LEARNED n≥5 | PASS (code) | V7 migration + `derivePreferredByExercise` |
| Persistence | FI idempotent; FR requires FI; batch partial errors | PASS (code) | Unique FR constraint retained |
| Mobile | Practice `CONTEXT_PRACTICE` | PASS (code) | `ExerciseTemplateRenderer` param |
| Mobile | Preferred-by-exercise in mastery UI | PASS (code) | Learning / Insufficient data copy |
| Teacher web | Preferred Modality by Exercise widget | PASS (code) | Insights payload + card |
| Teacher web | Recent Adaptive Interventions + FR delta | PASS (code) | |
| Teacher web | RCT hidden by default | PASS (code) | `research=1` or `NEXT_PUBLIC_APSLE_SHOW_RCT` |
| Teacher web | Chart theme tokens | PASS (code) | `var(--chart-N)` / `var(--primary)` |
| Cleanup | Agent debug logs removed | PASS | |
| Tests | Catalog / preferred-by-exercise / lifecycle | PASS (added) | Run CI / local gradle |
| E2E | Manual matrix all five exercises | BLOCKED | Requires device + live API/DB (`docs/apsle-e2e-matrix.md`) |
| Prod DB | Apply V7 column on Supabase | BLOCKED | Manual `ALTER TABLE` like prior V6 |

## P0 release gates

1. Apply V7 on production Postgres before deploying backend with new entity field.
2. Complete device E2E matrix rows (not fake-pass).
3. Confirm teacher Adaptive page without `research=1` has no Adaptive vs Static / Classroom Comparison.

Overall readiness: **CONDITIONAL** — code complete; prod migration + device E2E still required.
