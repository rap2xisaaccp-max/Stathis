package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.service.RctEvaluationMetrics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RctEvaluationMetricsTest {

  @Test
  void successRateAndMean() {
    assertEquals(0.5, RctEvaluationMetrics.successRate(5, 10), 1e-6);
    assertEquals(0.0, RctEvaluationMetrics.successRate(1, 0), 1e-6);
    assertEquals(0.3, RctEvaluationMetrics.mean(List.of(0.1, 0.2, 0.6)), 1e-6);
  }

  @Test
  void baseArmNormalizesPracticeSuffix() {
    assertEquals("ADAPTIVE", RctEvaluationMetrics.baseArm("ADAPTIVE_PRACTICE"));
    assertEquals("STATIC", RctEvaluationMetrics.baseArm("STATIC_PRACTICE"));
    assertEquals("ADAPTIVE", RctEvaluationMetrics.baseArm(null));
    assertTrue(RctEvaluationMetrics.isPracticeArm("ADAPTIVE_PRACTICE"));
    assertFalse(RctEvaluationMetrics.isPracticeArm("ADAPTIVE"));
  }

  @Test
  void ablationContrastDetectsAdaptiveLift() {
    RctEvaluationMetrics.ArmStats adaptive =
        RctEvaluationMetrics.armStats("ADAPTIVE", List.of(0.3, 0.4, 0.35), 3, 0.6);
    RctEvaluationMetrics.ArmStats control =
        RctEvaluationMetrics.armStats("STATIC", List.of(0.05, 0.1, 0.08), 1, 0.4);
    RctEvaluationMetrics.AblationContrast contrast =
        RctEvaluationMetrics.contrast(adaptive, control);
    assertTrue(contrast.adaptiveOutperformsOnDelta());
    assertTrue(contrast.meanDeltaLift() > 0.2);
    assertTrue(contrast.successRateLift() > 0.0);
  }

  @Test
  void cohensDPositiveWhenTreatmentHigher() {
    double d =
        RctEvaluationMetrics.cohensD(
            List.of(0.4, 0.45, 0.5, 0.42), List.of(0.05, 0.1, 0.08, 0.12));
    assertTrue(d > 1.0, "expected large positive effect size, got " + d);
  }

  @Test
  void topErrorsRespectsLimitAndOrder() {
    Map<String, Long> top =
        RctEvaluationMetrics.topErrors(Map.of("SAG", 2L, "KNEES_IN", 9L, "PIKE", 4L), 2);
    assertEquals(2, top.size());
    assertEquals(List.of("KNEES_IN", "PIKE"), top.keySet().stream().toList());
  }
}
