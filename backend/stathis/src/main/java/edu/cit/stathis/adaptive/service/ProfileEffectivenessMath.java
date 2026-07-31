package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure update math for StudentLearningProfile evidence buckets.
 *
 * <p>Uses a small-sample running mean (n &lt; {@link #WARMUP_N}), then EWMA, keyed by modality and by
 * {@code exerciseType|errorCode|modality}. Preferred modality is derived from scored evidence.
 */
public final class ProfileEffectivenessMath {

  public static final double EWMA_ALPHA = 0.3;
  public static final double SUCCESS_DELTA_THRESHOLD = 0.15;
  /** Use arithmetic mean until this many samples, then switch to EWMA. */
  public static final int WARMUP_N = 5;
  /** Pseudo-count prior for Bayesian shrinkage of meanDelta toward 0. */
  public static final double BAYES_PRIOR_STRENGTH = 2.0;

  private ProfileEffectivenessMath() {}

  public static boolean isSuccessfulDelta(double delta) {
    return delta >= SUCCESS_DELTA_THRESHOLD;
  }

  public static double ewma(double prior, double observation) {
    return (1.0 - EWMA_ALPHA) * prior + EWMA_ALPHA * observation;
  }

  /**
   * Running mean for warm-up samples; EWMA afterward. Also returns Bayesian-shrunk mean for
   * preference scoring (shrink toward 0 with prior strength {@link #BAYES_PRIOR_STRENGTH}).
   */
  public static Map<String, Object> updateBucket(
      Map<String, Object> effectiveness, String key, double delta, boolean success) {
    Map<String, Object> target =
        effectiveness != null ? effectiveness : new HashMap<>();
    Map<String, Object> bucket = readBucket(target.get(key));

    int n = ((Number) bucket.getOrDefault("n", 0)).intValue();
    double mean = ((Number) bucket.getOrDefault("meanDelta", 0.0)).doubleValue();
    int successes = ((Number) bucket.getOrDefault("successes", 0)).intValue();

    double newMean;
    if (n == 0) {
      newMean = delta;
    } else if (n < WARMUP_N) {
      newMean = (mean * n + delta) / (n + 1);
    } else {
      newMean = ewma(mean, delta);
    }

    int newN = n + 1;
    int newSuccesses = successes + (success ? 1 : 0);
    double successRate = (double) newSuccesses / newN;
    double bayesianMean =
        (BAYES_PRIOR_STRENGTH * 0.0 + newN * newMean) / (BAYES_PRIOR_STRENGTH + newN);

    bucket.put("meanDelta", newMean);
    bucket.put("bayesianMeanDelta", bayesianMean);
    bucket.put("n", newN);
    bucket.put("successes", newSuccesses);
    bucket.put("successRate", successRate);
    target.put(key, bucket);
    return target;
  }

  public static String compositeKey(String exerciseType, String errorCode, String modality) {
    return exerciseType + "|" + errorCode + "|" + modality;
  }

  @SuppressWarnings("unchecked")
  public static FeedbackModality derivePreferredModality(Map<String, Object> effectiveness) {
    FeedbackModality best = FeedbackModality.VERBAL_TEXT;
    double bestScore = Double.NEGATIVE_INFINITY;
    if (effectiveness == null || effectiveness.isEmpty()) {
      return best;
    }

    for (FeedbackModality modality : FeedbackModality.values()) {
      Object raw = effectiveness.get(modality.name());
      if (!(raw instanceof Map<?, ?>)) {
        continue;
      }
      Map<String, Object> bucket = (Map<String, Object>) raw;
      int n = ((Number) bucket.getOrDefault("n", 0)).intValue();
      if (n < 1) {
        continue;
      }
      double meanDelta =
          ((Number)
                  bucket.getOrDefault(
                      "bayesianMeanDelta", bucket.getOrDefault("meanDelta", 0.0)))
              .doubleValue();
      double successRate = ((Number) bucket.getOrDefault("successRate", 0.0)).doubleValue();
      // Evidence-weighted score: shrunk mean + success rate + mild sample-size bonus
      double score = meanDelta + 0.2 * successRate + Math.log1p(n) * 0.01;
      if (score > bestScore) {
        bestScore = score;
        best = modality;
      }
    }
    return best;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readBucket(Object existing) {
    if (existing instanceof Map<?, ?> map) {
      return new HashMap<>((Map<String, Object>) map);
    }
    Map<String, Object> bucket = new HashMap<>();
    bucket.put("meanDelta", 0.0);
    bucket.put("bayesianMeanDelta", 0.0);
    bucket.put("n", 0);
    bucket.put("successRate", 0.0);
    bucket.put("successes", 0);
    return bucket;
  }
}
