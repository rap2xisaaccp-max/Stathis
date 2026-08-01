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
  masteryLevel: number;
  commonErrorsJson?: Record<string, number> | null;
  sessionsCount?: number | null;
  medianTimeToCorrectionMs?: number | null;
  recommendedDifficulty?: string | null;
  recommendedGoalReps?: number | null;
  recommendationRationale?: string | null;
  requiresTeacherApproval?: boolean;
  lastSessionAt?: string | null;
}

export interface DifficultyRecommendationDTO {
  studentId: string;
  exerciseType: string;
  masteryLevel: number;
  sessionsCount?: number | null;
  recommendedDifficulty: string;
  recommendedGoalReps: number;
  rationale: string;
  requiresTeacherApproval: boolean;
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
  mastery?: ExerciseMasteryDTO[];
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

export interface AdaptiveEvaluationSummaryDTO {
  studentId: string;
  experimentArm?: string | null;
  totalInterventions: number;
  successfulInterventions: number;
  successRate: number;
  meanDelta: number;
  meanDeltaByModality?: Record<string, number>;
  errorFrequency?: Record<string, number>;
  meanMasteryLevel?: number | null;
  sessionsTracked?: number | null;
  practiceInterventions?: number;
  taskInterventions?: number;
  interventionsByArm?: Record<string, number>;
}

export async function fetchAdaptiveEvaluation(
  studentId: string
): Promise<AdaptiveEvaluationSummaryDTO | null> {
  const { data, error, status } = await serverApiClient.get<AdaptiveEvaluationSummaryDTO>(
    `/adaptive/evaluation/${encodeURIComponent(studentId)}`
  );
  if (status === 404) return null;
  if (error || !data) {
    throw new Error(error || `Adaptive evaluation unavailable (${status})`);
  }
  return data;
}

export interface ClassroomEvaluationDTO {
  classroomId: string;
  studentCount: number;
  totalInterventions: number;
  successfulInterventions: number;
  overallSuccessRate: number;
  meanDelta: number;
  meanMasteryLevel: number;
  practiceInterventions: number;
  taskInterventions: number;
  interventionsByArm?: Record<string, number>;
  adaptiveMeanDelta?: number | null;
  staticMeanDelta?: number | null;
  meanDeltaLift?: number | null;
  successRateLift?: number | null;
  cohensD?: number | null;
  adaptiveOutperformsOnDelta?: boolean;
  students?: AdaptiveEvaluationSummaryDTO[];
}

export async function fetchClassroomEvaluation(
  classroomId: string
): Promise<ClassroomEvaluationDTO | null> {
  const { data, error, status } = await serverApiClient.get<ClassroomEvaluationDTO>(
    `/adaptive/evaluation/classroom/${encodeURIComponent(classroomId)}`
  );
  if (status === 404) return null;
  if (error || !data) {
    throw new Error(error || `Classroom evaluation unavailable (${status})`);
  }
  return data;
}
