package edu.cit.stathis.adaptive.controller;

import edu.cit.stathis.adaptive.dto.*;
import edu.cit.stathis.adaptive.service.AdaptiveFeedbackService;
import edu.cit.stathis.adaptive.service.FormCorrectionEvidenceService;
import edu.cit.stathis.auth.service.PhysicalIdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/adaptive")
@Tag(
    name = "Form Correction Coaching",
    description = "Confirmed form-error coaching events and teacher evidence log")
public class AdaptiveController {

  @Autowired private AdaptiveFeedbackService adaptiveFeedbackService;
  @Autowired private FormCorrectionEvidenceService evidenceService;
  @Autowired private PhysicalIdService physicalIdService;

  @PostMapping("/batch")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Batch ingest coaching interventions from a live exercise session")
  public ResponseEntity<AdaptiveBatchIngestResultDTO> ingestBatch(
      @RequestBody AdaptiveBatchIngestDTO batch) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.ingestBatch(studentId, batch));
  }

  @PostMapping("/interventions")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Log a single form-correction intervention")
  public ResponseEntity<FeedbackInterventionResponseDTO> createIntervention(
      @RequestBody FeedbackInterventionRequestDTO request) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    var saved = adaptiveFeedbackService.saveIntervention(studentId, request);
    return ResponseEntity.ok(
        FeedbackInterventionResponseDTO.builder()
            .physicalId(saved.getPhysicalId())
            .studentId(saved.getStudentId())
            .sessionId(saved.getSessionId())
            .taskId(saved.getTaskId())
            .classroomId(saved.getClassroomId())
            .exerciseType(saved.getExerciseType())
            .errorCode(saved.getErrorCode())
            .modality(saved.getModality())
            .messageCode(saved.getMessageCode())
            .messageText(saved.getMessageText())
            .deliveredAt(
                saved.getDeliveredAt() != null ? saved.getDeliveredAt().toString() : null)
            .baselineSeverity(saved.getBaselineSeverity())
            .policySource(saved.getPolicySource())
            .experimentArm(saved.getExperimentArm())
            .correctionDelivered(saved.getMessageText())
            .build());
  }

  @PostMapping("/responses")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Legacy response ingest (no longer drives learning)")
  public ResponseEntity<FeedbackResponseResponseDTO> createResponse(
      @RequestBody FeedbackResponseRequestDTO request) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    var saved = adaptiveFeedbackService.saveResponse(studentId, request);
    return ResponseEntity.ok(
        FeedbackResponseResponseDTO.builder()
            .physicalId(saved.getPhysicalId())
            .studentId(saved.getStudentId())
            .interventionPhysicalId(saved.getInterventionPhysicalId())
            .windowEndAt(
                saved.getWindowEndAt() != null ? saved.getWindowEndAt().toString() : null)
            .postSeverity(saved.getPostSeverity())
            .delta(saved.getDelta())
            .repsInWindow(saved.getRepsInWindow())
            .success(saved.isSuccess())
            .confoundersJson(saved.getConfoundersJson())
            .build());
  }

  @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Upload one evidence snapshot for a confirmed coaching event")
  public ResponseEntity<FormCorrectionEvidenceDTO> uploadEvidence(
      @RequestParam("interventionId") String interventionId,
      @RequestParam("sessionId") String sessionId,
      @RequestParam(value = "taskId", required = false) String taskId,
      @RequestParam(value = "classroomId", required = false) String classroomId,
      @RequestParam(value = "attemptNumber", required = false) Integer attemptNumber,
      @RequestParam("exerciseType") String exerciseType,
      @RequestParam("errorCode") String errorCode,
      @RequestParam(value = "errorDescription", required = false) String errorDescription,
      @RequestParam(value = "correctionText", required = false) String correctionText,
      @RequestParam(value = "capturedAt", required = false) String capturedAt,
      @RequestParam("file") MultipartFile file) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(
        evidenceService.upload(
            studentId,
            interventionId,
            sessionId,
            taskId,
            classroomId,
            attemptNumber,
            exerciseType,
            errorCode,
            errorDescription,
            correctionText,
            capturedAt,
            file));
  }

  @GetMapping("/evidence/students/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Form Correction Evidence Log for a student")
  public ResponseEntity<List<FormCorrectionEvidenceDTO>> listStudentEvidence(
      @PathVariable String studentId,
      @RequestParam(value = "classroomId", required = false) String classroomId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(evidenceService.listForStudent(teacherId, studentId, classroomId));
  }

  @GetMapping(value = "/evidence/{evidenceId}/image", produces = MediaType.IMAGE_JPEG_VALUE)
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Authenticated JPEG stream for one evidence snapshot")
  public ResponseEntity<byte[]> getEvidenceImage(@PathVariable String evidenceId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    byte[] bytes = evidenceService.readImage(teacherId, evidenceId);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(org.springframework.http.CacheControl.noStore().mustRevalidate().cachePrivate())
        .header(org.springframework.http.HttpHeaders.PRAGMA, "no-cache")
        .body(bytes);
  }

  @GetMapping("/profile")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Get current student's coaching profile")
  public ResponseEntity<StudentLearningProfileDTO> getOwnProfile() {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getProfile(studentId));
  }

  @GetMapping("/profile/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Get a student's coaching profile (teacher)")
  public ResponseEntity<StudentLearningProfileDTO> getStudentProfile(
      @PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getInsights(teacherId, studentId).getProfile());
  }

  @GetMapping("/mastery")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "List exercise mastery for current student")
  public ResponseEntity<List<ExerciseMasteryDTO>> getOwnMastery() {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getMastery(studentId));
  }

  @GetMapping("/mastery/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "List exercise mastery for a student (teacher)")
  public ResponseEntity<List<ExerciseMasteryDTO>> getStudentMastery(
      @PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(
        adaptiveFeedbackService.getInsights(teacherId, studentId).getMastery());
  }

  @GetMapping("/difficulty-recommendations/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(
      summary =
          "Soft goalReps/difficulty suggestions for a student (teacher must approve; never auto-applies)")
  public ResponseEntity<List<DifficultyRecommendationDTO>> getDifficultyRecommendations(
      @PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(
        adaptiveFeedbackService.getDifficultyRecommendations(teacherId, studentId));
  }

  @PostMapping("/mastery/{exerciseType}/session")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Record an exercise session toward mastery tracking")
  public ResponseEntity<ExerciseMasteryDTO> recordSession(@PathVariable String exerciseType) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.recordSession(studentId, exerciseType));
  }

  @GetMapping("/insights/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Teacher coaching insights: recurring errors and mastery")
  public ResponseEntity<AdaptiveInsightsDTO> getInsights(@PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getInsights(teacherId, studentId));
  }
}
