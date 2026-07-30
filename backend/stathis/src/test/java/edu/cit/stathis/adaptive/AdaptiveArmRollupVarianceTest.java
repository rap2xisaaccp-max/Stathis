package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.service.AdaptiveArmRollupService;
import org.junit.jupiter.api.Test;

public class AdaptiveArmRollupVarianceTest {

  @Test
  void sampleVarianceMatchesKnownSeries() {
    // values 0.1, 0.2, 0.3 → mean 0.2, sample variance 0.01
    double sum = 0.6;
    double sumSq = 0.01 + 0.04 + 0.09;
    assertEquals(0.2, AdaptiveArmRollupService.mean(3, sum), 1e-9);
    assertEquals(0.01, AdaptiveArmRollupService.sampleVariance(3, sum, sumSq), 1e-9);
  }

  @Test
  void sampleVarianceZeroWhenInsufficientN() {
    assertEquals(0.0, AdaptiveArmRollupService.sampleVariance(0, 0, 0), 1e-9);
    assertEquals(0.0, AdaptiveArmRollupService.sampleVariance(1, 0.5, 0.25), 1e-9);
  }
}
