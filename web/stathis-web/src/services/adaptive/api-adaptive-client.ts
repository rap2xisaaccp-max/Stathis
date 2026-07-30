import { serverApiClient } from '@/lib/api/server-client';

export interface StudentLearningProfileDTO {
  physicalId: string;
  studentId: string;
  preferredModality?: string | null;
  modalityEffectivenessJson?: Record<string, any> | null;
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
    baselineSeverity: number;
    policySource: string;
    experimentArm?: string;
    deliveredAt?: string;
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
  // #region agent log
  fetch('http://127.0.0.1:7316/ingest/495f4aba-74a7-432b-b062-a71e4ed7ed12',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'b7147e'},body:JSON.stringify({sessionId:'b7147e',runId:'adaptive-ui',hypothesisId:'W1',location:'api-adaptive-client.ts:fetchAdaptiveInsights',message:'insights loaded',data:{status,hasProfile:!!data.profile,masteryCount:(data.mastery||[]).length,modalityKeys:Object.keys(data.modalityMeanDelta||{}).length,errorKeys:Object.keys(data.topRecurringErrors||{}).length,recentCount:(data.recentInterventions||[]).length,historyCount:(data.profileHistory||[]).length,totalInterventions:data.totalInterventions,successRate:data.overallSuccessRate},timestamp:Date.now()})}).catch(()=>{});
  // #endregion
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
  // #region agent log
  fetch('http://127.0.0.1:7316/ingest/495f4aba-74a7-432b-b062-a71e4ed7ed12',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'b7147e'},body:JSON.stringify({sessionId:'b7147e',runId:'adaptive-ui',hypothesisId:'W2',location:'api-adaptive-client.ts:fetchAdaptiveEvaluation',message:'evaluation loaded',data:{status,arm:data.experimentArm,successRate:data.successRate,meanDelta:data.meanDelta,modalityKeys:Object.keys(data.meanDeltaByModality||{}).length,sessionsTracked:data.sessionsTracked},timestamp:Date.now()})}).catch(()=>{});
  // #endregion
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
  // #region agent log
  fetch('http://127.0.0.1:7316/ingest/495f4aba-74a7-432b-b062-a71e4ed7ed12',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'b7147e'},body:JSON.stringify({sessionId:'b7147e',runId:'adaptive-ui',hypothesisId:'W3',location:'api-adaptive-client.ts:fetchClassroomEvaluation',message:'classroom evaluation loaded',data:{status,studentCount:data.studentCount,hasBothArms:data.adaptiveMeanDelta!=null&&data.staticMeanDelta!=null,studentsLen:(data.students||[]).length,overallSuccessRate:data.overallSuccessRate,meanMasteryLevel:data.meanMasteryLevel},timestamp:Date.now()})}).catch(()=>{});
  // #endregion
  return data;
}
