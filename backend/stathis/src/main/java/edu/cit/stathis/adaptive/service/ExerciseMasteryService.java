package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.dto.DifficultyRecommendationDTO;
import edu.cit.stathis.adaptive.dto.ExerciseMasteryDTO;
import edu.cit.stathis.adaptive.entity.ExerciseMastery;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.repository.ExerciseMasteryRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseMasteryService {

  @Autowired private ExerciseMasteryRepository masteryRepository;

  @Transactional
  public ExerciseMastery getOrCreate(String studentId, String exerciseType) {
    String normalized = CoachingInstructionCatalog.normalizeExercise(exerciseType);
    return masteryRepository
        .findByStudentIdAndExerciseType(studentId, normalized)
        .orElseGet(
            () ->
                masteryRepository.save(
                    ExerciseMastery.builder()
                        .physicalId("EM-" + UUID.randomUUID().toString().toUpperCase())
                        .studentId(studentId)
                        .exerciseType(normalized)
                        .masteryLevel(0.0)
                        .commonErrorsJson(new HashMap<>())
                        .sessionsCount(0)
                        .recommendedDifficulty("BEGINNER")
                        .build()));
  }

  public List<ExerciseMasteryDTO> listForStudent(String studentId) {
    return masteryRepository.findByStudentId(studentId).stream()
        .map(this::toDto)
        .collect(Collectors.toList());
  }

  public List<DifficultyRecommendationDTO> listDifficultyRecommendations(String studentId) {
    return masteryRepository.findByStudentId(studentId).stream()
        .map(this::toDifficultyRecommendation)
        .collect(Collectors.toList());
  }

  public ExerciseMasteryDTO toDto(ExerciseMastery mastery) {
    SoftRecommendation soft = softRecommendationFor(mastery);
    return ExerciseMasteryDTO.builder()
        .physicalId(mastery.getPhysicalId())
        .studentId(mastery.getStudentId())
        .exerciseType(mastery.getExerciseType())
        .masteryLevel(mastery.getMasteryLevel())
        .commonErrorsJson(mastery.getCommonErrorsJson())
        .sessionsCount(mastery.getSessionsCount())
        .medianTimeToCorrectionMs(mastery.getMedianTimeToCorrectionMs())
        .recommendedDifficulty(soft.difficulty)
        .recommendedGoalReps(soft.goalReps)
        .recommendationRationale(soft.rationale)
        .requiresTeacherApproval(true)
        .lastSessionAt(
            mastery.getLastSessionAt() != null ? mastery.getLastSessionAt().toString() : null)
        .build();
  }

  public DifficultyRecommendationDTO toDifficultyRecommendation(ExerciseMastery mastery) {
    SoftRecommendation soft = softRecommendationFor(mastery);
    return DifficultyRecommendationDTO.builder()
        .studentId(mastery.getStudentId())
        .exerciseType(mastery.getExerciseType())
        .masteryLevel(mastery.getMasteryLevel())
        .sessionsCount(mastery.getSessionsCount() == null ? 0 : mastery.getSessionsCount())
        .recommendedDifficulty(soft.difficulty)
        .recommendedGoalReps(soft.goalReps)
        .rationale(soft.rationale)
        .requiresTeacherApproval(true)
        .topErrors(ExerciseMasteryMath.topErrorCodes(mastery.getCommonErrorsJson(), 3))
        .build();
  }

  /**
   * Sessions alone do not imply form mastery. When sessions ran but mastery is still ~0,
   * withhold Beginner/goalReps so teachers see an honest "insufficient form-correction data"
   * signal instead of a false difficulty suggestion.
   */
  public static boolean isInsufficientFormEvidence(Integer sessionsCount, double masteryLevel) {
    int sessions = sessionsCount == null ? 0 : sessionsCount;
    return sessions > 0 && masteryLevel < 0.05;
  }

  private SoftRecommendation softRecommendationFor(ExerciseMastery mastery) {
    double level = mastery.getMasteryLevel();
    if (isInsufficientFormEvidence(mastery.getSessionsCount(), level)) {
      return new SoftRecommendation(
          null,
          null,
          "Insufficient form-correction data. Soft difficulty suggestions require measured "
              + "coaching responses that improve form — session count alone is not enough.");
    }
    String difficulty =
        mastery.getRecommendedDifficulty() != null
            ? mastery.getRecommendedDifficulty()
            : ExerciseMasteryMath.recommendDifficulty(level);
    int goalReps = ExerciseMasteryMath.recommendGoalReps(difficulty, null);
    return new SoftRecommendation(
        difficulty,
        goalReps,
        ExerciseMasteryMath.buildRationale(
            level, difficulty, goalReps, mastery.getCommonErrorsJson()));
  }

  private record SoftRecommendation(String difficulty, Integer goalReps, String rationale) {}

  @Transactional
  public ExerciseMastery applyResponse(
      FeedbackIntervention intervention, FeedbackResponse response) {
    ExerciseMastery mastery =
        getOrCreate(intervention.getStudentId(), intervention.getExerciseType());

    Map<String, Object> errors =
        mastery.getCommonErrorsJson() != null
            ? new HashMap<>(mastery.getCommonErrorsJson())
            : new HashMap<>();
    String errorKey = intervention.getErrorCode().name();
    int count = ((Number) errors.getOrDefault(errorKey, 0)).intValue() + 1;
    errors.put(errorKey, count);
    mastery.setCommonErrorsJson(errors);

    double updated =
        ExerciseMasteryMath.updateMastery(
            mastery.getMasteryLevel(), response.getDelta(), response.isSuccess());
    mastery.setMasteryLevel(updated);
    mastery.setRecommendedDifficulty(ExerciseMasteryMath.recommendDifficulty(updated));
    mastery.setLastSessionAt(OffsetDateTime.now());

    if (response.isSuccess()
        && intervention.getDeliveredAt() != null
        && response.getWindowEndAt() != null) {
      long correctionMs =
          java.time.Duration.between(intervention.getDeliveredAt(), response.getWindowEndAt())
              .toMillis();
      Long priorMedian = mastery.getMedianTimeToCorrectionMs();
      if (priorMedian == null) {
        mastery.setMedianTimeToCorrectionMs(correctionMs);
      } else {
        mastery.setMedianTimeToCorrectionMs((priorMedian + correctionMs) / 2);
      }
    }

    return masteryRepository.save(mastery);
  }

  @Transactional
  public ExerciseMastery recordSession(String studentId, String exerciseType) {
    ExerciseMastery mastery =
        getOrCreate(studentId, CoachingInstructionCatalog.normalizeExercise(exerciseType));
    int sessions = mastery.getSessionsCount() == null ? 0 : mastery.getSessionsCount();
    mastery.setSessionsCount(sessions + 1);
    mastery.setLastSessionAt(OffsetDateTime.now());
    // Keep difficulty label aligned with current mastery even if no new responses arrived.
    mastery.setRecommendedDifficulty(
        ExerciseMasteryMath.recommendDifficulty(mastery.getMasteryLevel()));
    return masteryRepository.save(mastery);
  }
}
