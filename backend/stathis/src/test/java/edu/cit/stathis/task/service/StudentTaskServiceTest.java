package edu.cit.stathis.task.service;

import edu.cit.stathis.task.dto.ExerciseResultSubmissionDTO;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskCompletion;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.repository.LessonTemplateRepository;
import edu.cit.stathis.task.repository.QuizTemplateRepository;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import edu.cit.stathis.classroom.service.ClassroomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentTaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private TaskCompletionRepository taskCompletionRepository;
    @Mock
    private LessonTemplateRepository lessonTemplateRepository;
    @Mock
    private QuizTemplateRepository quizTemplateRepository;
    @Mock
    private ExerciseTemplateRepository exerciseTemplateRepository;
    @Mock
    private ClassroomService classroomService;

    @InjectMocks
    private StudentTaskService studentTaskService;

    @Test
    void submitExerciseResultPersistsRepetitionsCalculatesScoreAndCompletesTask() {
        Task task = Task.builder()
            .physicalId("TASK-1")
            .exerciseTemplateId("EXERCISE-1")
            .maxAttempts(3)
            .build();
        ExerciseTemplate template = ExerciseTemplate.builder()
            .physicalId("EXERCISE-1")
            .goalReps(10)
            .build();
        ExerciseResultSubmissionDTO submission = ExerciseResultSubmissionDTO.builder()
            .reps(6)
            .accuracy(82.5)
            .timeTaken(42_000L)
            .build();

        when(taskRepository.findByPhysicalId("TASK-1")).thenReturn(Optional.of(task));
        when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-1")).thenReturn(Optional.of(template));
        when(scoreRepository.findExerciseScore("STUDENT-1", "TASK-1", "EXERCISE-1")).thenReturn(Optional.empty());
        when(taskCompletionRepository.findByStudentIdAndTaskId("STUDENT-1", "TASK-1")).thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskCompletionRepository.save(any(TaskCompletion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Score result = studentTaskService.submitExerciseResult("STUDENT-1", "TASK-1", "EXERCISE-1", submission);

        assertEquals(6, result.getReps());
        assertEquals(10, result.getGoalReps());
        assertEquals(60, result.getScore());
        assertEquals(100, result.getMaxScore());
        assertEquals(1, result.getAttempts());
        assertTrue(result.isCompleted());

        ArgumentCaptor<TaskCompletion> completionCaptor = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionRepository).save(completionCaptor.capture());
        assertTrue(completionCaptor.getValue().isExerciseCompleted());
        assertTrue(completionCaptor.getValue().isFullyCompleted());
    }

    @Test
    void submitExerciseResultCapsScoreAtOneHundredForRepsAboveGoal() {
        Task task = Task.builder()
            .physicalId("TASK-2")
            .exerciseTemplateId("EXERCISE-2")
            .maxAttempts(3)
            .build();
        ExerciseTemplate template = ExerciseTemplate.builder()
            .physicalId("EXERCISE-2")
            .goalReps(10)
            .build();
        ExerciseResultSubmissionDTO submission = ExerciseResultSubmissionDTO.builder()
            .reps(15)
            .accuracy(90.0)
            .timeTaken(30_000L)
            .build();

        when(taskRepository.findByPhysicalId("TASK-2")).thenReturn(Optional.of(task));
        when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-2")).thenReturn(Optional.of(template));
        when(scoreRepository.findExerciseScore("STUDENT-2", "TASK-2", "EXERCISE-2")).thenReturn(Optional.empty());
        when(taskCompletionRepository.findByStudentIdAndTaskId("STUDENT-2", "TASK-2")).thenReturn(Optional.empty());
        when(scoreRepository.save(any(Score.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskCompletionRepository.save(any(TaskCompletion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Score result = studentTaskService.submitExerciseResult("STUDENT-2", "TASK-2", "EXERCISE-2", submission);

        assertEquals(100, result.getScore());
    }
}
