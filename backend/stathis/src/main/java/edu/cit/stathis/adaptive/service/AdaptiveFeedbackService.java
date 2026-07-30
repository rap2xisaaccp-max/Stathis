package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.dto.*;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.repository.FeedbackInterventionRepository;
import edu.cit.stathis.adaptive.repository.FeedbackResponseRepository;
import edu.cit.stathis.classroom.entity.Classroom;
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
  @Autowired private AdaptivePolicyService policyService;
  @Autowired private ClassroomRepository classroomRepository;

  @Transactional
  public AdaptiveBatchIngestResultDTO ingestBatch(String studentId, AdaptiveBatchIngestDTO batch) {
    List<String> interventionIds = new ArrayList<>();
    List<String> responseIds = new ArrayList<>();

    if (batch.getInterventions() != null) {
      for (FeedbackInterventionRequestDTO req : batch.getInterventions()) {
        FeedbackIntervention saved = saveIntervention(studentId, req);
        interventionIds.add(saved.getPhysicalId());
      }
    }

    if (batch.getResponses() != null) {
      for (FeedbackResponseRequestDTO req : batch.getResponses()) {
        FeedbackResponse saved = saveResponse(studentId, req);
        responseIds.add(saved.getPhysicalId());
      }
    }

    return AdaptiveBatchIngestResultDTO.builder()
        .interventionsSaved(interventionIds.size())
        .responsesSaved(responseIds.size())
        .interventionPhysicalIds(interventionIds)
        .responsePhysicalIds(responseIds)
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

    FeedbackIntervention entity =
        FeedbackIntervention.builder()
            .physicalId(physicalId)
            .studentId(studentId)
            .sessionId(req.getSessionId())
            .taskId(req.getTaskId())
            .classroomId(req.getClassroomId())
            .exerciseType(req.getExerciseType())
            .errorCode(req.getErrorCode() != null ? req.getErrorCode() : FormErrorCode.UNKNOWN)
            .modality(
                req.getModality() != null ? req.getModality() : FeedbackModality.VERBAL_TEXT)
            .messageCode(req.getMessageCode())
            .messageText(req.getMessageText())
            .deliveredAt(parseTime(req.getDeliveredAt(), OffsetDateTime.now()))
            .baselineSeverity(clamp01(req.getBaselineSeverity()))
            .policySource(
                req.getPolicySource() != null ? req.getPolicySource() : PolicySource.DEFAULT)
            .experimentArm(req.getExperimentArm())
            .build();

    return interventionRepository.save(entity);
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
    boolean success =
        req.getSuccess() != null
            ? req.getSuccess()
            : StudentLearningProfileService.isSuccessfulDelta(delta);

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

    FeedbackResponse saved = responseRepository.save(entity);
    profileService.applyResponse(intervention, saved);
    masteryService.applyResponse(intervention, saved);
    return saved;
  }

  public AdaptiveRecommendationDTO recommend(
      String studentId, AdaptiveRecommendationRequestDTO request) {
    return policyService.recommend(studentId, request);
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
    List<FeedbackIntervention> recent =
        allInterventions.stream().limit(25).collect(Collectors.toList());

    Map<String, Long> topErrors =
        allInterventions.stream()
            .collect(
                Collectors.groupingBy(i -> i.getErrorCode().name(), Collectors.counting()));

    Map<String, Double> modalityMeanDelta = new HashMap<>();
    StudentLearningProfileDTO profile = getProfile(studentId);
    if (profile.getModalityEffectivenessJson() != null) {
      for (FeedbackModality modality : FeedbackModality.values()) {
        Object raw = profile.getModalityEffectivenessJson().get(modality.name());
        if (raw instanceof Map<?, ?> map) {
          Object mean = map.get("meanDelta");
          if (mean instanceof Number number) {
            modalityMeanDelta.put(modality.name(), number.doubleValue());
          }
        }
      }
    }

    long total = interventionRepository.countByStudentId(studentId);
    long successes = responseRepository.countByStudentIdAndSuccessTrue(studentId);
    List<ExerciseMasteryDTO> mastery = getMastery(studentId);
    List<ProfileHistoryPointDTO> history = profileService.listHistory(studentId);

    // Seed a current point so the timeline chart is useful before the 5th snapshot.
    if (history.isEmpty()
        && ((profile.getTotalInterventions() != null && profile.getTotalInterventions() > 0)
            || (mastery != null && !mastery.isEmpty()))) {
      double meanMastery =
          mastery == null || mastery.isEmpty()
              ? 0.0
              : mastery.stream().mapToDouble(ExerciseMasteryDTO::getMasteryLevel).average().orElse(0.0);
      history =
          List.of(
              ProfileHistoryPointDTO.builder()
                  .physicalId("CURRENT")
                  .createdAt(java.time.OffsetDateTime.now().toString())
                  .reason("live")
                  .learningRateEstimate(profile.getLearningRateEstimate())
                  .consistencyScore(profile.getConsistencyScore())
                  .meanMasteryLevel(meanMastery)
                  .totalInterventions(profile.getTotalInterventions())
                  .preferredModality(
                      profile.getPreferredModality() != null
                          ? profile.getPreferredModality().name()
                          : null)
                  .build());
    }

    return AdaptiveInsightsDTO.builder()
        .studentId(studentId)
        .profile(profile)
        .mastery(mastery)
        .modalityMeanDelta(modalityMeanDelta)
        .topRecurringErrors(topErrors)
        .totalInterventions(total)
        .successfulInterventions(successes)
        .overallSuccessRate(total == 0 ? 0.0 : (double) successes / Math.max(1, total))
        .recentInterventions(
            recent.stream().map(this::toInterventionDto).collect(Collectors.toList()))
        .profileHistory(history)
        .build();
  }

  public AdaptiveEvaluationSummaryDTO getEvaluationSummary(String teacherId, String studentId) {
    AdaptiveInsightsDTO insights = getInsights(teacherId, studentId);
    List<FeedbackIntervention> all =
        interventionRepository.findByStudentIdOrderByDeliveredAtDesc(studentId);
    String arm =
        all.stream()
            .map(FeedbackIntervention::getExperimentArm)
            .filter(a -> a != null && !a.isBlank())
            .findFirst()
            .orElse("ADAPTIVE");

    List<FeedbackResponse> responses =
        responseRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    double meanDelta = 0.0;
    int deltaN = 0;
    for (FeedbackResponse response : responses) {
      meanDelta += response.getDelta();
      deltaN++;
    }
    if (deltaN > 0) {
      meanDelta /= deltaN;
    }

    List<ExerciseMasteryDTO> mastery = insights.getMastery();
    double meanMastery =
        mastery == null || mastery.isEmpty()
            ? 0.0
            : mastery.stream().mapToDouble(ExerciseMasteryDTO::getMasteryLevel).average().orElse(0.0);
    int sessions =
        mastery == null
            ? 0
            : mastery.stream()
                .mapToInt(m -> m.getSessionsCount() == null ? 0 : m.getSessionsCount())
                .sum();

    Map<String, Long> byArm =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    i ->
                        i.getExperimentArm() == null || i.getExperimentArm().isBlank()
                            ? "ADAPTIVE"
                            : i.getExperimentArm(),
                    Collectors.counting()));
    long practiceCount =
        byArm.entrySet().stream()
            .filter(e -> RctEvaluationMetrics.isPracticeArm(e.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
    long taskCount = Math.max(0, all.size() - practiceCount);

    return AdaptiveEvaluationSummaryDTO.builder()
        .studentId(studentId)
        .experimentArm(arm)
        .totalInterventions(insights.getTotalInterventions())
        .successfulInterventions(insights.getSuccessfulInterventions())
        .successRate(insights.getOverallSuccessRate())
        .meanDelta(meanDelta)
        .meanDeltaByModality(insights.getModalityMeanDelta())
        .errorFrequency(insights.getTopRecurringErrors())
        .meanMasteryLevel(meanMastery)
        .sessionsTracked(sessions)
        .practiceInterventions(practiceCount)
        .taskInterventions(taskCount)
        .interventionsByArm(byArm)
        .build();
  }

  public ClassroomEvaluationDTO getClassroomEvaluation(String teacherId, String classroomId) {
    Classroom classroom =
        classroomRepository
            .findByPhysicalId(classroomId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classroom not found"));
    if (!teacherId.equals(classroom.getTeacherId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this classroom");
    }

    List<String> studentIds =
        classroom.getClassroomStudents() == null
            ? List.of()
            : classroom.getClassroomStudents().stream()
                .filter(cs -> cs.getStudent() != null && cs.getStudent().getUser() != null)
                .map(cs -> cs.getStudent().getUser().getPhysicalId())
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

    List<AdaptiveEvaluationSummaryDTO> studentSummaries = new ArrayList<>();
    Map<String, Long> interventionsByArm = new HashMap<>();
    List<Double> adaptiveDeltas = new ArrayList<>();
    List<Double> staticDeltas = new ArrayList<>();
    long adaptiveSuccesses = 0;
    long staticSuccesses = 0;
    long totalInterventions = 0;
    long successfulInterventions = 0;
    long practiceInterventions = 0;
    long taskInterventions = 0;
    double masterySum = 0.0;
    int masteryN = 0;

    for (String studentId : studentIds) {
      AdaptiveEvaluationSummaryDTO summary = getEvaluationSummary(teacherId, studentId);
      studentSummaries.add(summary);
      totalInterventions += summary.getTotalInterventions();
      successfulInterventions += summary.getSuccessfulInterventions();
      practiceInterventions += summary.getPracticeInterventions();
      taskInterventions += summary.getTaskInterventions();
      if (summary.getMeanMasteryLevel() != null) {
        masterySum += summary.getMeanMasteryLevel();
        masteryN++;
      }
      if (summary.getInterventionsByArm() != null) {
        summary
            .getInterventionsByArm()
            .forEach((k, v) -> interventionsByArm.merge(k, v, Long::sum));
      }

      // Collect per-response deltas by base arm for Cohen's d.
      Map<String, String> interventionArm =
          interventionRepository.findByStudentIdOrderByDeliveredAtDesc(studentId).stream()
              .collect(
                  Collectors.toMap(
                      FeedbackIntervention::getPhysicalId,
                      i ->
                          i.getExperimentArm() == null || i.getExperimentArm().isBlank()
                              ? "ADAPTIVE"
                              : i.getExperimentArm(),
                      (a, b) -> a));
      for (FeedbackResponse response :
          responseRepository.findByStudentIdOrderByCreatedAtDesc(studentId)) {
        String rawArm =
            interventionArm.getOrDefault(response.getInterventionPhysicalId(), "ADAPTIVE");
        String base = RctEvaluationMetrics.baseArm(rawArm);
        if ("STATIC".equals(base)) {
          staticDeltas.add(response.getDelta());
          if (response.isSuccess()) {
            staticSuccesses++;
          }
        } else {
          adaptiveDeltas.add(response.getDelta());
          if (response.isSuccess()) {
            adaptiveSuccesses++;
          }
        }
      }
    }

    RctEvaluationMetrics.ArmStats adaptiveStats =
        RctEvaluationMetrics.armStats("ADAPTIVE", adaptiveDeltas, adaptiveSuccesses, null);
    RctEvaluationMetrics.ArmStats staticStats =
        RctEvaluationMetrics.armStats("STATIC", staticDeltas, staticSuccesses, null);
    RctEvaluationMetrics.AblationContrast contrast =
        RctEvaluationMetrics.contrast(adaptiveStats, staticStats);
    double d = RctEvaluationMetrics.cohensD(adaptiveDeltas, staticDeltas);

    return ClassroomEvaluationDTO.builder()
        .classroomId(classroomId)
        .studentCount(studentIds.size())
        .totalInterventions(totalInterventions)
        .successfulInterventions(successfulInterventions)
        .overallSuccessRate(
            RctEvaluationMetrics.successRate(successfulInterventions, totalInterventions))
        .meanDelta(
            RctEvaluationMetrics.mean(
                studentSummaries.stream()
                    .map(AdaptiveEvaluationSummaryDTO::getMeanDelta)
                    .toList()))
        .meanMasteryLevel(masteryN == 0 ? 0.0 : masterySum / masteryN)
        .practiceInterventions(practiceInterventions)
        .taskInterventions(taskInterventions)
        .interventionsByArm(interventionsByArm)
        .adaptiveMeanDelta(adaptiveStats.meanDelta())
        .staticMeanDelta(staticStats.meanDelta())
        .meanDeltaLift(contrast.meanDeltaLift())
        .successRateLift(contrast.successRateLift())
        .cohensD(d)
        .adaptiveOutperformsOnDelta(contrast.adaptiveOutperformsOnDelta())
        .students(studentSummaries)
        .build();
  }

  private void assertTeacherCanViewStudent(String teacherId, String studentId) {
    boolean sharedClassroom =
        classroomRepository
            .findByClassroomStudents_Student_User_PhysicalId(studentId)
            .stream()
            .anyMatch(c -> teacherId.equals(c.getTeacherId()));
    if (!sharedClassroom) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this student");
    }
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
