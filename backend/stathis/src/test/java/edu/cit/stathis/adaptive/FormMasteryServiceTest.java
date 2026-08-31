package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.dto.FormMasteryDTO;
import edu.cit.stathis.adaptive.service.FormMasteryService;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import edu.cit.stathis.task.entity.ScoreAttempt;
import edu.cit.stathis.task.enums.ExerciseType;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.repository.ScoreAttemptRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FormMasteryServiceTest {

  @Mock private ScoreAttemptRepository scoreAttemptRepository;
  @Mock private ExerciseTemplateRepository exerciseTemplateRepository;
  @InjectMocks private FormMasteryService service;

  @Test
  void averagesRetriesForOneExerciseAndIsolatesOthers() {
    ExerciseTemplate squats = template("TPL-SQUAT", ExerciseType.SQUATS);
    ExerciseTemplate push = template("TPL-PUSH", ExerciseType.PUSH_UP);
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(
            List.of(
                attempt("A1", "TPL-SQUAT", 10, 40.0, "2026-08-01T00:00:00Z"),
                attempt("A2", "TPL-SQUAT", 10, 60.0, "2026-08-02T00:00:00Z"),
                attempt("A3", "TPL-PUSH", 8, 100.0, "2026-08-03T00:00:00Z")));
    when(exerciseTemplateRepository.findByPhysicalIdIn(any()))
        .thenReturn(List.of(squats, push));

    List<FormMasteryDTO> rows = service.listForStudent("STUDENT-1");

    assertEquals(2, rows.size());
    FormMasteryDTO squatRow =
        rows.stream().filter(r -> "SQUATS".equals(r.getExerciseType())).findFirst().orElseThrow();
    FormMasteryDTO pushRow =
        rows.stream().filter(r -> "PUSH_UP".equals(r.getExerciseType())).findFirst().orElseThrow();
    assertEquals(0.50, squatRow.getFormMasteryLevel(), 1e-9);
    assertEquals(50.0, squatRow.getFormMasteryPercent(), 1e-9);
    assertEquals(2, squatRow.getEligibleAttemptCount());
    assertEquals(1.0, pushRow.getFormMasteryLevel(), 1e-9);
    assertEquals(1, pushRow.getEligibleAttemptCount());
  }

  @Test
  void normalizesExerciseAliasesOntoOneBucket() {
    ExerciseTemplate squats = template("TPL-SQUAT", ExerciseType.SQUATS);
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(
            List.of(
                attempt("A1", "TPL-SQUAT", 10, 40.0, "2026-08-01T00:00:00Z"),
                attempt("A2", "TPL-SQUAT", 10, 80.0, "2026-08-02T00:00:00Z")));
    when(exerciseTemplateRepository.findByPhysicalIdIn(any()))
        .thenReturn(List.of(squats));

    List<FormMasteryDTO> rows = service.listForStudent("STUDENT-1");
    assertEquals(1, rows.size());
    assertEquals("SQUATS", rows.get(0).getExerciseType());
    assertEquals(0.60, rows.get(0).getFormMasteryLevel(), 1e-9);
  }

  @Test
  void excludesQuizEmptyAndUnmappedAttempts() {
    ExerciseTemplate squats = template("TPL-SQUAT", ExerciseType.SQUATS);
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(
            List.of(
                attempt("EMPTY", "TPL-SQUAT", 0, 0.0, "2026-08-01T00:00:00Z"),
                ScoreAttempt.builder()
                    .physicalId("QUIZ")
                    .studentId("STUDENT-1")
                    .taskId("TASK-Q")
                    .scorePhysicalId("SCORE-Q")
                    .exerciseTemplateId("TPL-SQUAT")
                    .quizTemplateId("QUIZ-1")
                    .reps(10)
                    .accuracy(90.0)
                    .completedAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"))
                    .build(),
                attempt("MISSING-TPL", "TPL-GONE", 10, 80.0, "2026-08-01T00:00:00Z")));
    when(exerciseTemplateRepository.findByPhysicalIdIn(any()))
        .thenReturn(List.of(squats));

    assertTrue(service.listForStudent("STUDENT-1").isEmpty());
  }

  @Test
  void noDataWhenNoClassroomAttempts() {
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(List.of());
    assertTrue(service.listForStudent("STUDENT-1").isEmpty());
  }

  @Test
  void excludesZeroRepsWithPositiveAccuracyFromAverage() {
    ExerciseTemplate squats = template("TPL-SQUAT", ExerciseType.SQUATS);
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(
            List.of(
                attempt("ZERO-REPS", "TPL-SQUAT", 0, 80.0, "2026-08-01T00:00:00Z"),
                attempt("COUNTED", "TPL-SQUAT", 10, 50.0, "2026-08-02T00:00:00Z")));
    when(exerciseTemplateRepository.findByPhysicalIdIn(any())).thenReturn(List.of(squats));

    List<FormMasteryDTO> rows = service.listForStudent("STUDENT-1");
    assertEquals(1, rows.size());
    assertEquals(0.50, rows.get(0).getFormMasteryLevel(), 1e-9);
    assertEquals(1, rows.get(0).getEligibleAttemptCount());
  }

  @Test
  void zeroRepsOnlyIsNoDataNotZeroPercent() {
    ExerciseTemplate squats = template("TPL-SQUAT", ExerciseType.SQUATS);
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(List.of(attempt("ZERO-REPS", "TPL-SQUAT", 0, 75.0, "2026-08-01T00:00:00Z")));
    when(exerciseTemplateRepository.findByPhysicalIdIn(any())).thenReturn(List.of(squats));

    assertTrue(service.listForStudent("STUDENT-1").isEmpty());
  }

  @Test
  void measuredZeroIsReturnedWhenRepsExist() {
    ExerciseTemplate squats = template("TPL-SQUAT", ExerciseType.SQUATS);
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(List.of(attempt("A1", "TPL-SQUAT", 12, 0.0, "2026-08-01T00:00:00Z")));
    when(exerciseTemplateRepository.findByPhysicalIdIn(any()))
        .thenReturn(List.of(squats));

    List<FormMasteryDTO> rows = service.listForStudent("STUDENT-1");
    assertEquals(1, rows.size());
    assertEquals(0.0, rows.get(0).getFormMasteryLevel(), 1e-9);
    assertEquals(0.0, rows.get(0).getFormMasteryPercent(), 1e-9);
  }

  @Test
  void coachingFrequencyRowsAndInterventionsAreNotInputs() {
    // FormMasteryService has no ExerciseMastery / FeedbackIntervention collaborators.
    // An empty score_attempt list stays empty even if those stores would be noisy.
    when(scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull("STUDENT-1"))
        .thenReturn(List.of());
    assertTrue(service.listForStudent("STUDENT-1").isEmpty());
  }

  private static ExerciseTemplate template(String physicalId, ExerciseType type) {
    return ExerciseTemplate.builder().physicalId(physicalId).exerciseType(type).build();
  }

  private static ScoreAttempt attempt(
      String physicalId, String templateId, int reps, double accuracy, String completedAt) {
    return ScoreAttempt.builder()
        .physicalId(physicalId)
        .studentId("STUDENT-1")
        .taskId("TASK-1")
        .scorePhysicalId("SCORE-1")
        .exerciseTemplateId(templateId)
        .reps(reps)
        .accuracy(accuracy)
        .completedAt(OffsetDateTime.parse(completedAt))
        .build();
  }
}
