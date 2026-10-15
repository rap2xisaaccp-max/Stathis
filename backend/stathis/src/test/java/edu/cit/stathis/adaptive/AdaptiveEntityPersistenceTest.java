package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.entity.ExerciseMastery;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.entity.LearningProfileHistory;
import edu.cit.stathis.adaptive.entity.StudentLearningProfile;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.repository.ExerciseMasteryRepository;
import edu.cit.stathis.adaptive.repository.FeedbackInterventionRepository;
import edu.cit.stathis.adaptive.repository.FeedbackResponseRepository;
import edu.cit.stathis.adaptive.repository.LearningProfileHistoryRepository;
import edu.cit.stathis.adaptive.repository.StudentLearningProfileRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Phase 1 data-model contract tests.
 *
 * <p>Full JPA round-trips against H2 are skipped because adaptive JSON columns use PostgreSQL
 * {@code jsonb} (same pattern as existing LessonTemplate/QuizTemplate). These tests lock the
 * entity/table naming, required fields, and repository query methods that Phase 2 APIs will use.
 */
class AdaptiveEntityPersistenceTest {

  @Test
  void feedbackInterventionEntityMapsClosedLoopFields() {
    FeedbackIntervention entity =
        FeedbackIntervention.builder()
            .physicalId("FI-1")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .taskId("TASK-1")
            .classroomId("CLASS-1")
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.CHEST_UP)
            .modality(FeedbackModality.VERBAL_TEXT)
            .messageCode("CHEST_UP")
            .messageText("Keep chest up.")
            .deliveredAt(OffsetDateTime.now())
            .baselineSeverity(0.7)
            .policySource(PolicySource.EXPLOIT)
            .experimentArm("ADAPTIVE")
            .build();

    assertEquals("feedback_intervention", entity.getClass().getAnnotation(Table.class).name());
    assertTrue(entity.getClass().isAnnotationPresent(Entity.class));
    assertEquals(FormErrorCode.CHEST_UP, entity.getErrorCode());
    assertEquals(0.7, entity.getBaselineSeverity(), 1e-9);
    assertNotNull(entity.getPhysicalId());
  }

  @Test
  void feedbackResponseCapturesDeltaAndSuccess() {
    FeedbackResponse entity =
        FeedbackResponse.builder()
            .physicalId("FR-1")
            .studentId("STUDENT-1")
            .interventionPhysicalId("FI-1")
            .windowEndAt(OffsetDateTime.now())
            .postSeverity(0.3)
            .delta(0.4)
            .repsInWindow(2)
            .success(true)
            .confoundersJson(Map.of("visibilityOk", true))
            .build();

    assertEquals("feedback_response", entity.getClass().getAnnotation(Table.class).name());
    assertTrue(entity.isSuccess());
    assertEquals("FI-1", entity.getInterventionPhysicalId());
    assertEquals(0.4, entity.getDelta(), 1e-9);
  }

  @Test
  void studentLearningProfileStoresEvidenceMap() {
    Map<String, Object> effectiveness = new HashMap<>();
    effectiveness.put(
        FeedbackModality.VISUAL_HIGHLIGHT.name(),
        Map.of("meanDelta", 0.3, "n", 4, "successRate", 0.75));

    StudentLearningProfile entity =
        StudentLearningProfile.builder()
            .physicalId("SLP-1")
            .studentId("STUDENT-1")
            .preferredModality(FeedbackModality.VISUAL_HIGHLIGHT)
            .modalityEffectivenessJson(effectiveness)
            .learningRateEstimate(0.22)
            .consistencyScore(0.7)
            .fatigueSensitivity(0.1)
            .totalInterventions(4)
            .totalSuccessfulInterventions(3)
            .build();

    assertEquals("student_learning_profile", entity.getClass().getAnnotation(Table.class).name());
    assertEquals(FeedbackModality.VISUAL_HIGHLIGHT, entity.getPreferredModality());
    assertEquals(4, entity.getTotalInterventions());
    assertTrue(entity.getModalityEffectivenessJson().containsKey("VISUAL_HIGHLIGHT"));
  }

  @Test
  void exerciseMasteryIsPerStudentAndExercise() {
    ExerciseMastery entity =
        ExerciseMastery.builder()
            .physicalId("EM-1")
            .studentId("STUDENT-1")
            .exerciseType("SQUAT")
            .masteryLevel(0.55)
            .commonErrorsJson(Map.of("KNEES_IN", 5))
            .sessionsCount(3)
            .medianTimeToCorrectionMs(9000L)
            .recommendedDifficulty("INTERMEDIATE")
            .lastSessionAt(OffsetDateTime.now())
            .build();

    assertEquals("exercise_mastery", entity.getClass().getAnnotation(Table.class).name());
    assertEquals("SQUAT", entity.getExerciseType());
    assertEquals(0.55, entity.getMasteryLevel(), 1e-9);
  }

  @Test
  void learningProfileHistoryStoresSnapshot() {
    LearningProfileHistory entity =
        LearningProfileHistory.builder()
            .physicalId("LPH-1")
            .studentId("STUDENT-1")
            .snapshotJson(Map.of("preferredModality", "VERBAL_TEXT", "totalInterventions", 5))
            .reason("every-5-interventions")
            .build();

    assertEquals("learning_profile_history", entity.getClass().getAnnotation(Table.class).name());
    assertEquals("every-5-interventions", entity.getReason());
    assertNotNull(entity.getSnapshotJson());
  }

  @Test
  void repositoriesExposePhase2QueryMethods() throws Exception {
    assertTrue(JpaRepository.class.isAssignableFrom(FeedbackInterventionRepository.class));
    assertTrue(JpaRepository.class.isAssignableFrom(FeedbackResponseRepository.class));
    assertTrue(JpaRepository.class.isAssignableFrom(StudentLearningProfileRepository.class));
    assertTrue(JpaRepository.class.isAssignableFrom(ExerciseMasteryRepository.class));
    assertTrue(JpaRepository.class.isAssignableFrom(LearningProfileHistoryRepository.class));

    assertMethod(
        FeedbackInterventionRepository.class,
        "findByPhysicalId",
        Optional.class,
        String.class);
    assertMethod(
        FeedbackInterventionRepository.class,
        "findByStudentIdOrderByDeliveredAtDesc",
        java.util.List.class,
        String.class);
    assertMethod(
        FeedbackResponseRepository.class,
        "findByInterventionPhysicalId",
        Optional.class,
        String.class);
    assertMethod(
        StudentLearningProfileRepository.class, "findByStudentId", Optional.class, String.class);
    assertMethod(
        ExerciseMasteryRepository.class,
        "findByStudentIdAndExerciseType",
        Optional.class,
        String.class,
        String.class);
    assertMethod(
        LearningProfileHistoryRepository.class,
        "findByStudentIdOrderByCreatedAtDesc",
        java.util.List.class,
        String.class);
  }

  @Test
  void formErrorCodeMapsExistingRuleFlags() {
    assertEquals(FormErrorCode.CHEST_UP, FormErrorCode.fromFlag("chest_up"));
    assertEquals(FormErrorCode.DEPTH_LOW, FormErrorCode.fromFlag("depth_low"));
    assertEquals(FormErrorCode.KNEES_IN, FormErrorCode.fromFlag("knees_in"));
    assertEquals(FormErrorCode.UNKNOWN, FormErrorCode.fromFlag("not_a_real_flag"));
  }

  @Test
  void entityIdsAreUuidTypedForJpa() throws Exception {
    assertEquals(UUID.class, FeedbackIntervention.class.getDeclaredField("id").getType());
    assertEquals(UUID.class, FeedbackResponse.class.getDeclaredField("id").getType());
    assertEquals(UUID.class, StudentLearningProfile.class.getDeclaredField("id").getType());
    assertEquals(UUID.class, ExerciseMastery.class.getDeclaredField("id").getType());
    assertEquals(UUID.class, LearningProfileHistory.class.getDeclaredField("id").getType());
  }

  private static void assertMethod(
      Class<?> type, String name, Class<?> returnType, Class<?>... params) throws Exception {
    Method method = type.getMethod(name, params);
    assertEquals(returnType, method.getReturnType(), name);
  }
}
