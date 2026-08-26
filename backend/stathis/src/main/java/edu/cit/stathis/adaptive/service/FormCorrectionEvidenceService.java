package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.coaching.InstructionIntensity;
import edu.cit.stathis.adaptive.dto.FeedbackInterventionRequestDTO;
import edu.cit.stathis.adaptive.dto.FormCorrectionEvidenceDTO;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FormCorrectionEvidence;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.repository.FormCorrectionEvidenceRepository;
import edu.cit.stathis.classroom.repository.ClassroomRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FormCorrectionEvidenceService {

  public static final int MAX_BYTES = 800_000;

  @Autowired private FormCorrectionEvidenceRepository evidenceRepository;
  @Autowired private AdaptiveFeedbackService adaptiveFeedbackService;
  @Autowired private FormCorrectionStorage storage;
  @Autowired private ClassroomRepository classroomRepository;

  @Value("${apsle.evidence.max-bytes:800000}")
  private int maxBytes;

  public FormCorrectionEvidenceDTO upload(
      String studentId,
      String interventionId,
      String sessionId,
      String taskId,
      String classroomId,
      Integer attemptNumber,
      String exerciseType,
      String errorCodeRaw,
      String errorDescription,
      String correctionText,
      String capturedAt,
      MultipartFile file) {
    if (interventionId == null || interventionId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interventionId is required");
    }
    if (sessionId == null || sessionId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
    }
    if (exerciseType == null || exerciseType.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exerciseType is required");
    }
    FormErrorCode errorCode = parseErrorCode(errorCodeRaw);
    if (errorCode.isTechnical() || errorCode == FormErrorCode.UNKNOWN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Technical or unclassified signals cannot create evidence");
    }
    byte[] jpeg = readJpeg(file);

    FormCorrectionEvidenceDTO existingDto = existingOwned(studentId, interventionId);
    if (existingDto != null) {
      return existingDto;
    }

    // One snapshot per exercise attempt/session (mobile assigns a new SES-* per start/retry).
    if (sessionId != null && !sessionId.isBlank()) {
      Optional<FormCorrectionEvidence> sameSession =
          evidenceRepository.findFirstByStudentIdAndSessionId(studentId, sessionId);
      if (sameSession.isPresent()) {
        return toDto(sameSession.get());
      }
    }

    String description =
        (errorDescription == null || errorDescription.isBlank())
            ? FormErrorCopy.explanation(errorCode, exerciseType)
            : errorDescription.trim();
    String correction =
        (correctionText == null || correctionText.isBlank())
            ? CoachingInstructionCatalog.messageText(
                exerciseType, errorCode, InstructionIntensity.REMINDER)
            : correctionText.trim();

    FeedbackInterventionRequestDTO fi =
        FeedbackInterventionRequestDTO.builder()
            .physicalId(interventionId)
            .sessionId(sessionId)
            .taskId(taskId)
            .classroomId(classroomId)
            .exerciseType(exerciseType)
            .errorCode(errorCode)
            .modality(FeedbackModality.VERBAL_TTS)
            .messageText(correction)
            .deliveredAt(capturedAt)
            .baselineSeverity(0.5)
            .policySource(PolicySource.DEFAULT)
            .build();
    FeedbackIntervention savedFi = adaptiveFeedbackService.saveIntervention(studentId, fi);

    FormCorrectionStorage.StoredObject stored = null;
    try {
      stored = storage.put(studentId, interventionId, jpeg);
      FormCorrectionEvidence entity =
          FormCorrectionEvidence.builder()
              .physicalId("FCE-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT))
              .interventionPhysicalId(savedFi.getPhysicalId())
              .studentId(studentId)
              .sessionId(sessionId)
              .taskId(taskId)
              .classroomId(classroomId)
              .attemptNumber(attemptNumber)
              .exerciseType(CoachingInstructionCatalog.normalizeExercise(exerciseType))
              .errorCode(errorCode)
              .errorDescription(description)
              .correctionText(correction)
              .capturedAt(parseTime(capturedAt, OffsetDateTime.now()))
              .storageKey(stored.storageKey())
              .contentType("image/jpeg")
              .byteSize(stored.byteSize())
              .sha256(stored.sha256())
              .build();
      return toDto(evidenceRepository.save(entity));
    } catch (DataIntegrityViolationException ex) {
      FormCorrectionEvidenceDTO raced = existingOwned(studentId, interventionId);
      if (raced != null) {
        return raced;
      }
      if (stored != null) {
        storage.delete(stored.storageKey());
      }
      throw ex;
    } catch (RuntimeException ex) {
      if (stored != null && evidenceRepository.findByInterventionPhysicalId(interventionId).isEmpty()) {
        storage.delete(stored.storageKey());
      }
      throw ex;
    }
  }

  public List<FormCorrectionEvidenceDTO> listForStudent(String teacherId, String studentId) {
    return listForStudent(teacherId, studentId, null);
  }

  public List<FormCorrectionEvidenceDTO> listForStudent(
      String teacherId, String studentId, String classroomId) {
    adaptiveFeedbackService.assertTeacherCanViewStudent(teacherId, studentId);
    if (classroomId != null && !classroomId.isBlank()) {
      assertTeacherOwnsClassroomForStudent(teacherId, studentId, classroomId);
    }
    return evidenceRepository.findByStudentIdOrderByCapturedAtDesc(studentId).stream()
        .filter(row -> row.getErrorCode() != null && !row.getErrorCode().isTechnical())
        .filter(row -> matchesClassroomScope(row, classroomId))
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  public byte[] readImage(String teacherId, String evidencePhysicalId) {
    FormCorrectionEvidence row =
        evidenceRepository
            .findByPhysicalId(evidencePhysicalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
    adaptiveFeedbackService.assertTeacherCanViewStudent(teacherId, row.getStudentId());
    if (row.getClassroomId() != null && !row.getClassroomId().isBlank()) {
      assertTeacherOwnsClassroomForStudent(teacherId, row.getStudentId(), row.getClassroomId());
    }
    return storage
        .get(row.getStorageKey())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
  }

  public FormCorrectionEvidence getEntity(String physicalId) {
    return evidenceRepository
        .findByPhysicalId(physicalId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
  }

  private byte[] readJpeg(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    }
    String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    if (!contentType.contains("jpeg") && !contentType.contains("jpg") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must be image/jpeg");
    }
    if (file.getSize() > Math.max(MAX_BYTES, maxBytes)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file exceeds maximum size");
    }
    try {
      byte[] bytes = file.getBytes();
      if (bytes.length == 0 || bytes.length > Math.max(MAX_BYTES, maxBytes)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file exceeds maximum size");
      }
      return bytes;
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read image");
    }
  }

  private FormCorrectionEvidenceDTO existingOwned(String studentId, String interventionId) {
    var existing = evidenceRepository.findByInterventionPhysicalId(interventionId);
    if (existing.isEmpty()) {
      return null;
    }
    FormCorrectionEvidence row = existing.get();
    if (!studentId.equals(row.getStudentId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Evidence belongs to another student");
    }
    return toDto(row);
  }

  private boolean matchesClassroomScope(FormCorrectionEvidence row, String classroomId) {
    if (classroomId == null || classroomId.isBlank()) {
      return true;
    }
    String rowClassroom = row.getClassroomId();
    return rowClassroom == null || rowClassroom.isBlank() || classroomId.equals(rowClassroom);
  }

  private void assertTeacherOwnsClassroomForStudent(
      String teacherId, String studentId, String classroomId) {
    boolean authorized =
        classroomRepository
            .findByClassroomStudents_Student_User_PhysicalId(studentId)
            .stream()
            .anyMatch(
                c ->
                    classroomId.equals(c.getPhysicalId()) && teacherId.equals(c.getTeacherId()));
    if (!authorized) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this classroom");
    }
  }

  private FormErrorCode parseErrorCode(String raw) {
    if (raw == null || raw.isBlank()) {
      return FormErrorCode.UNKNOWN;
    }
    try {
      return FormErrorCode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return FormErrorCode.UNKNOWN;
    }
  }

  private FormCorrectionEvidenceDTO toDto(FormCorrectionEvidence row) {
    return FormCorrectionEvidenceDTO.builder()
        .physicalId(row.getPhysicalId())
        .interventionPhysicalId(row.getInterventionPhysicalId())
        .studentId(row.getStudentId())
        .sessionId(row.getSessionId())
        .taskId(row.getTaskId())
        .classroomId(row.getClassroomId())
        .attemptNumber(row.getAttemptNumber())
        .exerciseType(row.getExerciseType())
        .errorCode(row.getErrorCode() != null ? row.getErrorCode().name() : null)
        .errorLabel(FormErrorCopy.label(row.getErrorCode(), row.getExerciseType()))
        .errorDescription(row.getErrorDescription())
        .correctionText(row.getCorrectionText())
        .capturedAt(row.getCapturedAt() != null ? row.getCapturedAt().toString() : null)
        .createdAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null)
        .byteSize(row.getByteSize())
        .imageUrl("/adaptive/evidence/" + row.getPhysicalId() + "/image")
        .build();
  }

  private OffsetDateTime parseTime(String value, OffsetDateTime fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ex) {
      return fallback;
    }
  }
}
