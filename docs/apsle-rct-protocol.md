# APSLE Classroom Pilot RCT Protocol

Pre-registration-style protocol for evaluating the Adaptive Physical Skill Learning Engine (APSLE) in STATHIS. Aligns with plan §16.

## Primary research question

Does adaptive feedback that learns individual responses to physical instruction improve exercise skill acquisition versus static text feedback?

## Design

| Arm | Mechanism | Client flag |
|-----|-----------|-------------|
| **Treatment (ADAPTIVE)** | Epsilon-greedy modality selection from measured deltas | `rct_static_control=false` (default) |
| **Control (STATIC)** | Open-loop verbal text only (`PolicySource.STATIC_CONTROL`) | `rct_static_control=true` in SharedPreferences `stathis_adaptive` |

- **Randomization:** individual (preferred for app pilots) or cluster-by-classroom.
- **Duration:** 4–8 weeks of classroom tasks + practice; optional retention session 1–2 weeks later.
- **Shared detectors:** both arms use the same pose/rules error codes and severity; only the feedback policy differs.
- **Practice volume:** ungraded practice logs as `*_PRACTICE` arms for research volume without changing grades.

## Assignment procedure

1. Assign each enrolled student (or classroom) to ADAPTIVE or STATIC before week 1.
2. On student devices in the STATIC cohort, set:

```text
SharedPreferences: stathis_adaptive / rct_static_control = true
```

   (or call `RctExperimentPrefs.setStaticControl(context, true)` from a researcher/debug build).
3. Confirm interventions store `experimentArm` of `ADAPTIVE`, `STATIC`, `ADAPTIVE_PRACTICE`, or `STATIC_PRACTICE`.

## Primary outcomes (export fields)

| Metric | Source |
|--------|--------|
| Mean post-intervention Δ | `FeedbackResponse.delta` / evaluation `meanDelta` |
| Intervention success rate | `success` where Δ ≥ threshold (~0.15) |
| Error recurrence | `topRecurringErrors` / `errorFrequency` |
| Mean mastery | `ExerciseMastery.masteryLevel` |
| Sessions tracked | mastery `sessionsCount` sum |

Classroom ablation export:

`GET /api/adaptive/evaluation/classroom/{classroomId}`

Returns adaptive vs static mean Δ, success-rate lift, and Cohen's d.

Student export:

`GET /api/adaptive/evaluation/{studentId}`

## Secondary outcomes

- Engagement: sessions completed / practice interventions
- Teacher workload: time in monitoring / adaptive insights (manual log)
- Safety covariates: vitals (not primary efficacy)
- Optional short satisfaction / NASA-TLX after week 4 and week 8

## Analysis plan

- Mixed-effects models with student nested in classroom when cluster-randomized
- Report effect sizes (Cohen's d on Δ); pre-register that pose noise may attenuate effects
- Ablation contrast uses **base arms** (`ADAPTIVE` vs `STATIC`), pooling practice suffixes
- Success criterion: meaningful improvement on primary form-learning metrics **and** evidence that modality selection correlates with individual historical deltas (policy learning, not random)

## Privacy / ethics

- Store error codes, severities, and deltas — not raw landmark windows for research export
- Teacher access limited to enrolled classrooms
- Consent for research use of adaptive logs; do not use low mastery punitively
- Face embeddings remain identity-only; never feed into pedagogy profiles

## Data lifecycle (volume control)

Raw closed-loop events live in `feedback_intervention` / `feedback_response` (1 delivered coaching cue → 1 FI + 1 FR on session flush). This is **expected research-grain logging**, not per-frame spam (mobile gate: confirm ticks, cooldown, max 4/min, one open response window).

**Permanent / long-lived**

- `student_learning_profile`, `exercise_mastery`, `learning_profile_history`
- `adaptive_arm_session_rollup` — per `(student, session, baseArm)` with `n`, `sum_delta`, `sum_delta_sq`, `successes` so **Cohen's d** remains computable if raw rows are later purged

**Raw retention (optional, off by default)**

```properties
apsle.retention.enabled=false
apsle.retention.dry-run=true
apsle.retention.raw-days=180
```

When enabled (and dry-run false), only raw FI/FR older than `raw-days` **with an existing rollup** are deleted. Profile/mastery/rollups are never purged by this job.

**Idempotency**

- Client `FI-` / `FR-` physical IDs + server lookup-before-insert
- DB unique on `feedback_response.intervention_physical_id` (one response per intervention)

## Instrumentation checklist

- [ ] Arms assigned and verified on a sample of `FeedbackIntervention.experimentArm` rows
- [ ] Task and practice sessions both flush adaptive batches
- [ ] Teacher can open Adaptive tab + classroom evaluation endpoint
- [ ] Static cohort only receives `VERBAL_TEXT` recommendations
- [ ] Retention session scheduled and labeled in analysis notes
- [ ] V4/V5 SQL applied on prod (indexes + rollup table) before enabling retention
