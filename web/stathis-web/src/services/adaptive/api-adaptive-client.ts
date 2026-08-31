import { serverApiClient } from '@/lib/api/server-client';

export interface PreferredModalityByExerciseEntry {
  modality?: string;
  n?: number;
  meanDelta?: number;
  confidence?: number;
  source?: 'DEFAULT' | 'EXPLORING' | 'LEARNED' | string;
}

export interface StudentLearningProfileDTO {
  physicalId: string;
  studentId: string;
  preferredModality?: string | null;
  modalityEffectivenessJson?: Record<string, any> | null;
  preferredModalityByExercise?: Record<string, PreferredModalityByExerciseEntry> | null;
  learningRateEstimate?: number | null;
  consistencyScore?: number | null;
  fatigueSensitivity?: number | null;
  totalInterventions?: number | null;
  totalSuccessfulInterventions?: number | null;
  updatedAt?: string | null;
}

export interface ExerciseMasteryDTO {
  physicalId: string;
  studentId: string;
  exerciseType: string;
  /** Coaching-frequency estimate. Not Form Mastery. */
  masteryLevel: number;
  commonErrorsJson?: Record<string, number> | null;
  sessionsCount?: number | null;
  medianTimeToCorrectionMs?: number | null;
  /** BEGINNER | INTERMEDIATE | ADVANCED */
  recommendedDifficulty?: string | null;
  /** Soft suggestion aligned to 10 / 20 / 30 */
  recommendedGoalReps?: number | null;
  recommendationRationale?: string | null;
  requiresTeacherApproval?: boolean;
  lastSessionAt?: string | null;
}

export interface FormMasteryDTO {
  studentId: string;
  exerciseType: string;
  /** Mean classroom attempt accuracy / 100, in [0, 1]. */
  formMasteryLevel: number;
  /** Mean of recorded accuracy values, in [0, 100]. */
  formMasteryPercent: number;
  eligibleAttemptCount: number;
  lastAttemptAt?: string | null;
}

export interface DifficultyRecommendationDTO {
  studentId: string;
  exerciseType: string;
  /** Coaching-frequency mastery_level. Isolated from Form Mastery; pending recalibration. */
  masteryLevel: number;
  sessionsCount?: number | null;
  /** BEGINNER | INTERMEDIATE | ADVANCED (never EXPERT) */
  recommendedDifficulty?: string | null;
  /** Soft suggestion aligned to 10 / 20 / 30 */
  recommendedGoalReps?: number | null;
  rationale?: string | null;
  requiresTeacherApproval?: boolean;
  topErrors?: string[];
}

export interface ProfileHistoryPointDTO {
  physicalId: string;
  createdAt?: string | null;
  reason?: string | null;
  learningRateEstimate?: number | null;
  consistencyScore?: number | null;
  meanMasteryLevel?: number | null;
  totalInterventions?: number | null;
  preferredModality?: string | null;
}

export interface AdaptiveInsightsDTO {
  studentId: string;
  profile?: StudentLearningProfileDTO | null;
  /** Coaching-frequency rows. Not Form Mastery. */
  mastery?: ExerciseMasteryDTO[];
  /** Attempt-level form quality from completed classroom score_attempt accuracy. */
  formMastery?: FormMasteryDTO[];
  preferredModalityByExercise?: Record<string, PreferredModalityByExerciseEntry> | null;
  modalityMeanDelta?: Record<string, number>;
  topRecurringErrors?: Record<string, number>;
  totalInterventions: number;
  successfulInterventions: number;
  overallSuccessRate: number;
  recentInterventions?: Array<{
    physicalId: string;
    exerciseType: string;
    errorCode: string;
    modality: string;
    messageText?: string;
    messageCode?: string;
    baselineSeverity: number;
    policySource: string;
    experimentArm?: string;
    deliveredAt?: string;
    responseSuccess?: boolean | null;
    responseDelta?: number | null;
    correctionDelivered?: string | null;
  }>;
  profileHistory?: ProfileHistoryPointDTO[];
}

export async function fetchAdaptiveInsights(
  studentId: string
): Promise<AdaptiveInsightsDTO> {
  const { data, error, status } = await serverApiClient.get<AdaptiveInsightsDTO>(
    `/adaptive/insights/${encodeURIComponent(studentId)}`
  );
  if (error || !data) {
    throw new Error(error || `Adaptive insights unavailable (${status})`);
  }
  return data;
}

export async function fetchDifficultyRecommendations(
  studentId: string
): Promise<DifficultyRecommendationDTO[]> {
  const { data, error, status } = await serverApiClient.get<DifficultyRecommendationDTO[]>(
    `/adaptive/difficulty-recommendations/${encodeURIComponent(studentId)}`
  );
  if (error) {
    throw new Error(error || `Difficulty recommendations unavailable (${status})`);
  }
  return data ?? [];
}

export interface FormCorrectionEvidenceDTO {
  physicalId: string;
  interventionPhysicalId: string;
  studentId: string;
  sessionId: string;
  taskId?: string | null;
  classroomId?: string | null;
  attemptNumber?: number | null;
  exerciseType: string;
  errorCode: string;
  errorLabel?: string | null;
  errorDescription?: string | null;
  correctionText?: string | null;
  capturedAt?: string | null;
  createdAt?: string | null;
  byteSize?: number;
  imageUrl?: string | null;
}

export async function fetchFormCorrectionEvidence(
  studentId: string,
  classroomId?: string | null
): Promise<FormCorrectionEvidenceDTO[]> {
  const params = classroomId
    ? `?classroomId=${encodeURIComponent(classroomId)}`
    : '';
  const { data, error, status } = await serverApiClient.get<FormCorrectionEvidenceDTO[]>(
    `/adaptive/evidence/students/${encodeURIComponent(studentId)}${params}`
  );
  if (error) {
    throw new Error(error || `Form-correction evidence unavailable (${status})`);
  }
  return data ?? [];
}
