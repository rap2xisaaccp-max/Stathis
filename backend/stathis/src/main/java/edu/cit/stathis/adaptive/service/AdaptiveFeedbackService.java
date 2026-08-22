package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.dto.*;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.repository.FeedbackInterventionRepository;
import edu.cit.stathis.adaptive.repository.FeedbackResponseRepository;
import edu.cit.stathis.classroom.repository.ClassroomRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdaptiveFeedbackService {

  @Autowired private FeedbackInterventionRepository interventionRepository;
  @Autowired private FeedbackResponseRepository responseRepository;
  @Autowired private StudentLearningProfileService profileService;
  @Autowired private ExerciseMasteryService masteryService;
  @Autowired private ClassroomRepository classroomRepository;

  @Transactional
  public AdaptiveBatchIngestResultDTO ingestBatch(String studentId, AdaptiveBatchIngestDTO batch) {
    List<String> interventionIds = new ArrayList<>();
    List<String> responseIds = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    int interventionsFailed = 0;
    int responsesFailed = 0;

    if (batch.getInterventions() != null) {
      for (FeedbackInterventionRequestDTO req : batch.getInterventions()) {
        try {
          FeedbackIntervention saved = saveIntervention(studentId, req);
          interventionIds.add(saved.getPhysicalId());
        } catch (ResponseStatusException ex) {
          interventionsFailed++;
          errors.add(
              "FI:"
                  + (req.getPhysicalId() != null ? req.getPhysicalId() : "?")
                  + ":"
                  + ex.getReason());
        } catch (RuntimeException ex) {
          interventionsFailed++;
          errors.add(
              "FI:"
                  + (req.getPhysicalId() != null ? req.getPhysicalId() : "?")
                  + ":"
                  + ex.getMessage());
        }
      }
    }

    if (batch.getResponses() != null) {
      for (FeedbackResponseRequestDTO req : batch.getResponses()) {
        try {
          FeedbackResponse saved = saveResponse(studentId, req);
          responseIds.add(saved.getPhysicalId());
        } catch (ResponseStatusException ex) {
          responsesFailed++;
          errors.add(
              "FR:"
                  + (req.getInterventionPhysicalId() != null
                      ? req.getInterventionPhysicalId()
                      : "?")
                  + ":"
                  + ex.getReason());
        } catch (RuntimeException ex) {
          responsesFailed++;
          errors.add(
              "FR:"
                  + (req.getInterventionPhysicalId() != null
                      ? req.getInterventionPhysicalId()
                      : "?")
                  + ":"
                  + ex.getMessage());
        }
      }
    }

    return AdaptiveBatchIngestResultDTO.builder()
        .interventionsSaved(interventionIds.size())
        .responsesSaved(responseIds.size())
        .interventionsFailed(interventionsFailed)
        .responsesFailed(responsesFailed)
        .interventionPhysicalIds(interventionIds)
        .responsePhysicalIds(responseIds)
        .errors(errors)
        .updatedProfile(profileService.toDto(profileService.getOrCreate(studentId)))
        .build();
  }

  @Transactional
  public FeedbackIntervention saveIntervention(
      String studentId, FeedbackInterventionRequestDTO req) {
    if (req.getSessionId() == null || req.getSessionId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
    }
    if (req.getExerciseType() == null || req.getExerciseType().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exerciseType is required");
    }

    String physicalId =
        (req.getPhysicalId() != null && !req.getPhysicalId().isBlank())
            ? req.getPhysicalId()
            : "FI-" + UUID.randomUUID().toString().toUpperCase();

    Optional<FeedbackIntervention> existing = interventionRepository.findByPhysicalId(physicalId);
    if (existing.isPresent()) {
      return existing.get();
    }

    FormErrorCode errorCode = req.getErrorCode() != null ? req.getErrorCode() : FormErrorCode.UNKNOWN;
    FeedbackIntervention entity =
        FeedbackIntervention.builder()
            .physicalId(physicalId)
            .studentId(studentId)
            .sessionId(req.getSessionId())
            .taskId(req.getTaskId())
            .classroomId(req.getClassroomId())
            .exerciseType(req.getExerciseType())
            .errorCode(errorCode)
            .modality(
                req.getModality() != null ? req.getModality() : FeedbackModality.VERBAL_TTS)
            .messageCode(req.getMessageCode())
            .messageText(req.getMessageText())
            .deliveredAt(parseTime(req.getDeliveredAt(), OffsetDateTime.now()))
            .baselineSeverity(clamp01(req.getBaselineSeverity()))
            .policySource(
                req.getPolicySource() != null ? req.getPolicySource() : PolicySource.DEFAULT)
            .experimentArm(req.getExperimentArm())
            .build();

    FeedbackIntervention saved = interventionRepository.save(entity);
    if (isCoachableIntervention(saved)) {
      masteryService.recordCoachableError(saved);
      profileService.recordCoachableIntervention(studentId);
    }
    return saved;
  }

  @Transactional
  public FeedbackResponse saveResponse(String studentId, FeedbackResponseRequestDTO req) {
    if (req.getInterventionPhysicalId() == null || req.getInterventionPhysicalId().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "interventionPhysicalId is required");
    }

    FeedbackIntervention intervention =
        interventionRepository
            .findByPhysicalId(req.getInterventionPhysicalId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Intervention not found"));

    if (!studentId.equals(intervention.getStudentId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Intervention belongs to another student");
    }

    Optional<FeedbackResponse> existing =
        responseRepository.findByInterventionPhysicalId(req.getInterventionPhysicalId());
    if (existing.isPresent()) {
      return existing.get();
    }

    double post = clamp01(req.getPostSeverity());
    double delta =
        req.getDelta() != null ? req.getDelta() : (intervention.getBaselineSeverity() - post);
    boolean success = req.getSuccess() != null ? req.getSuccess() : delta >= 0.15;

    String physicalId =
        (req.getPhysicalId() != null && !req.getPhysicalId().isBlank())
            ? req.getPhysicalId()
            : "FR-" + UUID.randomUUID().toString().toUpperCase();

    FeedbackResponse entity =
        FeedbackResponse.builder()
            .physicalId(physicalId)
            .studentId(studentId)
            .interventionPhysicalId(req.getInterventionPhysicalId())
            .windowEndAt(parseTime(req.getWindowEndAt(), OffsetDateTime.now()))
            .postSeverity(post)
            .delta(delta)
            .repsInWindow(req.getRepsInWindow())
            .success(success)
            .confoundersJson(req.getConfoundersJson())
            .build();

    return responseRepository.save(entity);
  }

  public StudentLearningProfileDTO getProfile(String studentId) {
    return profileService.toDto(profileService.getOrCreate(studentId));
  }

  public List<ExerciseMasteryDTO> getMastery(String studentId) {
    return masteryService.listForStudent(studentId);
  }

  public List<DifficultyRecommendationDTO> getDifficultyRecommendations(
      String teacherId, String studentId) {
    assertTeacherCanViewStudent(teacherId, studentId);
    return masteryService.listDifficultyRecommendations(studentId);
  }

  @Transactional
  public ExerciseMasteryDTO recordSession(String studentId, String exerciseType) {
    return masteryService.toDto(masteryService.recordSession(studentId, exerciseType));
  }

  public AdaptiveInsightsDTO getInsights(String teacherId, String studentId) {
    assertTeacherCanViewStudent(teacherId, studentId);

    List<FeedbackIntervention> allInterventions =
        interventionRepository.findByStudentIdOrderByDeliveredAtDesc(studentId);
    List<FeedbackIntervention> coachableInterventions =
        allInterventions.stream().filter(this::isCoachableIntervention).collect(Collectors.toList());
    List<FeedbackIntervention> recent =
        coachableInterventions.stream().limit(25).collect(Collectors.toList());

    Map<String, Long> topErrors = new LinkedHashMap<>();
    for (FeedbackIntervention intervention : coachableInterventions) {
      if (intervention.getErrorCode() == null) {
        continue;
      }
      String key = intervention.getErrorCode().name();
      topErrors.merge(key, 1L, Long::sum);
    }

    StudentLearningProfileDTO profile = getProfile(studentId);
    List<ExerciseMasteryDTO> mastery = getMastery(studentId);

    return AdaptiveInsightsDTO.builder()
        .studentId(studentId)
        .profile(profile)
        .mastery(mastery)
        .preferredModalityByExercise(Map.of())
        .modalityMeanDelta(Map.of())
        .topRecurringErrors(topErrors)
        .totalInterventions(coachableInterventions.size())
        .successfulInterventions(0)
        .overallSuccessRate(0.0)
        .recentInterventions(
            recent.stream().map(this::toInterventionDto).collect(Collectors.toList()))
        .profileHistory(List.of())
        .build();
  }

  public void assertTeacherCanViewStudent(String teacherId, String studentId) {
    boolean sharedClassroom =
        classroomRepository
            .findByClassroomStudents_Student_User_PhysicalId(studentId)
            .stream()
            .anyMatch(c -> teacherId.equals(c.getTeacherId()));
    if (!sharedClassroom) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this student");
    }
  }

  private boolean isCoachableIntervention(FeedbackIntervention intervention) {
    if (intervention == null || intervention.getErrorCode() == null) {
      return false;
    }
    return !intervention.getErrorCode().isTechnical()
        && intervention.getErrorCode() != FormErrorCode.UNKNOWN;
  }

  private FeedbackInterventionResponseDTO toInterventionDto(FeedbackIntervention entity) {
    return FeedbackInterventionResponseDTO.builder()
        .physicalId(entity.getPhysicalId())
        .studentId(entity.getStudentId())
        .sessionId(entity.getSessionId())
        .taskId(entity.getTaskId())
        .classroomId(entity.getClassroomId())
        .exerciseType(entity.getExerciseType())
        .errorCode(entity.getErrorCode())
        .modality(entity.getModality())
        .messageCode(entity.getMessageCode())
        .messageText(entity.getMessageText())
        .deliveredAt(entity.getDeliveredAt() != null ? entity.getDeliveredAt().toString() : null)
        .baselineSeverity(entity.getBaselineSeverity())
        .policySource(entity.getPolicySource())
        .experimentArm(entity.getExperimentArm())
        .correctionDelivered(entity.getMessageText())
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

  private double clamp01(double v) {
    if (Double.isNaN(v)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, v));
  }
}
