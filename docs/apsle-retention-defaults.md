# APSLE raw-data retention defaults

| Property | Default | Meaning |
| --- | --- | --- |
| `apsle.retention.enabled` | `false` | Retention job off unless explicitly enabled |
| `apsle.retention.dry-run` | `true` | When enabled, log candidates without deleting |
| `apsle.retention.raw-days` | `180` | Age threshold for raw FI/FR cleanup |
| `apsle.retention.cron` | `0 15 3 * * *` | Daily schedule |

Keep dry-run until a deliberate research archival policy is approved. Aggregates (`student_learning_profile`, `exercise_mastery`, `adaptive_arm_session_rollup`, `learning_profile_history`) are not purged by this job.
