package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.task.dto.ExerciseResultSubmissionDTO;
import edu.cit.stathis.task.dto.TaskBodyDTO;
import edu.cit.stathis.task.dto.TaskExerciseInputDTO;
import edu.cit.stathis.task.dto.TaskExerciseProgressDTO;
import edu.cit.stathis.task.dto.TaskProgressDTO;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.ScoreAttempt;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskCompletion;
import edu.cit.stathis.task.entity.TaskExercise;
import edu.cit.stathis.task.enums.ExerciseType;
import edu.cit.stathis.task.dto.StudentTaskResponseDTO;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.repository.QuizTemplateRepository;
import edu.cit.stathis.task.repository.ScoreAttemptRepository;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.repository.TaskExerciseRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import edu.cit.stathis.task.service.ExerciseCalorieService;
import edu.cit.stathis.task.service.ExerciseProgressService;
import edu.cit.stathis.task.service.StudentTaskService;
import edu.cit.stathis.task.service.TaskExerciseService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TaskExerciseFlowTest {

  @Mock private TaskExerciseRepository taskExerciseRepository;
  @Mock private ExerciseTemplateRepository exerciseTemplateRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private ScoreRepository scoreRepository;
  @Mock private ScoreAttemptRepository scoreAttemptRepository;
  @Mock private TaskCompletionRepository taskCompletionRepository;
  @Mock private ExerciseCalorieService exerciseCalorieService;
  @Mock private ExerciseProgressService exerciseProgressService;
  @Mock private QuizTemplateRepository quizTemplateRepository;

  private TaskExerciseService taskExerciseService;
  private StudentTaskService studentTaskService;
  private final List<Score> scores = new ArrayList<>();
  private final List<ScoreAttempt> attempts = new ArrayList<>();
  private final List<TaskCompletion> completions = new ArrayList<>();
  private final List<TaskExercise> rows = new ArrayList<>();

  @BeforeEach
  void setUp() {
    taskExerciseService = new TaskExerciseService(taskExerciseRepository, exerciseTemplateRepository);
    studentTaskService = new StudentTaskService();
    ReflectionTestUtils.setField(studentTaskService, "taskRepository", taskRepository);
    ReflectionTestUtils.setField(studentTaskService, "scoreRepository", scoreRepository);
    ReflectionTestUtils.setField(studentTaskService, "scoreAttemptRepository", scoreAttemptRepository);
    ReflectionTestUtils.setField(studentTaskService, "taskCompletionRepository", taskCompletionRepository);
    ReflectionTestUtils.setField(studentTaskService, "exerciseTemplateRepository", exerciseTemplateRepository);
    ReflectionTestUtils.setField(studentTaskService, "exerciseCalorieService", exerciseCalorieService);
    ReflectionTestUtils.setField(studentTaskService, "exerciseProgressService", exerciseProgressService);
    ReflectionTestUtils.setField(studentTaskService, "taskExerciseService", taskExerciseService);
    ReflectionTestUtils.setField(studentTaskService, "quizTemplateRepository", quizTemplateRepository);
  }

  @Test
  void legacyExerciseAttemptsStayHiddenUntilEveryExerciseQualifies() {
    assertEquals(1, ExerciseCompletionPolicy.legacyExerciseAttempts(1, 1, 1));
    assertEquals(0, ExerciseCompletionPolicy.legacyExerciseAttempts(3, 1, 2));
    assertEquals(2, ExerciseCompletionPolicy.legacyExerciseAttempts(3, 3, 2));
    assertTrue(TaskComponentCompletion.allAssignedExercisesComplete(3, 3));
    assertFalse(TaskComponentCompletion.allAssignedExercisesComplete(3, 2));
  }

  @Test
  void oldRequestBecomesOneExerciseAndNewRequestWins() {
    TaskBodyDTO oldBody = new TaskBodyDTO();
    oldBody.setExerciseTemplateId("EXERCISE-PUSH");
    assertEquals(List.of("EXERCISE-PUSH"), taskExerciseService.resolveRequestedTemplateIds(oldBody));

    TaskExerciseInputDTO first = new TaskExerciseInputDTO();
    first.setExerciseTemplateId("EXERCISE-SQUAT");
    first.setSortOrder(2);
    TaskExerciseInputDTO second = new TaskExerciseInputDTO();
    second.setExerciseTemplateId("EXERCISE-PUSH");
    second.setSortOrder(1);
    TaskBodyDTO both = new TaskBodyDTO();
    both.setExerciseTemplateId("EXERCISE-OTHER");
    both.setExercises(List.of(first, second));
    assertEquals(
        List.of("EXERCISE-PUSH", "EXERCISE-SQUAT"),
        taskExerciseService.resolveRequestedTemplateIds(both));

    TaskBodyDTO emptyList = new TaskBodyDTO();
    emptyList.setExerciseTemplateId("EXERCISE-PUSH");
    emptyList.setExercises(List.of());
    assertEquals(List.of(), taskExerciseService.resolveRequestedTemplateIds(emptyList));
  }

  @Test
  void duplicateTemplateAndTypeAreRejected() {
    stubTemplates();
    Task task = started(false);

    assertThrows(
        ResponseStatusException.class,
        () -> taskExerciseService.replace(task, List.of("EXERCISE-PUSH", "EXERCISE-PUSH")));
    assertThrows(
        ResponseStatusException.class,
        () -> taskExerciseService.replace(task, List.of("EXERCISE-PUSH", "EXERCISE-PUSH-2")));
  }

  @Test
  void missingTemplateIsRejected() {
    when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-MISSING")).thenReturn(Optional.empty());
    Task task = started(false);
    assertThrows(
        EntityNotFoundException.class,
        () -> taskExerciseService.replace(task, List.of("EXERCISE-MISSING")));
  }

  @Test
  void editingListAfterStartIsRejectedAndMirrorStaysFirst() {
    Task task = started(true);
    task.setExerciseTemplateId("EXERCISE-PUSH");
    rows.add(row("EXERCISE-PUSH", 1));
    rows.add(row("EXERCISE-SQUAT", 2));
    when(taskExerciseRepository.findByTaskIdOrderBySortOrderAsc("TASK-1")).thenReturn(rows);

    TaskBodyDTO changed = body("EXERCISE-SQUAT", "EXERCISE-PUSH");
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> taskExerciseService.applyRequestedExercises(task, changed));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

    taskExerciseService.applyRequestedExercises(task, body("EXERCISE-PUSH", "EXERCISE-SQUAT"));
    assertEquals("EXERCISE-PUSH", task.getExerciseTemplateId());
    verify(taskExerciseRepository, never()).deleteAll(any());
  }

  @Test
  void oneExerciseTaskMatchesLegacyCompletion() {
    wireThree(List.of(row("EXERCISE-PUSH", 1)));
    Task task = openTask(null, null);
    when(taskRepository.findByPhysicalId("TASK-1")).thenReturn(Optional.of(task));
    when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-PUSH"))
        .thenReturn(Optional.of(template("EXERCISE-PUSH")));

    TaskProgressDTO before = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(0, before.getExercisesCompleted());
    assertEquals(1, before.getExercisesRequired());
    assertFalse(before.isExerciseCompleted());
    assertFalse(before.isCompleted());

    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(10));
    TaskProgressDTO after = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(1, after.getExercisesCompleted());
    assertTrue(after.isExerciseCompleted());
    assertTrue(after.isCompleted());
    assertEquals(1, after.getExerciseAttempts());
    assertEquals("EXERCISE-PUSH", after.getExercises().get(0).getExerciseTemplateId());

    StudentTaskResponseDTO student = studentTaskService.getStudentTask("TASK-1", "STUDENT-1");
    assertEquals("EXERCISE-PUSH", student.getExerciseTemplateId());
    assertEquals(1, student.getExercisesRequired());
    assertEquals(1, student.getExercisesCompleted());
    assertTrue(student.isCompleted());
    assertEquals(100, student.getScore().getScore());
    assertNotNull(student.getExerciseTemplate());
  }

  @Test
  void threeExercisesCompleteOneAtATimeAndRetryDoesNotAdvanceTheCount() {
    wireThree(List.of(row("EXERCISE-PUSH", 1), row("EXERCISE-SQUAT", 2), row("EXERCISE-GLUTE", 3)));
    Task task = openTask(null, null);
    when(taskRepository.findByPhysicalId("TASK-1")).thenReturn(Optional.of(task));
    when(exerciseTemplateRepository.findByPhysicalId(anyString()))
        .thenAnswer(invocation -> Optional.of(template(invocation.getArgument(0))));

    TaskProgressDTO start = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(0, start.getExercisesCompleted());
    assertEquals(3, start.getExercisesRequired());
    assertEquals(0, start.getExerciseAttempts());
    assertFalse(start.isCompleted());
    assertEquals("EXERCISE-PUSH", start.getExercises().get(0).getExerciseTemplateId());

    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(10));
    TaskProgressDTO one = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(1, one.getExercisesCompleted());
    assertEquals(0, one.getExerciseAttempts());
    assertFalse(one.isExerciseCompleted());
    assertFalse(one.isCompleted());
    assertEquals(10, one.getExercises().get(0).getLatestValidReps());
    assertEquals(0, one.getExercises().get(1).getAttempts());

    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(8));
    TaskProgressDTO retry = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(1, retry.getExercisesCompleted());
    assertEquals(2, retry.getExercises().get(0).getAttempts());
    assertEquals(0, retry.getExercises().get(1).getAttempts());
    assertEquals(0, retry.getExercises().get(2).getAttempts());
    assertEquals(1, scores.size());
    assertEquals(2, attempts.stream().filter(a -> "EXERCISE-PUSH".equals(a.getExerciseTemplateId())).count());
    assertEquals(0, attempts.stream().filter(a -> "EXERCISE-SQUAT".equals(a.getExerciseTemplateId())).count());
    assertEquals(0, attempts.stream().filter(a -> "EXERCISE-GLUTE".equals(a.getExerciseTemplateId())).count());

    Score push = scores.stream().filter(s -> "EXERCISE-PUSH".equals(s.getExerciseTemplateId())).findFirst().orElseThrow();
    int pushScore = push.getScore();
    int pushAttempts = push.getAttempts();

    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-SQUAT", result(15));
    assertEquals(pushScore, push.getScore());
    assertEquals(pushAttempts, push.getAttempts());
    TaskProgressDTO two = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(2, two.getExercisesCompleted());
    assertFalse(two.isExerciseCompleted());

    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-GLUTE", result(12));
    TaskProgressDTO three = studentTaskService.getTaskProgress("TASK-1", "STUDENT-1");
    assertEquals(3, three.getExercisesCompleted());
    assertTrue(three.isExerciseCompleted());
    assertTrue(three.isCompleted());
    assertTrue(three.getExerciseAttempts() > 0);
    assertEquals("COMPLETED", three.getExercises().get(2).getCompletionStatus());
  }

  @Test
  void lessonAndQuizStillRequiredAlongsideExercises() {
    wireThree(List.of(row("EXERCISE-PUSH", 1), row("EXERCISE-SQUAT", 2)));
    when(exerciseTemplateRepository.findByPhysicalId(anyString()))
        .thenAnswer(invocation -> Optional.of(template(invocation.getArgument(0))));

    Task withLesson = openTask("LESSON-1", null);
    when(taskRepository.findByPhysicalId("TASK-1")).thenReturn(Optional.of(withLesson));
    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(10));
    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-SQUAT", result(15));
    assertFalse(studentTaskService.getTaskProgress("TASK-1", "STUDENT-1").isCompleted());
    studentTaskService.completeLesson("STUDENT-1", "TASK-1", "LESSON-1");
    assertTrue(studentTaskService.getTaskProgress("TASK-1", "STUDENT-1").isCompleted());

    scores.clear();
    attempts.clear();
    completions.clear();
    Task withQuiz = openTask(null, "QUIZ-1");
    when(taskRepository.findByPhysicalId("TASK-1")).thenReturn(Optional.of(withQuiz));
    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(10));
    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-SQUAT", result(15));
    assertFalse(studentTaskService.getTaskProgress("TASK-1", "STUDENT-1").isCompleted());
    when(quizTemplateRepository.findByPhysicalId("QUIZ-1")).thenReturn(Optional.empty());
    studentTaskService.submitQuizScore("STUDENT-1", "TASK-1", "QUIZ-1", 4);
    assertTrue(studentTaskService.getTaskProgress("TASK-1", "STUDENT-1").isCompleted());
    assertEquals(2, studentTaskService.getTaskProgress("TASK-1", "STUDENT-1").getExercisesCompleted());
  }

  @Test
  void exerciseNotOnTheTaskIsRejectedAndMaxAttemptsArePerExercise() {
    wireThree(List.of(row("EXERCISE-PUSH", 1)));
    Task task = openTask(null, null);
    task.setMaxAttempts(1);
    when(taskRepository.findByPhysicalId("TASK-1")).thenReturn(Optional.of(task));
    when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-PUSH"))
        .thenReturn(Optional.of(template("EXERCISE-PUSH")));
    when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-SQUAT"))
        .thenReturn(Optional.of(template("EXERCISE-SQUAT")));

    ResponseStatusException missing =
        assertThrows(
            ResponseStatusException.class,
            () -> studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-SQUAT", result(1)));
    assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());

    studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(10));
    ResponseStatusException capped =
        assertThrows(
            ResponseStatusException.class,
            () -> studentTaskService.completeExercise("STUDENT-1", "TASK-1", "EXERCISE-PUSH", result(10)));
    assertEquals(HttpStatus.BAD_REQUEST, capped.getStatusCode());
  }

  @Test
  void readerSynthesizesASingleExerciseWhenNoChildRowsExist() {
    when(taskExerciseRepository.findByTaskIdOrderBySortOrderAsc("TASK-1")).thenReturn(List.of());
    when(exerciseTemplateRepository.findByPhysicalId("EXERCISE-PUSH"))
        .thenReturn(Optional.of(template("EXERCISE-PUSH")));
    Task task = openTask(null, null);
    task.setExerciseTemplateId("EXERCISE-PUSH");
    List<TaskExerciseProgressDTO> rows = taskExerciseService.progressFor(task, List.of());
    assertEquals(1, rows.size());
    assertEquals("EXERCISE-PUSH", rows.get(0).getExerciseTemplateId());
    assertEquals(10, rows.get(0).getGoalReps());
    assertEquals(ExerciseCompletionPolicy.NOT_STARTED, rows.get(0).getCompletionStatus());
  }

  private void wireThree(List<TaskExercise> assigned) {
    when(taskExerciseRepository.findByTaskIdOrderBySortOrderAsc("TASK-1")).thenReturn(assigned);
    when(scoreRepository.findByStudentIdAndTaskId(anyString(), anyString())).thenAnswer(invocation -> List.copyOf(scores));
    when(scoreRepository.findExerciseScore(anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation ->
                scores.stream()
                    .filter(score -> invocation.getArgument(2).equals(score.getExerciseTemplateId()))
                    .findFirst());
    lenient()
        .when(scoreRepository.findQuizScore(anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(scoreRepository.save(any(Score.class)))
        .thenAnswer(
            invocation -> {
              Score saved = invocation.getArgument(0);
              scores.removeIf(
                  score -> {
                    if (saved.getExerciseTemplateId() != null) {
                      return saved.getExerciseTemplateId().equals(score.getExerciseTemplateId());
                    }
                    return score.getExerciseTemplateId() == null
                        && saved.getQuizTemplateId() != null
                        && saved.getQuizTemplateId().equals(score.getQuizTemplateId());
                  });
              scores.add(saved);
              return saved;
            });
    when(scoreAttemptRepository.save(any(ScoreAttempt.class)))
        .thenAnswer(
            invocation -> {
              ScoreAttempt saved = invocation.getArgument(0);
              attempts.add(saved);
              return saved;
            });
    when(taskCompletionRepository.findAllByStudentIdAndTaskId(anyString(), anyString()))
        .thenAnswer(invocation -> new ArrayList<>(completions));
    when(taskCompletionRepository.save(any(TaskCompletion.class)))
        .thenAnswer(
            invocation -> {
              TaskCompletion saved = invocation.getArgument(0);
              completions.clear();
              completions.add(saved);
              return saved;
            });
  }

  private static Task openTask(String lessonId, String quizId) {
    return Task.builder()
        .physicalId("TASK-1")
        .name("Upper/Lower Body Activity")
        .description("Mix")
        .submissionDate(OffsetDateTime.parse("2026-09-01T00:00:00Z"))
        .closingDate(OffsetDateTime.parse("2026-10-01T00:00:00Z"))
        .classroomPhysicalId("ROOM-1")
        .lessonTemplateId(lessonId)
        .quizTemplateId(quizId)
        .maxAttempts(3)
        .isActive(true)
        .isStarted(true)
        .createdAt(OffsetDateTime.parse("2026-09-01T00:00:00Z"))
        .updatedAt(OffsetDateTime.parse("2026-09-01T00:00:00Z"))
        .build();
  }

  private static Task started(boolean started) {
    Task task = openTask(null, null);
    task.setStarted(started);
    return task;
  }

  private static TaskExercise row(String templateId, int order) {
    return TaskExercise.builder()
        .physicalId("TEX-" + order)
        .taskId("TASK-1")
        .exerciseTemplateId(templateId)
        .sortOrder(order)
        .build();
  }

  private void stubTemplates() {
    when(exerciseTemplateRepository.findByPhysicalId(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(templateOrNull(invocation.getArgument(0))));
  }

  private static ExerciseTemplate templateOrNull(String id) {
    return switch (id) {
      case "EXERCISE-PUSH", "EXERCISE-PUSH-2", "EXERCISE-SQUAT", "EXERCISE-GLUTE" -> template(id);
      default -> null;
    };
  }

  private static ExerciseTemplate template(String id) {
    ExerciseType type =
        switch (id) {
          case "EXERCISE-SQUAT" -> ExerciseType.SQUATS;
          case "EXERCISE-GLUTE" -> ExerciseType.GLUTE_BRIDGE;
          default -> ExerciseType.PUSH_UP;
        };
    return ExerciseTemplate.builder()
        .physicalId(id)
        .title(id)
        .exerciseType(type)
        .goalReps(10)
        .goalAccuracy(80)
        .goalTime(600)
        .build();
  }

  private static TaskBodyDTO body(String... ids) {
    TaskBodyDTO dto = new TaskBodyDTO();
    List<TaskExerciseInputDTO> inputs = new ArrayList<>();
    int order = 1;
    for (String id : ids) {
      TaskExerciseInputDTO input = new TaskExerciseInputDTO();
      input.setExerciseTemplateId(id);
      input.setSortOrder(order++);
      inputs.add(input);
    }
    dto.setExercises(inputs);
    return dto;
  }

  private static ExerciseResultSubmissionDTO result(int reps) {
    return ExerciseResultSubmissionDTO.builder()
        .reps(reps)
        .attemptedReps(reps)
        .accuracy(90)
        .timeTaken(1000)
        .goalReps(10)
        .caloriesBurned(1.0)
        .exerciseType("PUSH_UP")
        .classroomId("ROOM-1")
        .build();
  }
}
