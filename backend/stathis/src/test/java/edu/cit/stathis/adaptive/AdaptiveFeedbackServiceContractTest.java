package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.dto.AdaptiveBatchIngestDTO;
import edu.cit.stathis.adaptive.dto.AdaptiveInsightsDTO;
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
import edu.cit.stathis.adaptive.service.AdaptiveFeedbackService;
import edu.cit.stathis.adaptive.service.ExerciseMasteryService;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import edu.cit.stathis.classroom.entity.Classroom;
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
  @Mock private ClassroomRepository classroomRepository;

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
  }

  @Test
  void getInsightsExcludesTechnicalErrorsFromSuccessAndTopErrors() {
    Classroom classroom = org.mockito.Mockito.mock(Classroom.class);
    when(classroom.getTeacherId()).thenReturn("TEACHER-1");
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(classroom));

    FeedbackIntervention coachable =
        FeedbackIntervention.builder()
            .physicalId("FI-SAG")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.SAG)
            .modality(FeedbackModality.VISUAL_HIGHLIGHT)
            .build();
    FeedbackIntervention technical =
        FeedbackIntervention.builder()
            .physicalId("FI-TECH")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.LOW_CONFIDENCE)
            .modality(FeedbackModality.VERBAL_TEXT)
            .build();
    FeedbackIntervention coachableFail =
        FeedbackIntervention.builder()
            .physicalId("FI-ROM")
            .studentId("STUDENT-1")
            .sessionId("SES-2")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.LOW_ROM)
            .modality(FeedbackModality.VERBAL_TEXT)
            .build();

    when(interventionRepository.findByStudentIdOrderByDeliveredAtDesc("STUDENT-1"))
        .thenReturn(List.of(coachable, technical, coachableFail));

    when(profileService.getOrCreate("STUDENT-1"))
        .thenReturn(
            StudentLearningProfile.builder()
                .physicalId("SLP-1")
                .studentId("STUDENT-1")
                .totalInterventions(2)
                .build());
    when(profileService.toDto(any()))
        .thenReturn(
            StudentLearningProfileDTO.builder()
                .studentId("STUDENT-1")
                .totalInterventions(2)
                .learningRateEstimate(0.1)
                .consistencyScore(0.5)
                .build());
    when(masteryService.listForStudent("STUDENT-1")).thenReturn(List.of());

    AdaptiveInsightsDTO insights = service.getInsights("TEACHER-1", "STUDENT-1");

    assertEquals(2, insights.getTotalInterventions());
    assertEquals(0, insights.getSuccessfulInterventions());
    assertFalse(insights.getTopRecurringErrors().containsKey("LOW_CONFIDENCE"));
    assertTrue(insights.getTopRecurringErrors().containsKey("SAG"));
    assertTrue(insights.getTopRecurringErrors().containsKey("LOW_ROM"));
  }
}
