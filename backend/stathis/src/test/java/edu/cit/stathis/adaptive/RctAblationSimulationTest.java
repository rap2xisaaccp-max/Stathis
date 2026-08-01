package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.service.EpsilonGreedyPolicy;
import edu.cit.stathis.adaptive.service.ProfileEffectivenessMath;
import edu.cit.stathis.adaptive.service.RctEvaluationMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 ablation: multi-student simulation of adaptive policy vs static verbal control,
 * reporting primary RCT metrics (mean Δ lift, success-rate lift, Cohen's d).
 */
class RctAblationSimulationTest {

  private static double simulateStudentDelta(FeedbackModality modality, Random random) {
    return switch (modality) {
      case VISUAL_HIGHLIGHT -> 0.35 + random.nextGaussian() * 0.05;
      case VERBAL_TTS -> 0.18 + random.nextGaussian() * 0.05;
      case VERBAL_TEXT, DEMONSTRATION -> 0.05 + random.nextGaussian() * 0.03;
    };
  }

  @Test
  void adaptiveCohortOutperformsStaticOnPrimaryMetrics() {
    Random rng = new Random(11);
    int students = 12;
    int roundsPerStudent = 40;

    List<Double> adaptiveDeltas = new ArrayList<>();
    List<Double> staticDeltas = new ArrayList<>();
    long adaptiveSuccesses = 0;
    long staticSuccesses = 0;

    for (int s = 0; s < students; s++) {
      Map<String, Object> effectiveness = new HashMap<>();
      FeedbackModality preferred = FeedbackModality.VERBAL_TEXT;
      int total = 0;
      EpsilonGreedyPolicy learner = new EpsilonGreedyPolicy(0.2, new Random(rng.nextLong()));

      for (int i = 0; i < roundsPerStudent; i++) {
        EpsilonGreedyPolicy.Decision adaptive =
            learner.decide(
                effectiveness, "SQUAT", FormErrorCode.CHEST_UP, preferred, total);
        double adaptiveDelta = simulateStudentDelta(adaptive.modality(), rng);
        adaptiveDeltas.add(adaptiveDelta);
        if (ProfileEffectivenessMath.isSuccessfulDelta(adaptiveDelta)) {
          adaptiveSuccesses++;
        }
        ProfileEffectivenessMath.updateBucket(
            effectiveness,
            adaptive.modality().name(),
            adaptiveDelta,
            ProfileEffectivenessMath.isSuccessfulDelta(adaptiveDelta));
        total++;
        preferred = ProfileEffectivenessMath.derivePreferredModality(effectiveness);

        double staticDelta = simulateStudentDelta(FeedbackModality.VERBAL_TEXT, rng);
        staticDeltas.add(staticDelta);
        if (ProfileEffectivenessMath.isSuccessfulDelta(staticDelta)) {
          staticSuccesses++;
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

    assertTrue(
        contrast.adaptiveOutperformsOnDelta(),
        "Adaptive mean Δ should exceed static; lift=" + contrast.meanDeltaLift());
    assertTrue(
        contrast.successRateLift() > 0.0,
        "Adaptive success rate should exceed static; lift=" + contrast.successRateLift());
    assertTrue(d > 0.5, "Expected at least medium Cohen's d, got " + d);
    assertEquals(students * roundsPerStudent, adaptiveStats.interventions());
    assertEquals(students * roundsPerStudent, staticStats.interventions());
  }
}
