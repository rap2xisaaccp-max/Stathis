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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

  /** Same five exercises and physical codes as the mobile FormErrorClassifier allow-list. */
  private static final Set<String> SUPPORTED_EXERCISES =
      Set.of("PUSH_UP", "SQUATS", "STATIC_LUNGES", "GLUTE_BRIDGE", "LYING_LEG_RAISES");

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

    // Same intervention id returns the original row and does not replace the image.
    FormCorrectionEvidenceDTO existingDto = existingOwned(studentId, interventionId);
    if (existingDto != null) {
      return existingDto;
    }

    FormErrorCode errorCode = parseErrorCode(errorCodeRaw);
    String exercise = CoachingInstructionCatalog.normalizeExercise(exerciseType);
    assertAllowedEvidence(exercise, errorCode);
    String description = approvedDescription(exercise, errorCode, errorDescription);
    String correction = approvedCorrection(exercise, errorCode, correctionText);
    byte[] jpeg = readJpeg(file);
    String digest = sha256Hex(jpeg);
    assertStudentEnrolled(studentId, classroomId);

    FeedbackInterventionRequestDTO fi =
        FeedbackInterventionRequestDTO.builder()
            .physicalId(interventionId)
            .sessionId(sessionId)
            .taskId(taskId)
            .classroomId(classroomId)
            .exerciseType(exercise)
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
      if (!sha256Matches(stored.sha256(), jpeg) || stored.byteSize() != jpeg.length) {
        storage.delete(stored.storageKey());
        stored = null;
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Stored evidence image does not match the uploaded bytes");
      }
      FormCorrectionEvidence entity =
          FormCorrectionEvidence.builder()
              .physicalId("FCE-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT))
              .interventionPhysicalId(savedFi.getPhysicalId())
              .studentId(studentId)
              .sessionId(sessionId)
              .taskId(taskId)
              .classroomId(blankToNull(classroomId))
              .attemptNumber(attemptNumber)
              .exerciseType(exercise)
              .errorCode(errorCode)
              .errorDescription(description)
              .correctionText(correction)
              .capturedAt(parseTime(capturedAt, OffsetDateTime.now()))
              .storageKey(stored.storageKey())
              .contentType("image/jpeg")
              .byteSize(stored.byteSize())
              .sha256(digest)
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

  public List<FormCorrectionEvidenceDTO> listForStudent(
      String teacherId, String studentId, String classroomId) {
    if (classroomId == null || classroomId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classroomId is required");
    }
    assertTeacherOwnsClassroomForStudent(teacherId, studentId, classroomId);
    return evidenceRepository.findByStudentIdOrderByCapturedAtDesc(studentId).stream()
        .filter(row -> classroomId.equals(row.getClassroomId()))
        .filter(row -> reviewedPair(row.getExerciseType(), row.getErrorCode()))
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  public byte[] readImage(String teacherId, String evidencePhysicalId) {
    FormCorrectionEvidence row =
        evidenceRepository
            .findByPhysicalId(evidencePhysicalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
    if (row.getClassroomId() == null || row.getClassroomId().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Evidence is not scoped to a classroom");
    }
    assertTeacherOwnsClassroomForStudent(teacherId, row.getStudentId(), row.getClassroomId());
    if (!reviewedPair(row.getExerciseType(), row.getErrorCode())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "This exercise does not allow that form error");
    }
    byte[] bytes =
        storage
            .get(row.getStorageKey())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
    if (!sha256Matches(row.getSha256(), bytes)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Evidence image failed integrity check");
    }
    return bytes;
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

  private void assertAllowedEvidence(String exercise, FormErrorCode errorCode) {
    if (errorCode == null || errorCode.isTechnical() || errorCode == FormErrorCode.UNKNOWN) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Technical or unclassified signals cannot create evidence");
    }
    if (!reviewedPair(exercise, errorCode)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "This exercise does not allow that form error");
    }
  }

  /**
   * Upload allow-list. Listing and image reads use it to hide historical rows. Those rows are
   * left unchanged.
   */
  private boolean reviewedPair(String exercise, FormErrorCode errorCode) {
    if (errorCode == null || errorCode.isTechnical() || errorCode == FormErrorCode.UNKNOWN) {
      return false;
    }
    String normalized = CoachingInstructionCatalog.normalizeExercise(exercise);
    return SUPPORTED_EXERCISES.contains(normalized)
        && CoachingInstructionCatalog.hasReviewedInstruction(normalized, errorCode);
  }

  /**
   * Blank classroom uploads stay untagged practice. A nonblank classroom must already enroll this
   * student. This check does not create or change an enrollment.
   */
  private void assertStudentEnrolled(String studentId, String classroomId) {
    String scopedClassroom = blankToNull(classroomId);
    if (scopedClassroom == null) {
      return;
    }
    boolean enrolled =
        classroomRepository
            .findByClassroomStudents_Student_User_PhysicalId(studentId)
            .stream()
            .anyMatch(classroom -> scopedClassroom.equals(classroom.getPhysicalId()));
    if (!enrolled) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Student is not enrolled in this classroom");
    }
  }

  /**
   * Blank text is replaced by the reviewed copy. A client value is kept only when it equals the
   * reviewed explanation or label, or one of the three reviewed coaching intensities.
   */
  private String approvedDescription(String exercise, FormErrorCode errorCode, String supplied) {
    String explanation = FormErrorCopy.explanation(errorCode, exercise);
    String label = FormErrorCopy.label(errorCode, exercise);
    if (supplied == null || supplied.isBlank()) {
      return explanation;
    }
    String trimmed = supplied.trim();
    if (trimmed.equals(explanation) || trimmed.equals(label)) {
      return trimmed;
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "errorDescription does not match the reviewed explanation");
  }

  private String approvedCorrection(String exercise, FormErrorCode errorCode, String supplied) {
    String reminder =
        CoachingInstructionCatalog.messageText(exercise, errorCode, InstructionIntensity.REMINDER);
    if (supplied == null || supplied.isBlank()) {
      return reminder;
    }
    String trimmed = supplied.trim();
    for (InstructionIntensity intensity : InstructionIntensity.values()) {
      if (trimmed.equals(CoachingInstructionCatalog.messageText(exercise, errorCode, intensity))) {
        return trimmed;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "correctionText does not match the reviewed instruction");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  static boolean sha256Matches(String stored, byte[] bytes) {
    if (stored == null || stored.isBlank() || bytes == null) {
      return false;
    }
    byte[] expected = stored.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    byte[] actual = sha256Hex(bytes).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, actual);
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
