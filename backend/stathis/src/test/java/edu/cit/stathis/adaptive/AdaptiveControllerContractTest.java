package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import edu.cit.stathis.adaptive.service.FormCorrectionEvidenceService;
import edu.cit.stathis.auth.service.PhysicalIdService;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdaptiveControllerContractTest {

  @Mock private AdaptiveFeedbackService adaptiveFeedbackService;
  @Mock private FormCorrectionEvidenceService evidenceService;
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
    assertPreAuthorize(method("ingestBatch"), "hasRole('STUDENT')");
    assertPreAuthorize(method("createIntervention"), "hasRole('STUDENT')");
    assertPreAuthorize(method("createResponse"), "hasRole('STUDENT')");
    assertPreAuthorize(method("uploadEvidence"), "hasRole('STUDENT')");
    assertPreAuthorize(method("getOwnProfile"), "hasRole('STUDENT')");
    assertPreAuthorize(method("getOwnMastery"), "hasRole('STUDENT')");
    assertPreAuthorize(method("getOwnFormMastery"), "hasRole('STUDENT')");
  }

  @Test
  void teacherReadEndpointsRequireTeacherRole() throws Exception {
    assertPreAuthorize(method("getStudentProfile"), "hasRole('TEACHER')");
    assertPreAuthorize(method("getStudentMastery"), "hasRole('TEACHER')");
    assertPreAuthorize(method("getStudentFormMastery"), "hasRole('TEACHER')");
    assertPreAuthorize(method("getDifficultyRecommendations"), "hasRole('TEACHER')");
    assertPreAuthorize(method("getInsights"), "hasRole('TEACHER')");
    assertPreAuthorize(method("listStudentEvidence"), "hasRole('TEACHER')");
    assertPreAuthorize(method("getEvidenceImage"), "hasRole('TEACHER')");
  }

  @Test
  void postBatchIngestsInterventions() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.ingestBatch(eq("STUDENT-1"), any(AdaptiveBatchIngestDTO.class)))
        .thenReturn(
            AdaptiveBatchIngestResultDTO.builder()
                .interventionsSaved(1)
                .responsesSaved(0)
                .interventionPhysicalIds(List.of("FI-1"))
                .responsePhysicalIds(List.of())
                .updatedProfile(
                    StudentLearningProfileDTO.builder()
                        .studentId("STUDENT-1")
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
                        .modality(FeedbackModality.VERBAL_TTS)
                        .baselineSeverity(0.7)
                        .policySource(PolicySource.DEFAULT)
                        .build()))
            .responses(List.of())
            .build();

    mockMvc
        .perform(
            post("/api/adaptive/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interventionsSaved").value(1))
        .andExpect(jsonPath("$.interventionPhysicalIds[0]").value("FI-1"));
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
                .modality(FeedbackModality.VERBAL_TTS)
                .messageText("Keep your front knee tracking over your toes.")
                .deliveredAt(OffsetDateTime.parse("2026-07-30T05:00:00Z"))
                .baselineSeverity(0.6)
                .policySource(PolicySource.DEFAULT)
                .build());

    FeedbackInterventionRequestDTO body =
        FeedbackInterventionRequestDTO.builder()
            .sessionId("SES-9")
            .exerciseType("SQUAT")
            .errorCode(FormErrorCode.KNEES_IN)
            .modality(FeedbackModality.VERBAL_TTS)
            .baselineSeverity(0.6)
            .policySource(PolicySource.DEFAULT)
            .build();

    mockMvc
        .perform(
            post("/api/adaptive/interventions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.physicalId").value("FI-99"))
        .andExpect(jsonPath("$.errorCode").value("KNEES_IN"));
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
        .andExpect(jsonPath("$.physicalId").value("FR-99"));
  }

  @Test
  void postEvidenceUploadsSnapshot() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(evidenceService.upload(
            eq("STUDENT-1"),
            eq("FI-1"),
            eq("SES-1"),
            eq("TASK-1"),
            eq("ROOM-1"),
            eq(1),
            eq("SQUATS"),
            eq("SAG"),
            eq("Hips sagging"),
            eq("Keep hips level"),
            any(),
            any()))
        .thenReturn(
            FormCorrectionEvidenceDTO.builder()
                .physicalId("FCE-1")
                .interventionPhysicalId("FI-1")
                .studentId("STUDENT-1")
                .exerciseType("SQUATS")
                .errorCode("SAG")
                .errorLabel("Hips sagging")
                .correctionText("Keep hips level")
                .build());

    MockMultipartFile file =
        new MockMultipartFile("file", "snap.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3});

    mockMvc
        .perform(
            multipart("/api/adaptive/evidence")
                .file(file)
                .param("interventionId", "FI-1")
                .param("sessionId", "SES-1")
                .param("taskId", "TASK-1")
                .param("classroomId", "ROOM-1")
                .param("attemptNumber", "1")
                .param("exerciseType", "SQUATS")
                .param("errorCode", "SAG")
                .param("errorDescription", "Hips sagging")
                .param("correctionText", "Keep hips level"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.physicalId").value("FCE-1"))
        .andExpect(jsonPath("$.errorLabel").value("Hips sagging"));
  }

  @Test
  void getOwnProfileReturnsStudentLearningProfile() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.getProfile("STUDENT-1"))
        .thenReturn(
            StudentLearningProfileDTO.builder()
                .physicalId("SLP-1")
                .studentId("STUDENT-1")
                .totalInterventions(3)
                .build());

    mockMvc
        .perform(get("/api/adaptive/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value("STUDENT-1"))
        .andExpect(jsonPath("$.totalInterventions").value(3));
  }

  @Test
  void getTeacherProfileUsesClassroomGuardedInsights() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("TEACHER-1");
    when(adaptiveFeedbackService.getInsights("TEACHER-1", "STUDENT-9"))
        .thenReturn(
            AdaptiveInsightsDTO.builder()
                .studentId("STUDENT-9")
                .profile(StudentLearningProfileDTO.builder().studentId("STUDENT-9").build())
                .mastery(List.of())
                .totalInterventions(0)
                .successfulInterventions(0)
                .overallSuccessRate(0.0)
                .build());

    mockMvc
        .perform(get("/api/adaptive/profile/STUDENT-9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value("STUDENT-9"));

    verify(adaptiveFeedbackService).getInsights("TEACHER-1", "STUDENT-9");
  }

  @Test
  void getOwnFormMasteryReturnsAttemptLevelRows() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("STUDENT-1");
    when(adaptiveFeedbackService.getFormMastery("STUDENT-1"))
        .thenReturn(
            List.of(
                FormMasteryDTO.builder()
                    .studentId("STUDENT-1")
                    .exerciseType("SQUATS")
                    .formMasteryLevel(0.5)
                    .formMasteryPercent(50.0)
                    .eligibleAttemptCount(2)
                    .build()));

    mockMvc
        .perform(get("/api/adaptive/form-mastery"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].exerciseType").value("SQUATS"))
        .andExpect(jsonPath("$[0].formMasteryLevel").value(0.5))
        .andExpect(jsonPath("$[0].formMasteryPercent").value(50.0))
        .andExpect(jsonPath("$[0].eligibleAttemptCount").value(2));
  }

  @Test
  void getTeacherFormMasteryUsesClassroomGuard() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("TEACHER-1");
    when(adaptiveFeedbackService.getFormMasteryForTeacher("TEACHER-1", "STUDENT-9"))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/adaptive/form-mastery/STUDENT-9"))
        .andExpect(status().isOk());

    verify(adaptiveFeedbackService).getFormMasteryForTeacher("TEACHER-1", "STUDENT-9");
  }

  @Test
  void getEvidenceImageSetsPrivateNoStoreHeaders() throws Exception {
    when(physicalIdService.getCurrentUserPhysicalId()).thenReturn("TEACHER-1");
    when(evidenceService.readImage("TEACHER-1", "FCE-1")).thenReturn(new byte[] {1, 2, 3});

    mockMvc
        .perform(get("/api/adaptive/evidence/FCE-1/image"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(header().string("Pragma", "no-cache"));
  }

  private static Method method(String name) {
    return Arrays.stream(AdaptiveController.class.getDeclaredMethods())
        .filter(m -> m.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing method " + name));
  }

  private static void assertPreAuthorize(Method method, String expected) {
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    assertNotNull(annotation, method.getName() + " missing @PreAuthorize");
    assertEquals(expected, annotation.value(), method.getName());
  }
}
