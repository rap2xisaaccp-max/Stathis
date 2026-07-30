package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.service.EpsilonGreedyPolicy;
import edu.cit.stathis.adaptive.service.ProfileEffectivenessMath;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Phase 5: simulate a student with known modality response rates and verify the epsilon-greedy
 * policy learns to prefer the better modality versus a static verbal baseline.
 */
class AdaptivePolicySimulationTest {

  /**
   * Synthetic student: visual feedback yields high deltas; verbal yields near-zero improvement.
   */
  private static double simulateStudentDelta(FeedbackModality modality, Random random) {
    return switch (modality) {
      case VISUAL_HIGHLIGHT -> 0.35 + random.nextGaussian() * 0.05;
      case VERBAL_TTS -> 0.18 + random.nextGaussian() * 0.05;
      case VERBAL_TEXT, DEMONSTRATION -> 0.05 + random.nextGaussian() * 0.03;
    };
  }

  @Test
  void adaptivePolicyLearnsPreferredModalityOverStaticBaseline() {
    Random rng = new Random(42);
    Map<String, Object> effectiveness = new HashMap<>();
    FeedbackModality preferred = FeedbackModality.VERBAL_TEXT;
    int total = 0;

    // Pure exploit after warm-up evidence collection with forced explore mix early
    EpsilonGreedyPolicy learner = new EpsilonGreedyPolicy(0.25, rng);
    EpsilonGreedyPolicy staticVerbal = new EpsilonGreedyPolicy(0.0, new Random(7));

    double adaptiveCumulativeDelta = 0.0;
    double staticCumulativeDelta = 0.0;
    int rounds = 60;
    EnumMap<FeedbackModality, Integer> adaptiveCounts = new EnumMap<>(FeedbackModality.class);
    for (FeedbackModality m : EpsilonGreedyPolicy.ACTIVE_MODALITIES) {
      adaptiveCounts.put(m, 0);
    }

    for (int i = 0; i < rounds; i++) {
      EpsilonGreedyPolicy.Decision adaptive =
          learner.decide(
              effectiveness, "SQUAT", FormErrorCode.CHEST_UP, preferred, total);
      adaptiveCounts.merge(adaptive.modality(), 1, Integer::sum);

      double adaptiveDelta = simulateStudentDelta(adaptive.modality(), rng);
      adaptiveCumulativeDelta += adaptiveDelta;
      ProfileEffectivenessMath.updateBucket(
          effectiveness,
          adaptive.modality().name(),
          adaptiveDelta,
          ProfileEffectivenessMath.isSuccessfulDelta(adaptiveDelta));
      ProfileEffectivenessMath.updateBucket(
          effectiveness,
          ProfileEffectivenessMath.compositeKey(
              "SQUAT", FormErrorCode.CHEST_UP.name(), adaptive.modality().name()),
          adaptiveDelta,
          ProfileEffectivenessMath.isSuccessfulDelta(adaptiveDelta));
      total++;
      preferred = ProfileEffectivenessMath.derivePreferredModality(effectiveness);

      // Static always verbal text
      double staticDelta = simulateStudentDelta(FeedbackModality.VERBAL_TEXT, rng);
      staticCumulativeDelta += staticDelta;
    }

    assertTrue(
        adaptiveCumulativeDelta > staticCumulativeDelta,
        "Adaptive cumulative delta "
            + adaptiveCumulativeDelta
            + " should exceed static "
            + staticCumulativeDelta);

    // Late-session preference should be visual for this synthetic student
    assertEquals(FeedbackModality.VISUAL_HIGHLIGHT, preferred);

    int visualPicks = adaptiveCounts.getOrDefault(FeedbackModality.VISUAL_HIGHLIGHT, 0);
    int verbalPicks = adaptiveCounts.getOrDefault(FeedbackModality.VERBAL_TEXT, 0);
    assertTrue(
        visualPicks > verbalPicks,
        "Expected more VISUAL_HIGHLIGHT picks than VERBAL_TEXT, got visual="
            + visualPicks
            + " verbal="
            + verbalPicks);

    // Sanity: static policy path always returns verbal under epsilon=0 with empty evidence
    EpsilonGreedyPolicy.Decision staticDecision =
        staticVerbal.decide(Map.of(), "SQUAT", FormErrorCode.CHEST_UP, FeedbackModality.VERBAL_TEXT, 0);
    assertEquals(FeedbackModality.VERBAL_TEXT, staticDecision.modality());
    assertEquals(PolicySource.DEFAULT, staticDecision.policySource());
  }

  @Test
  void exploitWithSeededRngIsDeterministic() {
    Map<String, Object> effectiveness = new HashMap<>();
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.5, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.45, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.4, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VERBAL_TEXT", 0.05, false);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VERBAL_TEXT", 0.05, false);

    EpsilonGreedyPolicy policy = new EpsilonGreedyPolicy(0.0, new Random(1));
    EpsilonGreedyPolicy.Decision d1 =
        policy.decide(
            effectiveness, "SQUAT", FormErrorCode.CHEST_UP, FeedbackModality.VERBAL_TEXT, 5);
    EpsilonGreedyPolicy.Decision d2 =
        policy.decide(
            effectiveness, "SQUAT", FormErrorCode.CHEST_UP, FeedbackModality.VERBAL_TEXT, 5);

    assertEquals(FeedbackModality.VISUAL_HIGHLIGHT, d1.modality());
    assertEquals(PolicySource.EXPLOIT, d1.policySource());
    assertEquals(d1.modality(), d2.modality());
  }

  @Test
  void exploreAlwaysLogsExplorePolicySource() {
    EpsilonGreedyPolicy alwaysExplore = new EpsilonGreedyPolicy(1.0, new Random(99));
    EpsilonGreedyPolicy.Decision decision =
        alwaysExplore.decide(
            Map.of(), "PUSH_UP", FormErrorCode.SAG, FeedbackModality.VERBAL_TEXT, 10);
    assertEquals(PolicySource.EXPLORE, decision.policySource());
    assertNotNull(decision.modality());
  }

  @Test
  void compositeEvidenceOutweighsGlobalModalityWhenEnoughSamples() {
    Map<String, Object> effectiveness = new HashMap<>();
    // Global: verbal looks best
    ProfileEffectivenessMath.updateBucket(effectiveness, "VERBAL_TEXT", 0.4, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VERBAL_TEXT", 0.4, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.1, false);
    ProfileEffectivenessMath.updateBucket(effectiveness, "VISUAL_HIGHLIGHT", 0.1, false);

    // For SQUAT|CHEST_UP, visual is specifically better (n>=2)
    String visualKey =
        ProfileEffectivenessMath.compositeKey(
            "SQUAT", FormErrorCode.CHEST_UP.name(), "VISUAL_HIGHLIGHT");
    String verbalKey =
        ProfileEffectivenessMath.compositeKey(
            "SQUAT", FormErrorCode.CHEST_UP.name(), "VERBAL_TEXT");
    ProfileEffectivenessMath.updateBucket(effectiveness, visualKey, 0.55, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, visualKey, 0.5, true);
    ProfileEffectivenessMath.updateBucket(effectiveness, verbalKey, 0.05, false);
    ProfileEffectivenessMath.updateBucket(effectiveness, verbalKey, 0.05, false);

    EpsilonGreedyPolicy policy = new EpsilonGreedyPolicy(0.0, new Random(3));
    EpsilonGreedyPolicy.Decision decision =
        policy.decide(
            effectiveness, "SQUAT", FormErrorCode.CHEST_UP, FeedbackModality.VERBAL_TEXT, 8);

    assertEquals(FeedbackModality.VISUAL_HIGHLIGHT, decision.modality());
    assertEquals(PolicySource.EXPLOIT, decision.policySource());
  }
}
