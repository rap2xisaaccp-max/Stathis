package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.dto.AdaptiveBatchIngestDTO;
import edu.cit.stathis.adaptive.dto.AdaptiveRecommendationDTO;
import edu.cit.stathis.adaptive.dto.AdaptiveRecommendationRequestDTO;
import edu.cit.stathis.adaptive.dto.FeedbackInterventionRequestDTO;
import edu.cit.stathis.adaptive.dto.FeedbackResponseRequestDTO;
import edu.cit.stathis.adaptive.dto.StudentLearningProfileDTO;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.entity.StudentLearningProfile;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.repository.FeedbackInterventionRepository;
import edu.cit.stathis.adaptive.repository.FeedbackResponseRepository;
import edu.cit.stathis.adaptive.service.AdaptiveArmRollupService;
import edu.cit.stathis.adaptive.service.AdaptiveFeedbackService;
import edu.cit.stathis.adaptive.service.AdaptivePolicyService;
import edu.cit.stathis.adaptive.service.ExerciseMasteryService;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import edu.cit.stathis.classroom.repository.ClassroomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Phase 2 service-layer contracts for ingest, profile read, and recommend.
 */
@ExtendWith(MockitoExtension.class)
class AdaptiveFeedbackServiceContractTest {

  @Mock private FeedbackInterventionRepository interventionRepository;
  @Mock private FeedbackResponseRepository responseRepository;
  @Mock private StudentLearningProfileService profileService;
  @Mock private ExerciseMasteryService masteryService;
  @Mock private AdaptivePolicyService policyService;
  @Mock private ClassroomRepository classroomRepository;
  @Mock private AdaptiveArmRollupService armRollupService;

  @InjectMocks private AdaptiveFeedbackService service;

  @Test
  void saveInterventionRejectsMissingSessionId() {
    FeedbackInterventionRequestDTO req =
        FeedbackInterventionRequestDTO.builder()
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.CHEST_UP)
            .build();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.saveIntervention("STUDENT-1", req));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verify(interventionRepository, never()).save(any());
  }

  @Test
  void saveInterventionPersistsNewEvent() {
    when(interventionRepository.findByPhysicalId(any())).thenReturn(Optional.empty());
    when(interventionRepository.save(any(FeedbackIntervention.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FeedbackIntervention saved =
        service.saveIntervention(
            "STUDENT-1",
            FeedbackInterventionRequestDTO.builder()
                .physicalId("FI-NEW")
                .sessionId("SES-1")
                .exerciseType("SQUAT")
                .errorCode(FormErrorCode.CHEST_UP)
                .modality(FeedbackModality.VERBAL_TEXT)
                .baselineSeverity(0.8)
                .policySource(PolicySource.DEFAULT)
                .build());

    assertEquals("FI-NEW", saved.getPhysicalId());
    assertEquals("STUDENT-1", saved.getStudentId());
    assertEquals(FormErrorCode.CHEST_UP, saved.getErrorCode());
    verify(interventionRepository).save(any(FeedbackIntervention.class));
  }

  @Test
  void ingestBatchReturnsCountsAndProfile() {
    when(interventionRepository.findByPhysicalId(any())).thenReturn(Optional.empty());
    when(interventionRepository.save(any(FeedbackIntervention.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(profileService.getOrCreate("STUDENT-1"))
        .thenReturn(
            StudentLearningProfile.builder()
                .physicalId("SLP-1")
                .studentId("STUDENT-1")
                .preferredModality(FeedbackModality.VERBAL_TEXT)
                .totalInterventions(0)
                .build());
    when(profileService.toDto(any()))
        .thenReturn(
            StudentLearningProfileDTO.builder()
                .studentId("STUDENT-1")
                .preferredModality(FeedbackModality.VERBAL_TEXT)
                .build());

    var result =
        service.ingestBatch(
            "STUDENT-1",
            AdaptiveBatchIngestDTO.builder()
                .interventions(
                    List.of(
                        FeedbackInterventionRequestDTO.builder()
                            .sessionId("SES-1")
                            .exerciseType("SQUAT")
                            .errorCode(FormErrorCode.SAG)
                            .baselineSeverity(0.5)
                            .build()))
                .responses(List.of())
                .build());

    assertEquals(1, result.getInterventionsSaved());
    assertEquals(0, result.getResponsesSaved());
    assertEquals("STUDENT-1", result.getUpdatedProfile().getStudentId());
  }

  @Test
  void recommendDelegatesToPolicyService() {
    AdaptiveRecommendationDTO expected =
        AdaptiveRecommendationDTO.builder()
            .modality(FeedbackModality.VISUAL_HIGHLIGHT)
            .errorCode(FormErrorCode.DEPTH_LOW)
            .policySource(PolicySource.EXPLOIT)
            .build();
    AdaptiveRecommendationRequestDTO request =
        AdaptiveRecommendationRequestDTO.builder()
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.DEPTH_LOW)
            .build();
    when(policyService.recommend("STUDENT-1", request)).thenReturn(expected);

    AdaptiveRecommendationDTO actual = service.recommend("STUDENT-1", request);
    assertEquals(FeedbackModality.VISUAL_HIGHLIGHT, actual.getModality());
    verify(policyService).recommend("STUDENT-1", request);
  }

  @Test
  void getProfileUsesProfileService() {
    when(profileService.getOrCreate("STUDENT-1"))
        .thenReturn(
            StudentLearningProfile.builder()
                .physicalId("SLP-1")
                .studentId("STUDENT-1")
                .build());
    when(profileService.toDto(any()))
        .thenReturn(StudentLearningProfileDTO.builder().studentId("STUDENT-1").build());

    assertEquals("STUDENT-1", service.getProfile("STUDENT-1").getStudentId());
  }

  @Test
  void saveResponseIsIdempotentPerIntervention() {
    FeedbackIntervention intervention =
        FeedbackIntervention.builder()
            .physicalId("FI-1")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.SAG)
            .baselineSeverity(0.7)
            .build();
    FeedbackResponse existing =
        FeedbackResponse.builder()
            .physicalId("FR-EXISTING")
            .studentId("STUDENT-1")
            .interventionPhysicalId("FI-1")
            .success(true)
            .delta(0.2)
            .build();

    when(interventionRepository.findByPhysicalId("FI-1")).thenReturn(Optional.of(intervention));
    when(responseRepository.findByInterventionPhysicalId("FI-1")).thenReturn(Optional.of(existing));

    FeedbackResponse returned =
        service.saveResponse(
            "STUDENT-1",
            FeedbackResponseRequestDTO.builder()
                .physicalId("FR-RETRY")
                .interventionPhysicalId("FI-1")
                .postSeverity(0.4)
                .delta(0.3)
                .success(true)
                .build());

    assertEquals("FR-EXISTING", returned.getPhysicalId());
    verify(responseRepository, never()).save(any());
    verify(profileService, never()).applyResponse(any(), any());
    verify(masteryService, never()).applyResponse(any(), any());
    verify(armRollupService, never()).recordResponse(any(), any());
  }
}
