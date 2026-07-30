package edu.cit.stathis.adaptive.controller;

import edu.cit.stathis.adaptive.dto.*;
import edu.cit.stathis.adaptive.service.AdaptiveFeedbackService;
import edu.cit.stathis.auth.service.PhysicalIdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/adaptive")
@Tag(
    name = "Adaptive Learning",
    description = "Closed-loop adaptive physical skill learning interventions and profiles")
public class AdaptiveController {

  @Autowired private AdaptiveFeedbackService adaptiveFeedbackService;
  @Autowired private PhysicalIdService physicalIdService;

  @PostMapping("/batch")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Batch ingest interventions and responses from a live exercise session")
  public ResponseEntity<AdaptiveBatchIngestResultDTO> ingestBatch(
      @RequestBody AdaptiveBatchIngestDTO batch) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.ingestBatch(studentId, batch));
  }

  @PostMapping("/interventions")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Log a single feedback intervention")
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
            .build());
  }

  @PostMapping("/responses")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Log measured response to an intervention (closed loop)")
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

  @PostMapping("/recommend")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Get next adaptive feedback recommendation for an error")
  public ResponseEntity<AdaptiveRecommendationDTO> recommend(
      @RequestBody AdaptiveRecommendationRequestDTO request) {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.recommend(studentId, request));
  }

  @GetMapping("/profile")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Get current student's movement learning profile")
  public ResponseEntity<StudentLearningProfileDTO> getOwnProfile() {
    String studentId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getProfile(studentId));
  }

  @GetMapping("/profile/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Get a student's learning profile (teacher)")
  public ResponseEntity<StudentLearningProfileDTO> getStudentProfile(
      @PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    // Authorization enforced via insights path; profile alone still checks classroom share
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
  @Operation(summary = "Teacher adaptive insights: modality effectiveness, errors, mastery")
  public ResponseEntity<AdaptiveInsightsDTO> getInsights(@PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getInsights(teacherId, studentId));
  }

  @GetMapping("/evaluation/{studentId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "RCT evaluation summary metrics for a student")
  public ResponseEntity<AdaptiveEvaluationSummaryDTO> getEvaluation(
      @PathVariable String studentId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(adaptiveFeedbackService.getEvaluationSummary(teacherId, studentId));
  }

  @GetMapping("/evaluation/classroom/{classroomId}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(
      summary =
          "Classroom RCT/ablation export: adaptive vs static mean Δ, success lift, Cohen's d")
  public ResponseEntity<ClassroomEvaluationDTO> getClassroomEvaluation(
      @PathVariable String classroomId) {
    String teacherId = physicalIdService.getCurrentUserPhysicalId();
    return ResponseEntity.ok(
        adaptiveFeedbackService.getClassroomEvaluation(teacherId, classroomId));
  }
}
