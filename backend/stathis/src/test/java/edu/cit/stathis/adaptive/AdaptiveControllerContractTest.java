package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.stathis.adaptive.controller.AdaptiveController;
import edu.cit.stathis.adaptive.dto.*;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.service.AdaptiveFeedbackService;
import edu.cit.stathis.auth.service.PhysicalIdService;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Phase 2 API contract tests for AdaptiveController.
 *
 * <p>Validates JSON request/response shapes and STUDENT/TEACHER authorization annotations without
 * booting the full Spring Security filter chain.
 */
@ExtendWith(MockitoExtension.class)
class AdaptiveControllerContractTest {

  @Mock private AdaptiveFeedbackService adaptiveFeedbackService;
  @Mock private PhysicalIdService physicalIdService;

  @InjectMocks private AdaptiveController controller;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void studentWriteEndpointsRequireStudentRole() throws Exception {
    assertPreAuthorize(AdaptiveController.class.getMethod("ingestBatch", AdaptiveBatchIngestDTO.class), "hasRole('STUDENT')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("createIntervention", FeedbackInterventionRequestDTO.class),
        "hasRole('STUDENT')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("createResponse", FeedbackResponseRequestDTO.class),
        "hasRole('STUDENT')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("recommend", AdaptiveRecommendationRequestDTO.class),
        "hasRole('STUDENT')");
    assertPreAuthorize(AdaptiveController.class.getMethod("getOwnProfile"), "hasRole('STUDENT')");
  }

  @Test
  void teacherReadEndpointsRequireTeacherRole() throws Exception {
    assertPreAuthorize(
        AdaptiveController.class.getMethod("getStudentProfile", String.class), "hasRole('TEACHER')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("getStudentMastery", String.class), "hasRole('TEACHER')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("getDifficultyRecommendations", String.class),
        "hasRole('TEACHER')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("getInsights", String.class), "hasRole('TEACHER')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("getEvaluation", String.class), "hasRole('TEACHER')");
    assertPreAuthorize(
        AdaptiveController.class.getMethod("getClassroomEvaluation", String.class),
        "hasRole('TEACHER')");
  }

  @Test
  void postBatchIngestsInterventionsAndResponses() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.ingestBatch(eq("STUDENT-1"), any(AdaptiveBatchIngestDTO.class)))
        .thenReturn(
            AdaptiveBatchIngestResultDTO.builder()
                .interventionsSaved(1)
                .responsesSaved(1)
                .interventionPhysicalIds(List.of("FI-1"))
                .responsePhysicalIds(List.of("FR-1"))
                .updatedProfile(
                    StudentLearningProfileDTO.builder()
                        .studentId("STUDENT-1")
                        .preferredModality(FeedbackModality.VERBAL_TEXT)
                        .totalInterventions(1)
                        .build())
                .build());

    AdaptiveBatchIngestDTO body =
        AdaptiveBatchIngestDTO.builder()
            .interventions(
                List.of(
                    FeedbackInterventionRequestDTO.builder()
                        .sessionId("SES-1")
                        .exerciseType("SQUAT")
                        .errorCode(FormErrorCode.CHEST_UP)
                        .modality(FeedbackModality.VERBAL_TEXT)
                        .baselineSeverity(0.7)
                        .policySource(PolicySource.DEFAULT)
                        .build()))
            .responses(
                List.of(
                    FeedbackResponseRequestDTO.builder()
                        .interventionPhysicalId("FI-1")
                        .postSeverity(0.4)
                        .delta(0.3)
                        .success(true)
                        .build()))
            .build();

    mockMvc
        .perform(
            post("/api/adaptive/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interventionsSaved").value(1))
        .andExpect(jsonPath("$.responsesSaved").value(1))
        .andExpect(jsonPath("$.interventionPhysicalIds[0]").value("FI-1"))
        .andExpect(jsonPath("$.updatedProfile.studentId").value("STUDENT-1"));

    verify(adaptiveFeedbackService).ingestBatch(eq("STUDENT-1"), any(AdaptiveBatchIngestDTO.class));
  }

  @Test
  void postInterventionReturnsPersistedShape() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackIntervention.builder()
                .physicalId("FI-99")
                .studentId("STUDENT-1")
                .sessionId("SES-9")
                .exerciseType("SQUAT")
                .errorCode(FormErrorCode.KNEES_IN)
                .modality(FeedbackModality.VISUAL_HIGHLIGHT)
                .messageText("Push knees outward.")
                .deliveredAt(OffsetDateTime.parse("2026-07-30T05:00:00Z"))
                .baselineSeverity(0.6)
                .policySource(PolicySource.EXPLORE)
                .experimentArm("ADAPTIVE")
                .build());

    FeedbackInterventionRequestDTO body =
        FeedbackInterventionRequestDTO.builder()
            .sessionId("SES-9")
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.KNEES_IN)
            .modality(FeedbackModality.VISUAL_HIGHLIGHT)
            .baselineSeverity(0.6)
            .policySource(PolicySource.EXPLORE)
            .build();

    mockMvc
        .perform(
            post("/api/adaptive/interventions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.physicalId").value("FI-99"))
        .andExpect(jsonPath("$.errorCode").value("KNEES_IN"))
        .andExpect(jsonPath("$.modality").value("VISUAL_HIGHLIGHT"))
        .andExpect(jsonPath("$.policySource").value("EXPLORE"));
  }

  @Test
  void postResponseReturnsClosedLoopFields() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.saveResponse(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackResponse.builder()
                .physicalId("FR-99")
                .studentId("STUDENT-1")
                .interventionPhysicalId("FI-99")
                .windowEndAt(OffsetDateTime.parse("2026-07-30T05:00:10Z"))
                .postSeverity(0.2)
                .delta(0.4)
                .repsInWindow(2)
                .success(true)
                .confoundersJson(Map.of("visibilityOk", true))
                .build());

    FeedbackResponseRequestDTO body =
        FeedbackResponseRequestDTO.builder()
            .interventionPhysicalId("FI-99")
            .postSeverity(0.2)
            .delta(0.4)
            .success(true)
            .build();

    mockMvc
        .perform(
            post("/api/adaptive/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.physicalId").value("FR-99"))
        .andExpect(jsonPath("$.interventionPhysicalId").value("FI-99"))
        .andExpect(jsonPath("$.delta").value(0.4))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void postRecommendReturnsModalityPolicyAndMessage() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.recommend(eq("STUDENT-1"), any()))
        .thenReturn(
            AdaptiveRecommendationDTO.builder()
                .modality(FeedbackModality.VERBAL_TEXT)
                .errorCode(FormErrorCode.CHEST_UP)
                .messageCode("CHEST_UP")
                .messageText("Keep chest up and back straight.")
                .policySource(PolicySource.DEFAULT)
                .expectedDelta(0.0)
                .experimentArm("ADAPTIVE")
                .cooldownMs(8000)
                .build());

    AdaptiveRecommendationRequestDTO body =
        AdaptiveRecommendationRequestDTO.builder()
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.CHEST_UP)
            .currentSeverity(0.7)
            .staticControl(false)
            .build();

    mockMvc
        .perform(
            post("/api/adaptive/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modality").value("VERBAL_TEXT"))
        .andExpect(jsonPath("$.errorCode").value("CHEST_UP"))
        .andExpect(jsonPath("$.cooldownMs").value(8000))
        .andExpect(jsonPath("$.experimentArm").value("ADAPTIVE"));
  }

  @Test
  void getOwnProfileReturnsStudentLearningProfile() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.getProfile("STUDENT-1"))
        .thenReturn(
            StudentLearningProfileDTO.builder()
                .physicalId("SLP-1")
                .studentId("STUDENT-1")
                .preferredModality(FeedbackModality.VERBAL_TTS)
                .totalInterventions(3)
                .totalSuccessfulInterventions(2)
                .build());

    mockMvc
        .perform(get("/api/adaptive/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value("STUDENT-1"))
        .andExpect(jsonPath("$.preferredModality").value("VERBAL_TTS"))
        .andExpect(jsonPath("$.totalInterventions").value(3));
  }

  @Test
  void getTeacherProfileUsesClassroomGuardedInsights() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("TEACHER-1");
    when(adaptiveFeedbackService.getInsights("TEACHER-1", "STUDENT-9"))
        .thenReturn(
            AdaptiveInsightsDTO.builder()
                .studentId("STUDENT-9")
                .profile(
                    StudentLearningProfileDTO.builder()
                        .studentId("STUDENT-9")
                        .preferredModality(FeedbackModality.VISUAL_HIGHLIGHT)
                        .build())
                .mastery(List.of())
                .totalInterventions(0)
                .successfulInterventions(0)
                .overallSuccessRate(0.0)
                .build());

    mockMvc
        .perform(get("/api/adaptive/profile/STUDENT-9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value("STUDENT-9"))
        .andExpect(jsonPath("$.preferredModality").value("VISUAL_HIGHLIGHT"));

    verify(adaptiveFeedbackService).getInsights("TEACHER-1", "STUDENT-9");
  }

  private static void assertPreAuthorize(Method method, String expected) {
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    assertNotNull(annotation, method.getName() + " missing @PreAuthorize");
    assertEquals(expected, annotation.value(), method.getName());
  }
}
