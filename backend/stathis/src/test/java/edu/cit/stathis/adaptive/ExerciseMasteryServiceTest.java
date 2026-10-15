package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.dto.DifficultyRecommendationDTO;
import edu.cit.stathis.adaptive.entity.ExerciseMastery;
import edu.cit.stathis.adaptive.repository.ExerciseMasteryRepository;
import edu.cit.stathis.adaptive.service.ExerciseMasteryService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExerciseMasteryServiceTest {

  @Mock private ExerciseMasteryRepository masteryRepository;
  @InjectMocks private ExerciseMasteryService service;

  @Test
  void insufficientFormEvidenceWhenSessionsButNearZeroMastery() {
    assertTrue(ExerciseMasteryService.isInsufficientFormEvidence(3, 0.0));
    assertTrue(ExerciseMasteryService.isInsufficientFormEvidence(1, 0.049));
    assertFalse(ExerciseMasteryService.isInsufficientFormEvidence(0, 0.0));
    assertFalse(ExerciseMasteryService.isInsufficientFormEvidence(2, 0.05));
  }

  @Test
  void difficultyRecommendationWithholdsBeginnerWhenFormEvidenceMissing() {
    ExerciseMastery mastery =
        ExerciseMastery.builder()
            .physicalId("EM-1")
            .studentId("STUDENT-1")
            .exerciseType("SQUATS")
            .masteryLevel(0.0)
            .sessionsCount(3)
            .recommendedDifficulty("BEGINNER")
            .commonErrorsJson(java.util.Map.of())
            .build();

    DifficultyRecommendationDTO dto = service.toDifficultyRecommendation(mastery);

    assertNull(dto.getRecommendedDifficulty());
    assertNull(dto.getRecommendedGoalReps());
    assertTrue(dto.getRationale().toLowerCase().contains("insufficient form-correction"));
  }

  @Test
  void recordSessionNormalizesExerciseType() {
    when(masteryRepository.findByStudentIdAndExerciseType("STUDENT-1", "SQUATS"))
        .thenReturn(Optional.empty());
    when(masteryRepository.save(any(ExerciseMastery.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recordSession("STUDENT-1", "SQUAT");

    ArgumentCaptor<ExerciseMastery> captor = ArgumentCaptor.forClass(ExerciseMastery.class);
    verify(masteryRepository, atLeastOnce()).save(captor.capture());
    ExerciseMastery last = captor.getValue();
    assertEquals("SQUATS", last.getExerciseType());
    assertEquals(1, last.getSessionsCount());
  }

  @Test
  void getOrCreateUsesNormalizedExerciseKey() {
    when(masteryRepository.findByStudentIdAndExerciseType(eq("STUDENT-1"), eq("SQUATS")))
        .thenReturn(Optional.empty());
    when(masteryRepository.save(any(ExerciseMastery.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ExerciseMastery created = service.getOrCreate("STUDENT-1", "squat");
    assertEquals("SQUATS", created.getExerciseType());
  }
}
