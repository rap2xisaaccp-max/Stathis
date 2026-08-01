package edu.cit.stathis.adaptive.service;

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

    if (Boolean.TRUE.equals(request.getStaticControl())) {
      return buildRecommendation(
          FeedbackModality.VERBAL_TEXT,
          errorCode,
          PolicySource.STATIC_CONTROL,
          0.0,
          "STATIC");
    }

    StudentLearningProfile profile = profileService.getOrCreate(studentId);
    Map<String, Object> effectiveness =
        profile.getModalityEffectivenessJson() != null
            ? profile.getModalityEffectivenessJson()
            : Map.of();
    int total =
        profile.getTotalInterventions() == null ? 0 : profile.getTotalInterventions();

    EpsilonGreedyPolicy policy =
        new EpsilonGreedyPolicy(EpsilonGreedyPolicy.DEFAULT_EPSILON, random);
    EpsilonGreedyPolicy.Decision decision =
        policy.decide(
            effectiveness,
            exerciseType,
            errorCode,
            profile.getPreferredModality(),
            total);

    return buildRecommendation(
        decision.modality(),
        errorCode,
        decision.policySource(),
        decision.expectedDelta(),
        "ADAPTIVE");
  }

  private AdaptiveRecommendationDTO buildRecommendation(
      FeedbackModality modality,
      FormErrorCode errorCode,
      PolicySource source,
      double expectedDelta,
      String arm) {
    return AdaptiveRecommendationDTO.builder()
        .modality(modality)
        .errorCode(errorCode)
        .messageCode(errorCode.name())
        .messageText(defaultMessage(errorCode))
        .policySource(source)
        .expectedDelta(expectedDelta)
        .experimentArm(arm)
        .cooldownMs(COOLDOWN_MS)
        .build();
  }

  public static String defaultMessage(FormErrorCode errorCode) {
    return switch (errorCode) {
      case DEPTH_LOW -> "Go deeper to at least parallel.";
      case KNEES_IN -> "Push knees outward over toes.";
      case CHEST_UP -> "Keep chest up and back straight.";
      case PIKE -> "Keep a straight line from head to heels.";
      case SAG -> "Avoid sagging hips.";
      case LOW_ROM -> "Increase trunk flexion.";
      case LOW_VISIBILITY, BODY_NOT_VISIBLE -> "Keep major body parts visible in frame.";
      case LOW_CONFIDENCE -> "Hold still so form can be detected.";
      case LEGS_BENT -> "Keep your legs straighter for better control.";
      default -> "Adjust your form and try again.";
    };
  }
}
