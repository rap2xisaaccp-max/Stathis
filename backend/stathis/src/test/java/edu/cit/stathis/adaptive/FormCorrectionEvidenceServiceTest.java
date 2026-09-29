package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.dto.FeedbackInterventionRequestDTO;
import edu.cit.stathis.adaptive.dto.FormCorrectionEvidenceDTO;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FormCorrectionEvidence;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.repository.FormCorrectionEvidenceRepository;
import edu.cit.stathis.adaptive.service.AdaptiveFeedbackService;
import edu.cit.stathis.adaptive.service.FormCorrectionEvidenceService;
import edu.cit.stathis.adaptive.service.FormCorrectionStorage;
import edu.cit.stathis.adaptive.service.FormErrorCopy;
import edu.cit.stathis.classroom.entity.Classroom;
import edu.cit.stathis.classroom.repository.ClassroomRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FormCorrectionEvidenceServiceTest {

  @Mock private FormCorrectionEvidenceRepository evidenceRepository;
  @Mock private AdaptiveFeedbackService adaptiveFeedbackService;
  @Mock private FormCorrectionStorage storage;
  @Mock private ClassroomRepository classroomRepository;

  @InjectMocks private FormCorrectionEvidenceService service;

  @Test
  void rejectsTechnicalErrorCodes() {
    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-1",
                    "SES-1",
                    null,
                    null,
                    null,
                    "SQUATS",
                    "LOW_CONFIDENCE",
                    null,
                    null,
                    null,
                    file));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void secondUploadForSameInterventionReturnsExisting() {
    FormCorrectionEvidence existing =
        FormCorrectionEvidence.builder()
            .physicalId("FCE-1")
            .interventionPhysicalId("FI-1")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.SAG)
            .errorDescription("Hips sagging")
            .correctionText("Keep hips level")
            .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
            .storageKey("STUDENT-1/FI-1.jpg")
            .contentType("image/jpeg")
            .byteSize(3)
            .build();
    when(evidenceRepository.findByInterventionPhysicalId("FI-1")).thenReturn(Optional.of(existing));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    FormCorrectionEvidenceDTO dto =
        service.upload(
            "STUDENT-1",
            "FI-1",
            "SES-1",
            null,
            null,
            1,
            "SQUATS",
            "SAG",
            "Hips sagging",
            "Keep hips level",
            "2026-08-21T00:00:00Z",
            file);
    assertEquals("FCE-1", dto.getPhysicalId());
    verify(storage, never()).put(any(), any(), any());
    verify(evidenceRepository, never()).save(any());
  }

  @Test
  void teacherWithoutClassroomIsForbidden() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this student"))
        .when(adaptiveFeedbackService)
        .assertTeacherCanViewStudent("TEACHER-X", "STUDENT-1");
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.listForStudent("TEACHER-X", "STUDENT-1"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  @Test
  void teacherWithClassroomCanList() {
    when(evidenceRepository.findByStudentIdOrderByCapturedAtDesc("STUDENT-1"))
        .thenReturn(
            List.of(
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-1")
                    .interventionPhysicalId("FI-1")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.SAG)
                    .capturedAt(OffsetDateTime.now())
                    .storageKey("k")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));

    List<FormCorrectionEvidenceDTO> rows = service.listForStudent("TEACHER-1", "STUDENT-1");
    assertEquals(1, rows.size());
    assertEquals("Hips sagging", rows.get(0).getErrorLabel());
  }

  @Test
  void teacherSeesBothRecordsWithFriendlyDescriptions() {
    when(evidenceRepository.findByStudentIdOrderByCapturedAtDesc("STUDENT-1"))
        .thenReturn(
            List.of(
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-2")
                    .interventionPhysicalId("FI-2")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.SAG)
                    .errorDescription("Hips or torso dropping below a straight body line.")
                    .correctionText("Keep your hips level with your shoulders.")
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:02:00Z"))
                    .storageKey("k2")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build(),
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-1")
                    .interventionPhysicalId("FI-1")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.SAG)
                    .errorDescription("Hips or torso dropping below a straight body line.")
                    .correctionText("Keep your hips level with your shoulders.")
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
                    .storageKey("k1")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));

    List<FormCorrectionEvidenceDTO> rows = service.listForStudent("TEACHER-1", "STUDENT-1");
    assertEquals(2, rows.size());
    assertEquals("FI-2", rows.get(0).getInterventionPhysicalId());
    assertEquals("FI-1", rows.get(1).getInterventionPhysicalId());
    assertEquals("Hips sagging", rows.get(0).getErrorLabel());
    assertEquals("Hips sagging", rows.get(1).getErrorLabel());
    assertEquals(
        "Hips or torso dropping below a straight body line.", rows.get(0).getErrorDescription());
    assertEquals(
        "Keep your hips level with your shoulders.", rows.get(0).getCorrectionText());
    assertEquals(rows.get(0).getErrorDescription(), rows.get(1).getErrorDescription());
  }

  @Test
  void formErrorCopyHasTeacherFriendlyLabels() {
    assertEquals("Hips sagging", FormErrorCopy.label(FormErrorCode.SAG));
    assertEquals("Torso leaning", FormErrorCopy.label(FormErrorCode.CHEST_UP, "SQUATS"));
    assertFalse(FormErrorCopy.explanation(FormErrorCode.DEPTH_LOW).isBlank());
  }

  @Test
  void storesNewSnapshotOnce() {
    when(evidenceRepository.findByInterventionPhysicalId("FI-NEW")).thenReturn(Optional.empty());
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackIntervention.builder()
                .physicalId("FI-NEW")
                .studentId("STUDENT-1")
                .sessionId("SES-1")
                .exerciseType("SQUATS")
                .errorCode(FormErrorCode.SAG)
                .build());
    when(storage.put(eq("STUDENT-1"), eq("FI-NEW"), any()))
        .thenReturn(new FormCorrectionStorage.StoredObject("STUDENT-1/FI-NEW.jpg", 3, "abc"));
    when(evidenceRepository.save(any(FormCorrectionEvidence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    FormCorrectionEvidenceDTO dto =
        service.upload(
            "STUDENT-1",
            "FI-NEW",
            "SES-1",
            "TASK-1",
            "ROOM-1",
            2,
            "SQUATS",
            "SAG",
            "Hips sagging",
            "Keep hips level",
            "2026-08-21T00:00:00Z",
            file);
    assertEquals("FI-NEW", dto.getInterventionPhysicalId());
    assertEquals(2, dto.getAttemptNumber());
  }

  @Test
  void sameSessionAndSameInterventionIdStoresOneRow() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "SAG");
    FormCorrectionEvidenceDTO retry = upload("FI-001", "SES-1", "SAG");

    assertEquals(1, rows.size());
    assertEquals("FI-001", retry.getInterventionPhysicalId());
    verify(storage, times(1)).put(eq("STUDENT-1"), eq("FI-001"), any());
  }

  @Test
  void sameSessionAndDifferentInterventionIdStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "SAG");
    upload("FI-002", "SES-1", "KNEES_IN");

    assertEquals(2, rows.size());
    assertEquals("SES-1", rows.get("FI-001").getSessionId());
    assertEquals("SES-1", rows.get("FI-002").getSessionId());
    verify(storage, times(2)).put(eq("STUDENT-1"), any(), any());
  }

  @Test
  void sameSessionSameErrorAndNewInterventionIdStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "SAG");
    upload("FI-002", "SES-1", "SAG");

    assertEquals(2, rows.size());
    assertEquals(FormErrorCode.SAG, rows.get("FI-001").getErrorCode());
    assertEquals(FormErrorCode.SAG, rows.get("FI-002").getErrorCode());
    verify(evidenceRepository, times(2)).save(any());
  }

  @Test
  void sameSessionAndDifferentSupportedErrorStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "SAG");
    upload("FI-002", "SES-1", "DEPTH_LOW");

    assertEquals(2, rows.size());
    assertEquals(FormErrorCode.SAG, rows.get("FI-001").getErrorCode());
    assertEquals(FormErrorCode.DEPTH_LOW, rows.get("FI-002").getErrorCode());
  }

  @Test
  void differentExercisesInTheSameTaskStaySeparate() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-PUSH", "SES-PUSH", "SAG", "PUSH_UP");
    upload("FI-SQUAT", "SES-SQUAT", "KNEES_IN", "SQUATS");

    assertEquals(2, rows.size());
    assertEquals("PUSH_UP", rows.get("FI-PUSH").getExerciseType());
    assertEquals("SQUATS", rows.get("FI-SQUAT").getExerciseType());
    assertEquals("TASK-1", rows.get("FI-PUSH").getTaskId());
    assertEquals("TASK-1", rows.get("FI-SQUAT").getTaskId());
    assertEquals("SES-PUSH", rows.get("FI-PUSH").getSessionId());
    assertEquals("SES-SQUAT", rows.get("FI-SQUAT").getSessionId());
  }

  @Test
  void duplicateRetryOfSecondInterventionStillStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "SAG");
    upload("FI-002", "SES-1", "KNEES_IN");
    FormCorrectionEvidenceDTO retry = upload("FI-002", "SES-1", "KNEES_IN");

    assertEquals(2, rows.size());
    assertEquals("FI-002", retry.getInterventionPhysicalId());
    verify(storage, times(1)).put(eq("STUDENT-1"), eq("FI-001"), any());
    verify(storage, times(1)).put(eq("STUDENT-1"), eq("FI-002"), any());
    verify(evidenceRepository, times(2)).save(any());
  }

  @Test
  void teacherEvidenceListReturnsAllLegitimateCyclesFromTheSameAttempt() {
    when(evidenceRepository.findByStudentIdOrderByCapturedAtDesc("STUDENT-1"))
        .thenReturn(
            List.of(
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-3")
                    .interventionPhysicalId("FI-003")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .attemptNumber(1)
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.CHEST_UP)
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:04:00Z"))
                    .storageKey("k3")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build(),
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-2")
                    .interventionPhysicalId("FI-002")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .attemptNumber(1)
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.KNEES_IN)
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:02:00Z"))
                    .storageKey("k2")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build(),
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-1")
                    .interventionPhysicalId("FI-001")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .attemptNumber(1)
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.SAG)
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
                    .storageKey("k1")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));

    List<FormCorrectionEvidenceDTO> rows = service.listForStudent("TEACHER-1", "STUDENT-1");
    assertEquals(3, rows.size());
    assertEquals("FI-003", rows.get(0).getInterventionPhysicalId());
    assertEquals("FI-002", rows.get(1).getInterventionPhysicalId());
    assertEquals("FI-001", rows.get(2).getInterventionPhysicalId());
    assertTrue(rows.stream().allMatch(row -> "SES-1".equals(row.getSessionId())));
    assertTrue(rows.stream().allMatch(row -> Integer.valueOf(1).equals(row.getAttemptNumber())));
  }

  @Test
  void concurrentDuplicateUploadReturnsExistingWithoutDeletingObject() {
    FormCorrectionEvidence existing =
        FormCorrectionEvidence.builder()
            .physicalId("FCE-RACE")
            .interventionPhysicalId("FI-RACE")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.SAG)
            .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
            .storageKey("STUDENT-1/FI-RACE.jpg")
            .contentType("image/jpeg")
            .byteSize(3)
            .build();
    when(evidenceRepository.findByInterventionPhysicalId("FI-RACE"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackIntervention.builder()
                .physicalId("FI-RACE")
                .studentId("STUDENT-1")
                .sessionId("SES-1")
                .exerciseType("SQUATS")
                .errorCode(FormErrorCode.SAG)
                .build());
    when(storage.put(eq("STUDENT-1"), eq("FI-RACE"), any()))
        .thenReturn(new FormCorrectionStorage.StoredObject("STUDENT-1/FI-RACE.jpg", 3, "abc"));
    when(evidenceRepository.save(any(FormCorrectionEvidence.class)))
        .thenThrow(new DataIntegrityViolationException("unique intervention_physical_id"));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    FormCorrectionEvidenceDTO dto =
        service.upload(
            "STUDENT-1",
            "FI-RACE",
            "SES-1",
            null,
            null,
            1,
            "SQUATS",
            "SAG",
            "Hips sagging",
            "Keep hips level",
            "2026-08-21T00:00:00Z",
            file);
    assertEquals("FCE-RACE", dto.getPhysicalId());
    verify(storage, never()).delete(any());
  }

  @Test
  void teacherCannotReadImageFromUnownedClassroom() {
    when(evidenceRepository.findByPhysicalId("FCE-1"))
        .thenReturn(
            Optional.of(
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-1")
                    .interventionPhysicalId("FI-1")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .classroomId("ROOM-B")
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.SAG)
                    .storageKey("k")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));
    Classroom other = org.mockito.Mockito.mock(Classroom.class);
    when(other.getPhysicalId()).thenReturn("ROOM-A");
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(other));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.readImage("TEACHER-1", "FCE-1"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  private Map<String, FormCorrectionEvidence> stubEvidenceStore() {
    Map<String, FormCorrectionEvidence> rows = new HashMap<>();
    when(evidenceRepository.findByInterventionPhysicalId(any()))
        .thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(0))));
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenAnswer(
            invocation -> {
              FeedbackInterventionRequestDTO request = invocation.getArgument(1);
              return FeedbackIntervention.builder()
                  .physicalId(request.getPhysicalId())
                  .studentId("STUDENT-1")
                  .sessionId(request.getSessionId())
                  .exerciseType(request.getExerciseType())
                  .errorCode(request.getErrorCode())
                  .build();
            });
    when(storage.put(eq("STUDENT-1"), any(), any()))
        .thenAnswer(
            invocation -> {
              String interventionId = invocation.getArgument(1);
              return new FormCorrectionStorage.StoredObject(
                  "STUDENT-1/" + interventionId + ".jpg", 3, "abc");
            });
    when(evidenceRepository.save(any(FormCorrectionEvidence.class)))
        .thenAnswer(
            invocation -> {
              FormCorrectionEvidence saved = invocation.getArgument(0);
              rows.put(saved.getInterventionPhysicalId(), saved);
              return saved;
            });
    return rows;
  }

  private FormCorrectionEvidenceDTO upload(String interventionId, String sessionId, String errorCode) {
    return upload(interventionId, sessionId, errorCode, "SQUATS");
  }

  private FormCorrectionEvidenceDTO upload(
      String interventionId, String sessionId, String errorCode, String exerciseType) {
    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    return service.upload(
        "STUDENT-1",
        interventionId,
        sessionId,
        "TASK-1",
        "ROOM-1",
        1,
        exerciseType,
        errorCode,
        null,
        null,
        "2026-08-21T00:00:00Z",
        file);
  }
}
