-- NOT part of the V8 form-correction evidence release.
-- Do not apply this script when deploying evidence capture.
-- V8 (form_correction_evidence) is the required additive migration.
-- Apply this DROP only later, in an explicit maintenance window, after no running
-- backend maps AdaptiveArmSessionRollup.
-- Do NOT drop preferred_modality / experiment_arm columns while Hibernate still maps them.

DROP TABLE IF EXISTS adaptive_arm_session_rollup;
