'use client';

import React, { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  getTaskScores, 
  getQuizAverageScore, 
  getExerciseAverageScore,
  gradeManually,
  ScoreResponseDTO 
} from '@/services/scores/api-score-client';
import { useExerciseProgress } from '@/lib/websocket/use-exercise-progress';
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { 
  Pencil, 
  Clock, 
  CheckCircle2, 
  AlertCircle, 
  BarChart3, 
  Download,
  Loader2
} from 'lucide-react';
import { toast } from 'sonner';
import { format } from 'date-fns';
import {
  invalidateAfterStudentScoreMutation,
  teacherStudentViewQueryOptions,
} from '@/lib/query/teacher-student-freshness';

interface TaskScoresTabProps {
  taskId: string;
  taskType?: 'QUIZ' | 'EXERCISE' | 'LESSON';
  templateId?: string;
  classroomId?: string;
}

export function TaskScoresTab({ taskId, taskType, templateId, classroomId }: TaskScoresTabProps) {
  const [selectedScore, setSelectedScore] = useState<ScoreResponseDTO | null>(null);
  const [gradeDialogOpen, setGradeDialogOpen] = useState(false);
  const [manualScore, setManualScore] = useState<number>(0);
  const [feedback, setFeedback] = useState<string>('');
  
  const queryClient = useQueryClient();
  const { progressByStudent, lastUpdated } = useExerciseProgress(classroomId || '', taskId);

  // Live exercise progress: merge into cached scores and refresh on completion
  useEffect(() => {
    const liveEntries = Object.values(progressByStudent);
    if (liveEntries.length === 0) return;

    queryClient.setQueryData<ScoreResponseDTO[]>(['task-scores', taskId], (prev) => {
      if (!prev) return prev;
      return prev.map((score) => {
        const live = progressByStudent[score.studentId];
        if (!live) return score;
        const liveScore =
          live.score ??
          (live.goalReps && live.goalReps > 0
            ? Math.min(100, Math.round((live.reps / live.goalReps) * 100))
            : score.score);
        return {
          ...score,
          reps: live.reps,
          goalReps: live.goalReps ?? score.goalReps,
          accuracy: live.accuracy ?? score.accuracy,
          caloriesBurned:
            live.totalCaloriesBurned ??
            live.sessionCaloriesBurned ??
            score.caloriesBurned,
          score: liveScore,
          maxScore: score.maxScore || 100,
        };
      });
    });

    if (liveEntries.some((p) => p.completed)) {
      invalidateAfterStudentScoreMutation(queryClient, taskId);
    }
  }, [progressByStudent, lastUpdated, queryClient, taskId]);

  // Fetch all scores for this task
  const { 
    data: scores, 
    isLoading: isLoadingScores,
    isError: isScoresError,
    error: scoresError
  } = useQuery({
    queryKey: ['task-scores', taskId],
    queryFn: () => getTaskScores(taskId),
    ...teacherStudentViewQueryOptions,
  });

  // Fetch average score if template ID is provided
  const { 
    data: averageScore,
    isLoading: isLoadingAverage 
  } = useQuery({
    queryKey: ['average-score', taskId, templateId, taskType],
    queryFn: () => {
      if (!templateId || !taskType) return null;
      
      if (taskType === 'QUIZ') {
        return getQuizAverageScore(taskId, templateId);
      } else if (taskType === 'EXERCISE') {
        return getExerciseAverageScore(taskId, templateId);
      }
      return null;
    },
    enabled: !!templateId && !!taskType && (taskType === 'QUIZ' || taskType === 'EXERCISE')
  });

  // Manual grading mutation
  const manualGradeMutation = useMutation({
    mutationFn: ({ physicalId, score, feedback }: { physicalId: string, score: number, feedback?: string }) => 
      gradeManually(physicalId, { score, feedback }),
    onSuccess: () => {
      toast.success('Score updated successfully');
      setGradeDialogOpen(false);
      invalidateAfterStudentScoreMutation(
        queryClient,
        taskId,
        selectedScore?.studentId,
        classroomId
      );
      queryClient.invalidateQueries({ queryKey: ['average-score', taskId, templateId, taskType] });
    },
    onError: (error: any) => {
      toast.error(`Failed to update score: ${error.message}`);
    }
  });

  // Open grading dialog
  const openGradeDialog = (score: ScoreResponseDTO) => {
    setSelectedScore(score);
    setManualScore(score.score);
    setFeedback(score.feedback || '');
    setGradeDialogOpen(true);
  };

  // Submit manual grade
  const handleSubmitGrade = () => {
    if (!selectedScore) return;
    
    manualGradeMutation.mutate({
      physicalId: selectedScore.physicalId,
      score: manualScore,
      feedback
    });
  };

  // Export scores as CSV
  const exportScores = () => {
    if (!scores || scores.length === 0) {
      toast.error('No scores to export');
      return;
    }

    // Create CSV content with exercise-specific fields
    const isExerciseTask = taskType === 'EXERCISE' || scores.some(s => s.exerciseTemplateId);
    
    const headers = isExerciseTask 
      ? ['Student ID', 'Reps', 'Goal Reps', 'Accuracy (%)', 'Goal Accuracy (%)', 'Calories Burned', 'Score', 'Max Score', 'Attempts', 'Remaining Attempts', 'Submission Date', 'Status', 'Feedback']
      : ['Student ID', 'Score', 'Max Score', 'Attempts', 'Remaining Attempts', 'Submission Date', 'Status', 'Feedback'];
    
    const rows = scores.map(score => isExerciseTask ? [
      score.studentId,
      score.reps || 0,
      score.goalReps || 'N/A',
      score.accuracy !== undefined ? score.accuracy.toFixed(1) : 'N/A',
      score.goalAccuracy || 'N/A',
      score.caloriesBurned !== undefined ? score.caloriesBurned.toFixed(1) : '0',
      score.score,
      score.maxScore,
      score.attempts,
      score.remainingAttempts,
      score.submissionDate,
      score.status,
      score.feedback || ''
    ] : [
      score.studentId,
      score.score,
      score.maxScore,
      score.attempts,
      score.remainingAttempts,
      score.submissionDate,
      score.status,
      score.feedback || ''
    ]);

    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.join(','))
    ].join('\n');

    // Create download link
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `task-${taskId}-scores.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    toast.success('Scores exported successfully');
  };

  // Format date for display
  const formatDate = (dateString: string) => {
    try {
      return format(new Date(dateString), 'MMM dd, yyyy HH:mm');
    } catch (e) {
      return dateString;
    }
  };

  // Get status badge
  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING':
        return <Badge variant="outline" className="bg-yellow-50 text-yellow-700">Pending</Badge>;
      case 'COMPLETED':
        return <Badge variant="outline" className="bg-blue-50 text-blue-700">Completed</Badge>;
      case 'GRADED':
        return <Badge variant="outline" className="bg-green-50 text-green-700">Graded</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  // Error state
  if (isScoresError) {
    return (
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="text-red-600">Error Loading Scores</CardTitle>
        </CardHeader>
        <CardContent>
          <p>Failed to load student scores: {(scoresError as Error)?.message || 'Unknown error'}</p>
          <Button 
            variant="outline" 
            className="mt-4"
            onClick={() => queryClient.invalidateQueries({ queryKey: ['task-scores', taskId] })}
          >
            Retry
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-bold">Student Scores</h2>
        <div className="flex gap-2">
          <Button 
            variant="outline" 
            onClick={exportScores} 
            disabled={!scores || scores.length === 0}
          >
            <Download className="h-4 w-4 mr-2" />
            Export Scores
          </Button>
        </div>
      </div>

      {/* Stats Summary */}
      {scores && scores.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">Total Submissions</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{scores.length}</div>
            </CardContent>
          </Card>
          
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Average Score
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {isLoadingAverage ? (
                  <Loader2 className="h-6 w-6 animate-spin" />
                ) : averageScore !== null && averageScore !== undefined ? (
                  `${Number(averageScore).toFixed(1)}%`
                ) : scores.length > 0 ? (
                  (() => {
                    const pcts = scores
                      .filter((s) => (s.maxScore || 0) > 0 && (s.status === 'COMPLETED' || s.status === 'GRADED' || (s.attempts || 0) > 0))
                      .map((s) => ((s.score || 0) / (s.maxScore || 100)) * 100);
                    return pcts.length > 0
                      ? `${(pcts.reduce((a, b) => a + b, 0) / pcts.length).toFixed(1)}%`
                      : 'N/A';
                  })()
                ) : (
                  'N/A'
                )}
              </div>
            </CardContent>
          </Card>
          
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">Completed</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {scores.filter(s => s.status === 'GRADED' || s.status === 'COMPLETED').length} / {scores.length}
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Scores Table */}
      {isLoadingScores ? (
        <div className="flex justify-center items-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
          <span className="ml-2">Loading scores...</span>
        </div>
      ) : scores && scores.length > 0 ? (
        <Card>
          <CardContent className="p-0 overflow-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Student ID</TableHead>
                  <TableHead>{taskType === 'EXERCISE' ? 'Score & Reps' : 'Score'}</TableHead>
                  <TableHead>Attempts</TableHead>
                  <TableHead>Submission Date</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {scores.map((score) => {
                  // For exercises, display reps and accuracy instead of generic score
                  const isExercise = taskType === 'EXERCISE' || score.exerciseTemplateId;
                  
                  return (
                    <TableRow key={score.physicalId}>
                      <TableCell className="font-medium">{score.studentId}</TableCell>
                      <TableCell>
                        {isExercise ? (
                          <div className="space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="text-xs text-muted-foreground">Score:</span>
                              <span className="font-semibold text-primary">
                                {score.score ?? 0}/{score.maxScore || 100}
                              </span>
                              {progressByStudent[score.studentId] && !progressByStudent[score.studentId].completed && (
                                <Badge variant="outline" className="text-[10px] px-1.5 py-0 bg-blue-50 text-blue-700">Live</Badge>
                              )}
                            </div>
                            <div className="flex items-center gap-2">
                              <span className="text-xs text-muted-foreground">Reps:</span>
                              <span className="font-medium">{score.reps || 0}/{score.goalReps || 'N/A'}</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <span className="text-xs text-muted-foreground">Calories:</span>
                              <span className="font-medium">
                                {score.caloriesBurned !== undefined
                                  ? `${score.caloriesBurned.toFixed(1)} kcal`
                                  : '0 kcal'}
                              </span>
                            </div>
                          </div>
                        ) : (
                          <span>
                            {score.score} / {score.maxScore} ({score.maxScore > 0 ? ((score.score / score.maxScore) * 100).toFixed(1) : 0}%)
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {typeof score.remainingAttempts === 'number'
                          ? `${score.attempts || 0} / ${(score.remainingAttempts || 0) + (score.attempts || 0)}`
                          : `${score.attempts || 0}`}
                      </TableCell>
                      <TableCell>{formatDate(score.submissionDate)}</TableCell>
                      <TableCell>{getStatusBadge(score.status)}</TableCell>
                      <TableCell className="text-right">
                        <Button 
                          variant="ghost" 
                          size="icon" 
                          onClick={() => openGradeDialog(score)}
                          title="Grade submission"
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <BarChart3 className="h-12 w-12 text-muted-foreground mb-4" />
          <h3 className="text-lg font-medium">No scores found</h3>
          <p className="text-muted-foreground mt-1">
            No students have submitted this task yet.
          </p>
        </div>
      )}

      {/* Grading Dialog */}
      <Dialog open={gradeDialogOpen} onOpenChange={setGradeDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Grade Submission</DialogTitle>
            <DialogDescription>
              Manually review and grade this student's submission.
            </DialogDescription>
          </DialogHeader>
          
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-sm font-medium mb-1">Student ID</p>
                <p className="text-sm">{selectedScore?.studentId}</p>
              </div>
              <div>
                <p className="text-sm font-medium mb-1">Submission Date</p>
                <p className="text-sm">{selectedScore?.submissionDate ? formatDate(selectedScore.submissionDate) : 'N/A'}</p>
              </div>
            </div>
            
            {/* Show exercise-specific info if it's an exercise */}
            {(taskType === 'EXERCISE' || selectedScore?.exerciseTemplateId) && (
              <div className="grid grid-cols-3 gap-4 p-3 bg-muted rounded-lg">
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Score</p>
                  <p className="text-sm font-medium">
                    {selectedScore?.score ?? 0} / {selectedScore?.maxScore || 100}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Repetitions</p>
                  <p className="text-sm font-medium">{selectedScore?.reps || 0} / {selectedScore?.goalReps || 'N/A'}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground mb-1">Calories</p>
                  <p className="text-sm font-medium">
                    {selectedScore?.caloriesBurned !== undefined
                      ? `${selectedScore.caloriesBurned.toFixed(1)} kcal`
                      : '0 kcal'}
                  </p>
                </div>
              </div>
            )}
            
            <div className="grid gap-2">
              <label htmlFor="score" className="text-sm font-medium">
                {taskType === 'EXERCISE' ? 'Final Score (reps ÷ goal × 100)' : 'Score'}
              </label>
              <Input
                id="score"
                type="number"
                min={0}
                max={selectedScore?.maxScore || 100}
                value={manualScore}
                onChange={(e) => setManualScore(Number(e.target.value))}
              />
              <p className="text-xs text-muted-foreground">
                {taskType === 'EXERCISE' 
                  ? `Auto score = completed reps / target reps × 100. Maximum: ${selectedScore?.maxScore || 100}` 
                  : `Maximum score: ${selectedScore?.maxScore || 'N/A'}`
                }
              </p>
            </div>
            
            <div className="grid gap-2">
              <label htmlFor="feedback" className="text-sm font-medium">Feedback</label>
              <Textarea
                id="feedback"
                placeholder="Provide feedback to the student..."
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
                className="min-h-[100px]"
              />
            </div>
          </div>
          
          <DialogFooter>
            <Button variant="outline" onClick={() => setGradeDialogOpen(false)}>Cancel</Button>
            <Button 
              onClick={handleSubmitGrade}
              disabled={manualGradeMutation.isPending}
            >
              {manualGradeMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                'Save Grade'
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
