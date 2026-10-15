# APSLE volume calibration (read-only)

Snapshot from Supabase via `backend/stathis/scripts/apsle_volume_audit_readonly.py` on **2026-07-30**.

| Table | Rows |
|-------|------|
| feedback_intervention | 67 |
| feedback_response | 58 |
| student_learning_profile | 1 |
| exercise_mastery | 4 |
| learning_profile_history | 22 |

| Finding | Value |
|---------|-------|
| Duplicate FR by intervention_physical_id | **0 groups** |
| Distinct students | 1 (`26-6310-714`) |
| Distinct sessions | 5 |
| FI per session avg / max / p50 | 13.4 / 24 / 10 |
| Calendar span | All rows on 2026-07-30 (acceptance testing) |

## Interpretation

- Growth matches **gated coaching events**, not per-frame spam.
- ~13 interventions/session is consistent with sustained form errors under the 4/min gate over a multi-minute session.
- FI (67) > FR (58): some interventions still open or flushed without paired response upload yet — monitor, not proof of duplication.
- Absolute volume is **small**; pilot risk is future scale, not current DB size.

## Manual DDL (prod `ddl-auto=validate`)

Apply in Supabase SQL editor (or direct DB host — not transaction pooler):

1. [`V4__apsle_feedback_indexes_and_response_unique.sql`](../backend/stathis/src/main/resources/db/migration/V4__apsle_feedback_indexes_and_response_unique.sql)
2. [`V5__apsle_arm_session_rollup.sql`](../backend/stathis/src/main/resources/db/migration/V5__apsle_arm_session_rollup.sql)

Helpers: `python backend/stathis/scripts/apply_v4_apsle_indexes.py` then `apply_v5_apsle_rollup.py`.
