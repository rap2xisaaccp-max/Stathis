'use client';

import { useState, useEffect, useCallback } from 'react';
import { WebSocketManager } from './websocket-client';

export interface ExerciseProgressDTO {
  studentId: string;
  studentName?: string;
  classroomId?: string;
  taskId?: string;
  exerciseTemplateId?: string;
  exerciseType?: string;
  reps: number;
  goalReps?: number;
  accuracy?: number;
  timeTakenMs?: number;
  sessionCaloriesBurned?: number;
  totalCaloriesBurned?: number;
  score?: number;
  completed?: boolean;
  timestamp?: string;
}

/**
 * Subscribe to live exercise progress (reps, calories) for a classroom.
 * Matches backend topics: /topic/classroom/{id}/exercise-progress and /topic/exercise-progress
 */
export function useExerciseProgress(classroomId: string, taskId?: string) {
  const [progressByStudent, setProgressByStudent] = useState<
    Record<string, ExerciseProgressDTO>
  >({});
  const [isConnected, setIsConnected] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const handleProgressMessage = useCallback(
    (data: any) => {
      if (!data || typeof data !== 'object' || !data.studentId) {
        return;
      }
      if (classroomId && data.classroomId && data.classroomId !== classroomId) {
        return;
      }
      if (taskId && data.taskId && data.taskId !== taskId) {
        return;
      }

      const progress: ExerciseProgressDTO = {
        studentId: data.studentId,
        studentName: data.studentName,
        classroomId: data.classroomId,
        taskId: data.taskId,
        exerciseTemplateId: data.exerciseTemplateId,
        exerciseType: data.exerciseType,
        reps: typeof data.reps === 'number' ? data.reps : 0,
        goalReps: data.goalReps,
        accuracy: data.accuracy,
        timeTakenMs: data.timeTakenMs,
        sessionCaloriesBurned: data.sessionCaloriesBurned,
        totalCaloriesBurned: data.totalCaloriesBurned,
        score:
          typeof data.score === 'number'
            ? data.score
            : data.goalReps && data.goalReps > 0
              ? Math.min(100, Math.round((data.reps / data.goalReps) * 100))
              : undefined,
        completed: !!data.completed,
        timestamp: data.timestamp || new Date().toISOString(),
      };

      setProgressByStudent((prev) => ({
        ...prev,
        [progress.studentId]: progress,
      }));
      setLastUpdated(new Date());
    },
    [classroomId, taskId]
  );

  useEffect(() => {
    if (!classroomId) {
      setProgressByStudent({});
      return;
    }

    const wsManager = WebSocketManager.getInstance();
    const subscriptions: (() => void)[] = [];

    subscriptions.push(
      wsManager.subscribe('$SYSTEM/connected', () => setIsConnected(true))
    );
    subscriptions.push(
      wsManager.subscribe('$SYSTEM/disconnected', () => setIsConnected(false))
    );

    const classroomTopic = `/topic/classroom/${classroomId}/exercise-progress`;
    subscriptions.push(wsManager.subscribe(classroomTopic, handleProgressMessage));
    subscriptions.push(
      wsManager.subscribe('/topic/exercise-progress', handleProgressMessage)
    );

    if (!wsManager.isConnected()) {
      const token =
        typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null;
      wsManager.connect(token || undefined);
    } else {
      setIsConnected(true);
    }

    return () => {
      subscriptions.forEach((unsub) => unsub());
    };
  }, [classroomId, handleProgressMessage]);

  return {
    progressByStudent,
    isConnected,
    lastUpdated,
  };
}
