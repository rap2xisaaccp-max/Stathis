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
  @Autowired private AdaptiveArmRollupService armRollupService;

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
    // Technical camera/detection signals must not drive preferred-modality learning.
    if (intervention.getErrorCode() == null || !intervention.getErrorCode().isTechnical()) {
      profileService.applyResponse(intervention, saved);
      masteryService.applyResponse(intervention, saved);
    }
    armRollupService.recordResponse(intervention, saved);
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
        RctEvaluationMetrics.countDistinctSessionErrors(
            allInterventions.stream()
                .map(FeedbackIntervention::getSessionId)
                .collect(Collectors.toList()),
            allInterventions.stream()
                .map(i -> i.getErrorCode() != null ? i.getErrorCode().name() : null)
                .collect(Collectors.toList()));

    StudentLearningProfileDTO profile = getProfile(studentId);
    List<FeedbackResponse> allResponses =
        responseRepository.findByStudentIdOrderByCreatedAtDesc(studentId);

    Map<String, Double> modalityMeanDelta =
        extractModalityMeanDelta(profile.getModalityEffectivenessJson());
    // Fallback: derive from closed-loop FR↔FI when profile buckets are empty/unreadable
    if (modalityMeanDelta.isEmpty() && !allResponses.isEmpty()) {
      modalityMeanDelta = modalityMeanDeltaFromResponses(allInterventions, allResponses);
    }

    // Closed-loop success: successes / response pairs (not FI count).
    long closedPairs = allResponses.size();
    long successes = allResponses.stream().filter(FeedbackResponse::isSuccess).count();
    List<ExerciseMasteryDTO> mastery = getMastery(studentId);
    List<ProfileHistoryPointDTO> history = profileService.listHistory(studentId);

    // Seed a current point so the timeline chart is useful before the 5th snapshot.
    // Include FI/FR presence so Score-only gaps don't hide timeline when adaptive telemetry exists.
    if (history.isEmpty()
        && ((profile.getTotalInterventions() != null && profile.getTotalInterventions() > 0)
            || (mastery != null && !mastery.isEmpty())
            || !allInterventions.isEmpty()
            || closedPairs > 0)) {
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

    Map<String, FeedbackResponse> responseByIntervention = new HashMap<>();
    for (FeedbackResponse response : allResponses) {
      if (response.getInterventionPhysicalId() != null) {
        responseByIntervention.putIfAbsent(response.getInterventionPhysicalId(), response);
      }
    }

    return AdaptiveInsightsDTO.builder()
        .studentId(studentId)
        .profile(profile)
        .mastery(mastery)
        .preferredModalityByExercise(profile.getPreferredModalityByExercise())
        .modalityMeanDelta(modalityMeanDelta)
        .topRecurringErrors(topErrors)
        .totalInterventions(closedPairs)
        .successfulInterventions(successes)
        .overallSuccessRate(RctEvaluationMetrics.successRate(successes, closedPairs))
        .recentInterventions(
            recent.stream()
                .map(fi -> toInterventionDto(fi, responseByIntervention.get(fi.getPhysicalId())))
                .collect(Collectors.toList()))
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
    long totalClosedPairs = 0;
    long successfulClosedPairs = 0;
    long practiceInterventions = 0;
    long taskInterventions = 0;
    double masterySum = 0.0;
    int masteryN = 0;

    for (String studentId : studentIds) {
      AdaptiveEvaluationSummaryDTO summary = getEvaluationSummary(teacherId, studentId);
      studentSummaries.add(summary);
      if (summary.getMeanMasteryLevel() != null) {
        masterySum += summary.getMeanMasteryLevel();
        masteryN++;
      }

      // Classroom-scoped FI only (exclude blank classroomId to avoid cross-room bleed).
      List<FeedbackIntervention> classroomInterventions =
          interventionRepository.findByStudentIdOrderByDeliveredAtDesc(studentId).stream()
              .filter(i -> RctEvaluationMetrics.matchesClassroom(i.getClassroomId(), classroomId))
              .toList();

      Map<String, String> interventionArm = new HashMap<>();
      java.util.HashSet<String> classroomInterventionIds = new java.util.HashSet<>();
      for (FeedbackIntervention intervention : classroomInterventions) {
        classroomInterventionIds.add(intervention.getPhysicalId());
        String arm =
            intervention.getExperimentArm() == null || intervention.getExperimentArm().isBlank()
                ? "ADAPTIVE"
                : intervention.getExperimentArm();
        interventionArm.put(intervention.getPhysicalId(), arm);
        interventionsByArm.merge(arm, 1L, Long::sum);
        if (RctEvaluationMetrics.isPracticeArm(arm)) {
          practiceInterventions++;
        } else {
          taskInterventions++;
        }
      }

      for (FeedbackResponse response :
          responseRepository.findByStudentIdOrderByCreatedAtDesc(studentId)) {
        if (!classroomInterventionIds.contains(response.getInterventionPhysicalId())) {
          continue;
        }
        totalClosedPairs++;
        if (response.isSuccess()) {
          successfulClosedPairs++;
        }
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
    boolean bothArmsHaveData = !adaptiveDeltas.isEmpty() && !staticDeltas.isEmpty();
    Double adaptiveMeanDelta = adaptiveDeltas.isEmpty() ? null : adaptiveStats.meanDelta();
    Double staticMeanDelta = staticDeltas.isEmpty() ? null : staticStats.meanDelta();
    Double meanDeltaLift = bothArmsHaveData ? contrast.meanDeltaLift() : null;
    Double successRateLift = bothArmsHaveData ? contrast.successRateLift() : null;
    Double cohensD =
        bothArmsHaveData ? RctEvaluationMetrics.cohensD(adaptiveDeltas, staticDeltas) : null;

    // Classroom mean Δ from classroom-scoped closed responses only.
    List<Double> allClassroomDeltas = new ArrayList<>();
    allClassroomDeltas.addAll(adaptiveDeltas);
    allClassroomDeltas.addAll(staticDeltas);

    return ClassroomEvaluationDTO.builder()
        .classroomId(classroomId)
        .studentCount(studentIds.size())
        .totalInterventions(totalClosedPairs)
        .successfulInterventions(successfulClosedPairs)
        .overallSuccessRate(
            RctEvaluationMetrics.successRate(successfulClosedPairs, totalClosedPairs))
        .meanDelta(RctEvaluationMetrics.mean(allClassroomDeltas))
        .meanMasteryLevel(masteryN == 0 ? 0.0 : masterySum / masteryN)
        .practiceInterventions(practiceInterventions)
        .taskInterventions(taskInterventions)
        .interventionsByArm(interventionsByArm)
        .adaptiveMeanDelta(adaptiveMeanDelta)
        .staticMeanDelta(staticMeanDelta)
        .meanDeltaLift(meanDeltaLift)
        .successRateLift(successRateLift)
        .cohensD(cohensD)
        .adaptiveOutperformsOnDelta(bothArmsHaveData && contrast.adaptiveOutperformsOnDelta())
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

  /** Read modality meanΔ from profile JSON buckets (modality enum keys only). */
  private Map<String, Double> extractModalityMeanDelta(Map<String, Object> effectiveness) {
    Map<String, Double> modalityMeanDelta = new HashMap<>();
    if (effectiveness == null || effectiveness.isEmpty()) {
      return modalityMeanDelta;
    }
    for (FeedbackModality modality : FeedbackModality.values()) {
      Object raw = effectiveness.get(modality.name());
      if (!(raw instanceof Map<?, ?> map)) {
        continue;
      }
      Object mean = map.get("meanDelta");
      if (mean == null) {
        mean = map.get("bayesianMeanDelta");
      }
      if (mean instanceof Number number) {
        modalityMeanDelta.put(modality.name(), number.doubleValue());
      }
    }
    return modalityMeanDelta;
  }

  /** Aggregate mean Δ per modality from closed FI↔FR pairs (teacher chart fallback). */
  private Map<String, Double> modalityMeanDeltaFromResponses(
      List<FeedbackIntervention> interventions, List<FeedbackResponse> responses) {
    Map<String, String> interventionModality = new HashMap<>();
    for (FeedbackIntervention intervention : interventions) {
      if (intervention.getPhysicalId() != null && intervention.getModality() != null) {
        interventionModality.put(intervention.getPhysicalId(), intervention.getModality().name());
      }
    }
    Map<String, List<Double>> deltasByModality = new HashMap<>();
    for (FeedbackResponse response : responses) {
      String modality = interventionModality.get(response.getInterventionPhysicalId());
      if (modality == null || modality.isBlank()) {
        continue;
      }
      deltasByModality
          .computeIfAbsent(modality, k -> new ArrayList<>())
          .add(response.getDelta());
    }
    Map<String, Double> means = new HashMap<>();
    for (Map.Entry<String, List<Double>> entry : deltasByModality.entrySet()) {
      means.put(entry.getKey(), RctEvaluationMetrics.mean(entry.getValue()));
    }
    return means;
  }

  private FeedbackInterventionResponseDTO toInterventionDto(FeedbackIntervention entity) {
    return toInterventionDto(entity, null);
  }

  private FeedbackInterventionResponseDTO toInterventionDto(
      FeedbackIntervention entity, FeedbackResponse response) {
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
        .responseSuccess(response != null ? response.isSuccess() : null)
        .responseDelta(response != null ? response.getDelta() : null)
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
