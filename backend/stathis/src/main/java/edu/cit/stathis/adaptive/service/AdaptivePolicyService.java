package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.coaching.InstructionIntensity;
import edu.cit.stathis.adaptive.dto.AdaptiveRecommendationDTO;
import edu.cit.stathis.adaptive.dto.AdaptiveRecommendationRequestDTO;
import edu.cit.stathis.adaptive.entity.StudentLearningProfile;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Server-authoritative adaptive feedback policy (epsilon-greedy explore/exploit).
 *
 * <p>Mobile clients may cache the last recommendation for latency, but decisions are produced here
 * and always include {@link PolicySource} for closed-loop logging.
 */
@Service
public class AdaptivePolicyService {

  private static final int COOLDOWN_MS = 8000;

  private final StudentLearningProfileService profileService;
  private final Random random;

  @Autowired
  public AdaptivePolicyService(StudentLearningProfileService profileService) {
    this(profileService, ThreadLocalRandom.current());
  }

  /** Test / simulation constructor with injectable RNG. */
  public AdaptivePolicyService(StudentLearningProfileService profileService, Random random) {
    this.profileService = profileService;
    this.random = random;
  }

  public AdaptiveRecommendationDTO recommend(
      String studentId, AdaptiveRecommendationRequestDTO request) {
    FormErrorCode errorCode =
        request.getErrorCode() != null ? request.getErrorCode() : FormErrorCode.UNKNOWN;
    String exerciseType =
        request.getExerciseType() != null ? request.getExerciseType() : "UNKNOWN";
    // Normalize exercise type so policy composite keys match stored evidence
    exerciseType = edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise(exerciseType);

    InstructionIntensity intensity = InstructionIntensity.REMINDER;

    StudentLearningProfile profile = profileService.getOrCreate(studentId);
    Map<String, Object> effectiveness =
        profile.getModalityEffectivenessJson() != null
            ? profile.getModalityEffectivenessJson()
            : Map.of();
    int total =
        profile.getTotalInterventions() == null ? 0 : profile.getTotalInterventions();
    FeedbackModality preferred = resolvePreferredForExercise(profile, exerciseType);

    EpsilonGreedyPolicy policy =
        new EpsilonGreedyPolicy(EpsilonGreedyPolicy.DEFAULT_EPSILON, random);
    EpsilonGreedyPolicy.Decision decision =
        policy.decide(
            effectiveness,
            exerciseType,
            errorCode,
            preferred,
            total);

    return buildRecommendation(
        decision.modality(),
        errorCode,
        exerciseType,
        intensity,
        decision.policySource(),
        decision.expectedDelta(),
        "ADAPTIVE");
  }

  private AdaptiveRecommendationDTO buildRecommendation(
      FeedbackModality modality,
      FormErrorCode errorCode,
      String exerciseType,
      InstructionIntensity intensity,
      PolicySource source,
      double expectedDelta,
      String arm) {
    return AdaptiveRecommendationDTO.builder()
        .modality(modality)
        .errorCode(errorCode)
        .messageCode(CoachingInstructionCatalog.messageCode(exerciseType, errorCode, intensity))
        .messageText(CoachingInstructionCatalog.messageText(exerciseType, errorCode, intensity))
        .policySource(source)
        .expectedDelta(expectedDelta)
        .experimentArm(arm)
        .cooldownMs(COOLDOWN_MS)
        .build();
  }

  /** Prefer learned/exploring per-exercise modality; fall back to global preferred. */
  @SuppressWarnings("unchecked")
  static FeedbackModality resolvePreferredForExercise(
      StudentLearningProfile profile, String exerciseType) {
    Map<String, Object> byExercise = profile.getPreferredModalityByExerciseJson();
    if (byExercise != null && !byExercise.isEmpty()) {
      String normalized = CoachingInstructionCatalog.normalizeExercise(exerciseType);
      Object row = byExercise.get(exerciseType);
      if (row == null) {
        row = byExercise.get(normalized);
      }
      if (row == null) {
        for (Map.Entry<String, Object> e : byExercise.entrySet()) {
          if (e.getKey() != null
              && CoachingInstructionCatalog.normalizeExercise(e.getKey()).equals(normalized)) {
            row = e.getValue();
            break;
          }
        }
      }
      if (row instanceof Map<?, ?> map) {
        Object modality = map.get("modality");
        if (modality != null) {
          try {
            return FeedbackModality.valueOf(String.valueOf(modality));
          } catch (IllegalArgumentException ignored) {
            // fall through
          }
        }
      }
    }
    return profile.getPreferredModality() != null
        ? profile.getPreferredModality()
        : FeedbackModality.VERBAL_TEXT;
  }

  /** @deprecated Prefer {@link CoachingInstructionCatalog#messageText}. Kept for tests. */
  public static String defaultMessage(FormErrorCode errorCode) {
    return CoachingInstructionCatalog.messageText("UNKNOWN", errorCode, InstructionIntensity.REMINDER);
  }
}
