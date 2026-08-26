package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
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
    assertFalse(FormErrorCopy.explanation(FormErrorCode.DEPTH_LOW).isBlank());
  }

  @Test
  void storesNewSnapshotOnce() {
    when(evidenceRepository.findByInterventionPhysicalId("FI-NEW")).thenReturn(Optional.empty());
    when(evidenceRepository.findFirstByStudentIdAndSessionId("STUDENT-1", "SES-1"))
        .thenReturn(Optional.empty());
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
  void secondUploadForSameAttemptSessionReturnsExisting() {
    FormCorrectionEvidence existing =
        FormCorrectionEvidence.builder()
            .physicalId("FCE-1")
            .interventionPhysicalId("FI-FIRST")
            .studentId("STUDENT-1")
            .sessionId("SES-ATTEMPT-1")
            .attemptNumber(1)
            .exerciseType("SQUATS")
            .errorCode(FormErrorCode.SAG)
            .errorDescription("Hips sagging")
            .correctionText("Keep hips level")
            .capturedAt(OffsetDateTime.parse("2026-08-21T00:00:00Z"))
            .storageKey("STUDENT-1/FI-FIRST.jpg")
            .contentType("image/jpeg")
            .byteSize(3)
            .build();
    when(evidenceRepository.findByInterventionPhysicalId("FI-SECOND")).thenReturn(Optional.empty());
    when(evidenceRepository.findFirstByStudentIdAndSessionId("STUDENT-1", "SES-ATTEMPT-1"))
        .thenReturn(Optional.of(existing));

    MockMultipartFile file =
        new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1, 2, 3});
    FormCorrectionEvidenceDTO dto =
        service.upload(
            "STUDENT-1",
            "FI-SECOND",
            "SES-ATTEMPT-1",
            "TASK-1",
            "ROOM-1",
            1,
            "SQUATS",
            "KNEES_IN",
            "Knees caving",
            "Push knees out",
            "2026-08-21T00:01:00Z",
            file);
    assertEquals("FCE-1", dto.getPhysicalId());
    assertEquals("FI-FIRST", dto.getInterventionPhysicalId());
    verify(storage, never()).put(any(), any(), any());
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
    when(evidenceRepository.findFirstByStudentIdAndSessionId("STUDENT-1", "SES-1"))
        .thenReturn(Optional.empty());
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
}
