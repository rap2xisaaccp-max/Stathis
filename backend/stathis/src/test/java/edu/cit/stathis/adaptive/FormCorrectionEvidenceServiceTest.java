package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.coaching.InstructionIntensity;
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
    verify(storage, never()).put(any(), any(), any());
    verify(adaptiveFeedbackService, never()).saveIntervention(any(), any());
    verify(evidenceRepository, never()).save(any());
  }

  @Test
  void rejectsCrossExerciseUnknownAndUnreviewedTextBeforeStorage() {
    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ResponseStatusException cross =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-PIKE",
                    "SES-1",
                    "TASK-1",
                    "ROOM-1",
                    1,
                    "SQUATS",
                    "PIKE",
                    null,
                    null,
                    "2026-08-21T00:00:00Z",
                    file));
    assertEquals(HttpStatus.BAD_REQUEST, cross.getStatusCode());

    ResponseStatusException unknown =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-UNK",
                    "SES-1",
                    "TASK-1",
                    "ROOM-1",
                    1,
                    "SQUATS",
                    "NOT_A_CODE",
                    null,
                    null,
                    "2026-08-21T00:00:00Z",
                    file));
    assertEquals(HttpStatus.BAD_REQUEST, unknown.getStatusCode());

    ResponseStatusException text =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-TEXT",
                    "SES-1",
                    "TASK-1",
                    "ROOM-1",
                    1,
                    "SQUATS",
                    "KNEES_IN",
                    "Invented explanation",
                    "Invented correction",
                    "2026-08-21T00:00:00Z",
                    file));
    assertEquals(HttpStatus.BAD_REQUEST, text.getStatusCode());
    verify(storage, never()).put(any(), any(), any());
    verify(adaptiveFeedbackService, never()).saveIntervention(any(), any());
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
  void listWithoutClassroomIsRejected() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.listForStudent("TEACHER-1", "STUDENT-1", " "));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verify(evidenceRepository, never()).findByStudentIdOrderByCapturedAtDesc(any());
  }

  @Test
  void teacherWhoDoesNotOwnClassroomIsForbidden() {
    Classroom other = org.mockito.Mockito.mock(Classroom.class);
    when(other.getPhysicalId()).thenReturn("ROOM-A");
    when(other.getTeacherId()).thenReturn("TEACHER-OTHER");
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(other));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-A"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(evidenceRepository, never()).findByStudentIdOrderByCapturedAtDesc(any());
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
                    .classroomId("ROOM-1")
                    .exerciseType("PUSH_UP")
                    .errorCode(FormErrorCode.SAG)
                    .capturedAt(OffsetDateTime.now())
                    .storageKey("k")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));
    stubTeacherOwns("TEACHER-1", "ROOM-1");

    List<FormCorrectionEvidenceDTO> rows = service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-1");
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
                    .classroomId("ROOM-1")
                    .exerciseType("PUSH_UP")
                    .errorCode(FormErrorCode.SAG)
                    .errorDescription("Hips dropping below a straight push-up line.")
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
                    .classroomId("ROOM-1")
                    .exerciseType("PUSH_UP")
                    .errorCode(FormErrorCode.SAG)
                    .errorDescription("Hips dropping below a straight push-up line.")
                    .correctionText("Keep your hips level with your shoulders.")
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
                    .storageKey("k1")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));

    stubTeacherOwns("TEACHER-1", "ROOM-1");
    List<FormCorrectionEvidenceDTO> rows = service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-1");
    assertEquals(2, rows.size());
    assertEquals("FI-2", rows.get(0).getInterventionPhysicalId());
    assertEquals("FI-1", rows.get(1).getInterventionPhysicalId());
    assertEquals("Hips sagging", rows.get(0).getErrorLabel());
    assertEquals("Hips sagging", rows.get(1).getErrorLabel());
    assertEquals(
        "Hips dropping below a straight push-up line.", rows.get(0).getErrorDescription());
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
    stubStudentEnrolled("ROOM-1");
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackIntervention.builder()
                .physicalId("FI-NEW")
                .studentId("STUDENT-1")
                .sessionId("SES-1")
                .exerciseType("SQUATS")
                .errorCode(FormErrorCode.KNEES_IN)
                .build());
    when(storage.put(eq("STUDENT-1"), eq("FI-NEW"), any()))
        .thenAnswer(invocation -> storedOf("FI-NEW", invocation.getArgument(2)));
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
            "KNEES_IN",
            null,
            null,
            "2026-08-21T00:00:00Z",
            file);
    assertEquals("FI-NEW", dto.getInterventionPhysicalId());
    assertEquals(2, dto.getAttemptNumber());
    assertEquals(FormErrorCopy.explanation(FormErrorCode.KNEES_IN, "SQUATS"), dto.getErrorDescription());
    assertEquals(
        CoachingInstructionCatalog.messageText(
            "SQUATS", FormErrorCode.KNEES_IN, InstructionIntensity.REMINDER),
        dto.getCorrectionText());
  }

  @Test
  void sameSessionAndSameInterventionIdStoresOneRow() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "KNEES_IN");
    FormCorrectionEvidenceDTO retry = upload("FI-001", "SES-1", "KNEES_IN");

    assertEquals(1, rows.size());
    assertEquals("FI-001", retry.getInterventionPhysicalId());
    verify(storage, times(1)).put(eq("STUDENT-1"), eq("FI-001"), any());
  }

  @Test
  void sameSessionAndDifferentInterventionIdStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "DEPTH_LOW");
    upload("FI-002", "SES-1", "KNEES_IN");

    assertEquals(2, rows.size());
    assertEquals("SES-1", rows.get("FI-001").getSessionId());
    assertEquals("SES-1", rows.get("FI-002").getSessionId());
    verify(storage, times(2)).put(eq("STUDENT-1"), any(), any());
  }

  @Test
  void sameSessionSameErrorAndNewInterventionIdStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "KNEES_IN");
    upload("FI-002", "SES-1", "KNEES_IN");

    assertEquals(2, rows.size());
    assertEquals(FormErrorCode.KNEES_IN, rows.get("FI-001").getErrorCode());
    assertEquals(FormErrorCode.KNEES_IN, rows.get("FI-002").getErrorCode());
    verify(evidenceRepository, times(2)).save(any());
  }

  @Test
  void sameSessionAndDifferentSupportedErrorStoresTwoRows() {
    Map<String, FormCorrectionEvidence> rows = stubEvidenceStore();
    upload("FI-001", "SES-1", "KNEES_IN");
    upload("FI-002", "SES-1", "DEPTH_LOW");

    assertEquals(2, rows.size());
    assertEquals(FormErrorCode.KNEES_IN, rows.get("FI-001").getErrorCode());
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
    upload("FI-001", "SES-1", "DEPTH_LOW");
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
                    .classroomId("ROOM-1")
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
                    .classroomId("ROOM-1")
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
                    .classroomId("ROOM-1")
                    .attemptNumber(1)
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.DEPTH_LOW)
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
                    .storageKey("k1")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build(),
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-OTHER")
                    .interventionPhysicalId("FI-OTHER")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .classroomId("ROOM-2")
                    .attemptNumber(1)
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.CHEST_UP)
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:05:00Z"))
                    .storageKey("k-other")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build(),
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-BLANK")
                    .interventionPhysicalId("FI-BLANK")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .attemptNumber(1)
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.CHEST_UP)
                    .capturedAt(OffsetDateTime.parse("2026-08-21T00:06:00Z"))
                    .storageKey("k-blank")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));
    stubTeacherOwns("TEACHER-1", "ROOM-1");

    List<FormCorrectionEvidenceDTO> rows =
        service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-1");
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
            .errorCode(FormErrorCode.KNEES_IN)
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
                .errorCode(FormErrorCode.KNEES_IN)
                .build());
    when(storage.put(eq("STUDENT-1"), eq("FI-RACE"), any()))
        .thenAnswer(invocation -> storedOf("FI-RACE", invocation.getArgument(2)));
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
            "KNEES_IN",
            null,
            null,
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
                    .errorCode(FormErrorCode.KNEES_IN)
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
    verify(storage, never()).get(any());
  }

  @Test
  void blankClassroomImageIsNotServed() {
    when(evidenceRepository.findByPhysicalId("FCE-BLANK"))
        .thenReturn(
            Optional.of(
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-BLANK")
                    .interventionPhysicalId("FI-BLANK")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.KNEES_IN)
                    .storageKey("k")
                    .sha256("abc")
                    .contentType("image/jpeg")
                    .byteSize(1)
                    .build()));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.readImage("TEACHER-1", "FCE-BLANK"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(storage, never()).get(any());
  }

  @Test
  void readImageRequiresMatchingSha256() {
    byte[] jpeg = new byte[] {9, 8, 7};
    when(evidenceRepository.findByPhysicalId("FCE-1"))
        .thenReturn(
            Optional.of(
                FormCorrectionEvidence.builder()
                    .physicalId("FCE-1")
                    .interventionPhysicalId("FI-1")
                    .studentId("STUDENT-1")
                    .sessionId("SES-1")
                    .classroomId("ROOM-1")
                    .exerciseType("SQUATS")
                    .errorCode(FormErrorCode.KNEES_IN)
                    .storageKey("k")
                    .sha256(FormCorrectionEvidenceService.sha256Hex(jpeg))
                    .contentType("image/jpeg")
                    .byteSize(jpeg.length)
                    .build()));
    stubTeacherOwns("TEACHER-1", "ROOM-1");
    when(storage.get("k")).thenReturn(Optional.of(jpeg));

    assertArrayEquals(jpeg, service.readImage("TEACHER-1", "FCE-1"));

    when(storage.get("k")).thenReturn(Optional.of(new byte[] {1}));
    ResponseStatusException mismatch =
        assertThrows(
            ResponseStatusException.class, () -> service.readImage("TEACHER-1", "FCE-1"));
    assertEquals(HttpStatus.CONFLICT, mismatch.getStatusCode());
  }

  @Test
  void storedHashMismatchDeletesObjectAndDoesNotSaveEvidence() {
    when(evidenceRepository.findByInterventionPhysicalId("FI-BAD")).thenReturn(Optional.empty());
    stubStudentEnrolled("ROOM-1");
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackIntervention.builder()
                .physicalId("FI-BAD")
                .studentId("STUDENT-1")
                .sessionId("SES-1")
                .exerciseType("SQUATS")
                .errorCode(FormErrorCode.KNEES_IN)
                .build());
    when(storage.put(eq("STUDENT-1"), eq("FI-BAD"), any()))
        .thenReturn(new FormCorrectionStorage.StoredObject("STUDENT-1/FI-BAD.jpg", 3, "not-the-hash"));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-BAD",
                    "SES-1",
                    "TASK-1",
                    "ROOM-1",
                    1,
                    "SQUATS",
                    "KNEES_IN",
                    null,
                    null,
                    "2026-08-21T00:00:00Z",
                    file));
    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    verify(storage).delete("STUDENT-1/FI-BAD.jpg");
    verify(evidenceRepository, never()).save(any());
  }

  @Test
  void storagePutFailureDoesNotSaveEvidenceAndRetryCanStoreTheImage() {
    byte[] jpeg = new byte[] {4, 5, 6};
    when(evidenceRepository.findByInterventionPhysicalId("FI-RETRY")).thenReturn(Optional.empty());
    stubStudentEnrolled("ROOM-1");
    when(adaptiveFeedbackService.saveIntervention(eq("STUDENT-1"), any()))
        .thenReturn(
            FeedbackIntervention.builder()
                .physicalId("FI-RETRY")
                .studentId("STUDENT-1")
                .sessionId("SES-1")
                .exerciseType("PUSH_UP")
                .errorCode(FormErrorCode.LOW_ROM)
                .build());
    when(storage.put(eq("STUDENT-1"), eq("FI-RETRY"), any()))
        .thenThrow(new IllegalStateException("storage unavailable"))
        .thenReturn(storedOf("FI-RETRY", jpeg));
    when(evidenceRepository.save(any(FormCorrectionEvidence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", jpeg);
    IllegalStateException failed =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-RETRY",
                    "SES-1",
                    "TASK-1",
                    "ROOM-1",
                    1,
                    "PUSH_UP",
                    "LOW_ROM",
                    null,
                    null,
                    "2026-08-21T00:00:00Z",
                    file));
    assertEquals("storage unavailable", failed.getMessage());
    verify(evidenceRepository, never()).save(any());

    FormCorrectionEvidenceDTO retried =
        service.upload(
            "STUDENT-1",
            "FI-RETRY",
            "SES-1",
            "TASK-1",
            "ROOM-1",
            1,
            "PUSH_UP",
            "LOW_ROM",
            null,
            null,
            "2026-08-21T00:00:00Z",
            file);
    assertEquals("FI-RETRY", retried.getInterventionPhysicalId());
    verify(evidenceRepository, times(1)).save(any());
    verify(storage, times(2)).put(eq("STUDENT-1"), eq("FI-RETRY"), any());
    verify(storage, never()).delete(any());
  }

  @Test
  void teacherOfTwoClassroomsListsOnlyTheRequestedClassroom() {
    Classroom roomA = org.mockito.Mockito.mock(Classroom.class);
    Classroom roomB = org.mockito.Mockito.mock(Classroom.class);
    when(roomA.getPhysicalId()).thenReturn("ROOM-A");
    when(roomA.getTeacherId()).thenReturn("TEACHER-1");
    when(roomB.getPhysicalId()).thenReturn("ROOM-B");
    when(roomB.getTeacherId()).thenReturn("TEACHER-1");
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(roomA, roomB));
    when(evidenceRepository.findByStudentIdOrderByCapturedAtDesc("STUDENT-1"))
        .thenReturn(
            List.of(
                evidenceRow("FCE-A", "FI-A", "ROOM-A"),
                evidenceRow("FCE-B", "FI-B", "ROOM-B")));

    List<FormCorrectionEvidenceDTO> roomARows =
        service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-A");
    assertEquals(1, roomARows.size());
    assertEquals("FI-A", roomARows.get(0).getInterventionPhysicalId());

    List<FormCorrectionEvidenceDTO> roomBRows =
        service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-B");
    assertEquals(1, roomBRows.size());
    assertEquals("FI-B", roomBRows.get(0).getInterventionPhysicalId());
  }

  @Test
  void historicalInvalidPairsAreHiddenAndTheirImagesAreDenied() {
    byte[] jpeg = new byte[] {3, 3, 3};
    FormCorrectionEvidence invalid =
        FormCorrectionEvidence.builder()
            .physicalId("FCE-INVALID")
            .interventionPhysicalId("FI-INVALID")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .classroomId("ROOM-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.SAG)
            .capturedAt(OffsetDateTime.parse("2026-08-21T00:03:00Z"))
            .storageKey("invalid-key")
            .sha256(FormCorrectionEvidenceService.sha256Hex(jpeg))
            .contentType("image/jpeg")
            .byteSize(jpeg.length)
            .build();
    FormCorrectionEvidence technical =
        FormCorrectionEvidence.builder()
            .physicalId("FCE-TECH")
            .interventionPhysicalId("FI-TECH")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .classroomId("ROOM-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.LOW_CONFIDENCE)
            .capturedAt(OffsetDateTime.parse("2026-08-21T00:02:00Z"))
            .storageKey("tech-key")
            .contentType("image/jpeg")
            .byteSize(1)
            .build();
    FormCorrectionEvidence valid =
        FormCorrectionEvidence.builder()
            .physicalId("FCE-VALID")
            .interventionPhysicalId("FI-VALID")
            .studentId("STUDENT-1")
            .sessionId("SES-1")
            .classroomId("ROOM-1")
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.KNEES_IN)
            .capturedAt(OffsetDateTime.parse("2026-08-21T00:01:00Z"))
            .storageKey("valid-key")
            .sha256(FormCorrectionEvidenceService.sha256Hex(jpeg))
            .contentType("image/jpeg")
            .byteSize(jpeg.length)
            .build();
    when(evidenceRepository.findByStudentIdOrderByCapturedAtDesc("STUDENT-1"))
        .thenReturn(List.of(invalid, technical, valid));
    when(evidenceRepository.findByPhysicalId("FCE-INVALID")).thenReturn(Optional.of(invalid));
    when(evidenceRepository.findByPhysicalId("FCE-VALID")).thenReturn(Optional.of(valid));
    stubTeacherOwns("TEACHER-1", "ROOM-1");
    when(storage.get("valid-key")).thenReturn(Optional.of(jpeg));

    List<FormCorrectionEvidenceDTO> rows =
        service.listForStudent("TEACHER-1", "STUDENT-1", "ROOM-1");
    assertEquals(1, rows.size());
    assertEquals("FI-VALID", rows.get(0).getInterventionPhysicalId());
    assertEquals("KNEES_IN", rows.get(0).getErrorCode());

    ResponseStatusException denied =
        assertThrows(
            ResponseStatusException.class, () -> service.readImage("TEACHER-1", "FCE-INVALID"));
    assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    assertArrayEquals(jpeg, service.readImage("TEACHER-1", "FCE-VALID"));

    assertEquals(FormErrorCode.SAG, invalid.getErrorCode());
    assertEquals("SQUATS", invalid.getExerciseType());
    verify(storage, never()).get("invalid-key");
    verify(storage, never()).delete(any());
    verify(evidenceRepository, never()).save(any());
    verify(evidenceRepository, never()).delete(any());
  }

  @Test
  void invalidEnrollmentDoesNotSaveInterventionOrStoreImage() {
    when(evidenceRepository.findByInterventionPhysicalId("FI-OUT")).thenReturn(Optional.empty());
    Classroom other = org.mockito.Mockito.mock(Classroom.class);
    when(other.getPhysicalId()).thenReturn("ROOM-OTHER");
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(other));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.upload(
                    "STUDENT-1",
                    "FI-OUT",
                    "SES-1",
                    "TASK-1",
                    "ROOM-1",
                    1,
                    "SQUATS",
                    "KNEES_IN",
                    null,
                    null,
                    "2026-08-21T00:00:00Z",
                    file));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    verify(adaptiveFeedbackService, never()).saveIntervention(any(), any());
    verify(storage, never()).put(any(), any(), any());
    verify(evidenceRepository, never()).save(any());
  }

  @Test
  void blankClassroomPracticeUploadStillStoresWithoutEnrollment() {
    when(evidenceRepository.findByInterventionPhysicalId(any())).thenReturn(Optional.empty());
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
            invocation -> storedOf(invocation.getArgument(1), invocation.getArgument(2)));
    when(evidenceRepository.save(any(FormCorrectionEvidence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FormCorrectionEvidenceDTO whitespace = uploadWithClassroom("FI-BLANK", "   ");
    FormCorrectionEvidenceDTO absent = uploadWithClassroom("FI-NONE", null);

    assertNull(whitespace.getClassroomId());
    assertNull(absent.getClassroomId());
    assertEquals("FI-BLANK", whitespace.getInterventionPhysicalId());
    assertEquals("FI-NONE", absent.getInterventionPhysicalId());
    verify(adaptiveFeedbackService, times(2)).saveIntervention(eq("STUDENT-1"), any());
    verify(storage, times(2)).put(eq("STUDENT-1"), any(), any());
    verify(evidenceRepository, times(2)).save(any());
    verify(classroomRepository, never())
        .findByClassroomStudents_Student_User_PhysicalId(any());
  }

  private Map<String, FormCorrectionEvidence> stubEvidenceStore() {
    stubStudentEnrolled("ROOM-1");
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
              byte[] jpeg = invocation.getArgument(2);
              return storedOf(interventionId, jpeg);
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

  private FormCorrectionEvidenceDTO uploadWithClassroom(String interventionId, String classroomId) {
    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    return service.upload(
        "STUDENT-1",
        interventionId,
        "SES-1",
        "TASK-1",
        classroomId,
        1,
        "SQUATS",
        "KNEES_IN",
        null,
        null,
        "2026-08-21T00:00:00Z",
        file);
  }

  private void stubStudentEnrolled(String classroomId) {
    Classroom room = org.mockito.Mockito.mock(Classroom.class);
    when(room.getPhysicalId()).thenReturn(classroomId);
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(room));
  }

  private void stubTeacherOwns(String teacherId, String classroomId) {
    Classroom room = org.mockito.Mockito.mock(Classroom.class);
    when(room.getPhysicalId()).thenReturn(classroomId);
    when(room.getTeacherId()).thenReturn(teacherId);
    when(classroomRepository.findByClassroomStudents_Student_User_PhysicalId("STUDENT-1"))
        .thenReturn(List.of(room));
  }

  private static FormCorrectionEvidence evidenceRow(
      String physicalId, String interventionId, String classroomId) {
    return FormCorrectionEvidence.builder()
        .physicalId(physicalId)
        .interventionPhysicalId(interventionId)
        .studentId("STUDENT-1")
        .sessionId("SES-1")
        .classroomId(classroomId)
        .attemptNumber(1)
        .exerciseType("SQUATS")
        .errorCode(FormErrorCode.KNEES_IN)
        .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
        .storageKey(physicalId)
        .contentType("image/jpeg")
        .byteSize(1)
        .build();
  }

  private static FormCorrectionStorage.StoredObject storedOf(String interventionId, byte[] jpeg) {
    return new FormCorrectionStorage.StoredObject(
        "STUDENT-1/" + interventionId + ".jpg",
        jpeg.length,
        FormCorrectionEvidenceService.sha256Hex(jpeg));
  }
}
