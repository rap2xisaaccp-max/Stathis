package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.entity.StudentLearningProfile;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.repository.ExerciseMasteryRepository;
import edu.cit.stathis.adaptive.repository.LearningProfileHistoryRepository;
import edu.cit.stathis.adaptive.repository.StudentLearningProfileRepository;
import edu.cit.stathis.adaptive.service.ProfileEffectivenessMath;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 3 unit tests: EWMA / Bayesian profile update math and applyResponse wiring.
 */
@ExtendWith(MockitoExtension.class)
class StudentLearningProfileUpdateMathTest {

  @Mock private StudentLearningProfileRepository profileRepository;
  @Mock private LearningProfileHistoryRepository historyRepository;
  @Mock private ExerciseMasteryRepository masteryRepository;

  @InjectMocks private StudentLearningProfileService profileService;

  @Test
  void ewmaBlendsPriorAndObservation() {
    double result = ProfileEffectivenessMath.ewma(0.0, 1.0);
    assertEquals(ProfileEffectivenessMath.EWMA_ALPHA, result, 1e-9);
    assertEquals(0.37, ProfileEffectivenessMath.ewma(0.1, 1.0), 1e-9);
  }

  @Test
  void warmUpUsesRunningMeanThenSwitchesToEwma() {
    Map<String, Object> map = new HashMap<>();
    ProfileEffectivenessMath.updateBucket(map, "VERBAL_TEXT", 0.2, true);
    ProfileEffectivenessMath.updateBucket(map, "VERBAL_TEXT", 0.4, true);

    @SuppressWarnings("unchecked")
    Map<String, Object> bucket = (Map<String, Object>) map.get("VERBAL_TEXT");
    assertEquals(2, ((Number) bucket.get("n")).intValue());
    assertEquals(0.3, ((Number) bucket.get("meanDelta")).doubleValue(), 1e-9);

    // Fill to warm-up boundary then apply a large delta that EWMA should damp
    for (int i = 0; i < 3; i++) {
      ProfileEffectivenessMath.updateBucket(map, "VERBAL_TEXT", 0.3, true);
    }
    assertEquals(5, ((Number) ((Map<?, ?>) map.get("VERBAL_TEXT")).get("n")).intValue());

    ProfileEffectivenessMath.updateBucket(map, "VERBAL_TEXT", 1.0, true);
    @SuppressWarnings("unchecked")
    Map<String, Object> after = (Map<String, Object>) map.get("VERBAL_TEXT");
    double mean = ((Number) after.get("meanDelta")).doubleValue();
    // EWMA from ~0.3 toward 1.0 with alpha 0.3 => 0.7*0.3 + 0.3*1.0 = 0.51
    assertEquals(0.51, mean, 0.05);
    assertTrue(mean < 1.0);
  }

  @Test
  void bayesianMeanShrinksTowardZeroWithFewSamples() {
    Map<String, Object> map = new HashMap<>();
    ProfileEffectivenessMath.updateBucket(map, "VISUAL_HIGHLIGHT", 1.0, true);

    @SuppressWarnings("unchecked")
    Map<String, Object> bucket = (Map<String, Object>) map.get("VISUAL_HIGHLIGHT");
    double raw = ((Number) bucket.get("meanDelta")).doubleValue();
    double bayes = ((Number) bucket.get("bayesianMeanDelta")).doubleValue();
    assertEquals(1.0, raw, 1e-9);
    assertTrue(bayes < raw);
    assertEquals(
        1.0 / (ProfileEffectivenessMath.BAYES_PRIOR_STRENGTH + 1.0), bayes, 1e-9);
  }

  @Test
  void compositeKeyIsExerciseErrorModality() {
    assertEquals(
        "SQUAT|CHEST_UP|VERBAL_TEXT",
        ProfileEffectivenessMath.compositeKey("SQUAT", "CHEST_UP", "VERBAL_TEXT"));
  }

  @Test
  void preferredModalityChoosesHigherEvidenceScore() {
    Map<String, Object> effectiveness = new HashMap<>();
    ProfileEffectivenessMath.updateBucket(effectiveness, "VERBAL_TEXT", 0.05, false);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VERBAL_TEXT", 0.05, false);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.5, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.45, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.4, true);

    assertEquals(
        FeedbackModality.VISUAL_HIGHLIGHT,
        ProfileEffectivenessMath.derivePreferredModality(effectiveness));
  }

  @Test
  void preferredModalityDefaultsWhenNoEvidence() {
    assertEquals(
        FeedbackModality.VERBAL_TEXT,
        ProfileEffectivenessMath.derivePreferredModality(new HashMap<>()));
  }

  @Test
  void applyResponseUpdatesModalityAndCompositeBuckets() {
    StudentLearningProfile existing =
        StudentLearningProfile.builder()
            .physicalId("SLP-1")
            .studentId("STUDENT-1")
            .preferredModality(FeedbackModality.VERBAL_TEXT)
            .modalityEffectivenessJson(new HashMap<>())
            .learningRateEstimate(0.0)
            .consistencyScore(0.5)
            .totalInterventions(0)
            .totalSuccessfulInterventions(0)
            .build();
    when(profileRepository.findByStudentId("STUDENT-1")).thenReturn(Optional.of(existing));
    when(profileRepository.save(any(StudentLearningProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FeedbackIntervention intervention =
        FeedbackIntervention.builder()
            .physicalId("FI-1")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.CHEST_UP)
            .modality(FeedbackModality.VISUAL_HIGHLIGHT)
            .deliveredAt(OffsetDateTime.now())
            .baselineSeverity(0.8)
            .policySource(PolicySource.EXPLORE)
            .build();
    FeedbackResponse response =
        FeedbackResponse.builder()
            .physicalId("FR-1")
            .studentId("STUDENT-1")
            .interventionPhysicalId("FI-1")
            .windowEndAt(OffsetDateTime.now())
            .postSeverity(0.3)
            .delta(0.5)
            .success(true)
            .build();

    StudentLearningProfile updated = profileService.applyResponse(intervention, response);

    assertEquals(1, updated.getTotalInterventions());
    assertEquals(1, updated.getTotalSuccessfulInterventions());
    assertEquals(1.0, updated.getConsistencyScore(), 1e-9);
    assertEquals(
        ProfileEffectivenessMath.EWMA_ALPHA * 0.5,
        updated.getLearningRateEstimate(),
        1e-9);
    assertTrue(updated.getModalityEffectivenessJson().containsKey("VISUAL_HIGHLIGHT"));
    assertTrue(
        updated
            .getModalityEffectivenessJson()
            .containsKey("SQUAT|CHEST_UP|VISUAL_HIGHLIGHT"));
    assertEquals(FeedbackModality.VISUAL_HIGHLIGHT, updated.getPreferredModality());
    assertNotNull(updated.getPreferredModalityByExerciseJson());
    assertTrue(updated.getPreferredModalityByExerciseJson().containsKey("SQUAT"));
    @SuppressWarnings("unchecked")
    Map<String, Object> squatPref =
        (Map<String, Object>) updated.getPreferredModalityByExerciseJson().get("SQUAT");
    assertEquals("EXPLORING", squatPref.get("source"));
  }

  @Test
  void applyResponseSnapshotsHistoryEveryFiveInterventions() {
    StudentLearningProfile existing =
        StudentLearningProfile.builder()
            .physicalId("SLP-1")
            .studentId("STUDENT-1")
            .preferredModality(FeedbackModality.VERBAL_TEXT)
            .modalityEffectivenessJson(new HashMap<>())
            .learningRateEstimate(0.1)
            .consistencyScore(0.5)
            .totalInterventions(4)
            .totalSuccessfulInterventions(2)
            .build();
    when(profileRepository.findByStudentId("STUDENT-1")).thenReturn(Optional.of(existing));
    when(profileRepository.save(any(StudentLearningProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(masteryRepository.findByStudentId("STUDENT-1")).thenReturn(Collections.emptyList());

    profileService.applyResponse(
        FeedbackIntervention.builder()
            .physicalId("FI-5")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("PUSH_UP")
            .errorCode(FormErrorCode.SAG)
            .modality(FeedbackModality.VERBAL_TEXT)
            .deliveredAt(OffsetDateTime.now())
            .baselineSeverity(0.5)
            .policySource(PolicySource.DEFAULT)
            .build(),
        FeedbackResponse.builder()
            .physicalId("FR-5")
            .studentId("STUDENT-1")
            .interventionPhysicalId("FI-5")
            .windowEndAt(OffsetDateTime.now())
            .postSeverity(0.4)
            .delta(0.1)
            .success(false)
            .build());

    verify(historyRepository, times(1))
        .save(argThat(h -> "STUDENT-1".equals(h.getStudentId()) && h.getSnapshotJson() != null));
  }

  @Test
  void applyResponseDoesNotSnapshotOffCadence() {
    StudentLearningProfile existing =
        StudentLearningProfile.builder()
            .physicalId("SLP-1")
            .studentId("STUDENT-1")
            .preferredModality(FeedbackModality.VERBAL_TEXT)
            .modalityEffectivenessJson(new HashMap<>())
            .totalInterventions(1)
            .totalSuccessfulInterventions(0)
            .build();
    when(profileRepository.findByStudentId("STUDENT-1")).thenReturn(Optional.of(existing));
    when(profileRepository.save(any(StudentLearningProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    profileService.applyResponse(
        FeedbackIntervention.builder()
            .physicalId("FI-2")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.DEPTH_LOW)
            .modality(FeedbackModality.VERBAL_TEXT)
            .deliveredAt(OffsetDateTime.now())
            .baselineSeverity(0.5)
            .policySource(PolicySource.DEFAULT)
            .build(),
        FeedbackResponse.builder()
            .physicalId("FR-2")
            .studentId("STUDENT-1")
            .interventionPhysicalId("FI-2")
            .windowEndAt(OffsetDateTime.now())
            .postSeverity(0.5)
            .delta(0.0)
            .success(false)
            .build());

    verify(historyRepository, never()).save(any());
  }

  @Test
  void successfulDeltaThresholdDelegatesToMath() {
    assertTrue(StudentLearningProfileService.isSuccessfulDelta(0.15));
    assertFalse(StudentLearningProfileService.isSuccessfulDelta(0.149));
  }
}
