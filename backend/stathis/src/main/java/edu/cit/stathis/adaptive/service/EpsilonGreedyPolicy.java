package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Pure epsilon-greedy policy over feedback modalities.
 *
 * <p>Server-authoritative decision core used by {@link AdaptivePolicyService}. Injectable {@link
 * Random} enables deterministic simulation tests.
 */
public final class EpsilonGreedyPolicy {

  public static final double DEFAULT_EPSILON = 0.2;

  public static final FeedbackModality[] ACTIVE_MODALITIES = {
    FeedbackModality.VERBAL_TEXT, FeedbackModality.VISUAL_HIGHLIGHT, FeedbackModality.VERBAL_TTS
  };

  public record Decision(
      FeedbackModality modality,
      PolicySource policySource,
      double expectedDelta) {}

  private final double epsilon;
  private final Random random;

  public EpsilonGreedyPolicy(double epsilon, Random random) {
    if (epsilon < 0.0 || epsilon > 1.0) {
      throw new IllegalArgumentException("epsilon must be in [0,1]");
    }
    this.epsilon = epsilon;
    this.random = Objects.requireNonNull(random);
  }

  public EpsilonGreedyPolicy(Random random) {
    this(DEFAULT_EPSILON, random);
  }

  public Decision decide(
      Map<String, Object> effectiveness,
      String exerciseType,
      FormErrorCode errorCode,
      FeedbackModality preferredModality,
      int totalInterventions) {
    Map<String, Object> evidence = effectiveness != null ? effectiveness : Map.of();
    String exercise = exerciseType != null ? exerciseType : "UNKNOWN";
    FormErrorCode error = errorCode != null ? errorCode : FormErrorCode.UNKNOWN;

    boolean explore = random.nextDouble() < epsilon;
    if (explore) {
      FeedbackModality chosen = ACTIVE_MODALITIES[random.nextInt(ACTIVE_MODALITIES.length)];
      return new Decision(
          chosen, PolicySource.EXPLORE, estimateDelta(evidence, exercise, error, chosen));
    }

    EnumMap<FeedbackModality, Double> scores = scoreModalities(evidence, exercise, error);
    if (scores.values().stream().allMatch(v -> v == 0.0) && totalInterventions <= 0) {
      return new Decision(FeedbackModality.VERBAL_TEXT, PolicySource.DEFAULT, 0.0);
    }

    FeedbackModality chosen = argMax(scores, preferredModality);
    return new Decision(chosen, PolicySource.EXPLOIT, scores.getOrDefault(chosen, 0.0));
  }

  EnumMap<FeedbackModality, Double> scoreModalities(
      Map<String, Object> effectiveness, String exerciseType, FormErrorCode errorCode) {
    EnumMap<FeedbackModality, Double> scores = new EnumMap<>(FeedbackModality.class);
    for (FeedbackModality modality : ACTIVE_MODALITIES) {
      String composite =
          ProfileEffectivenessMath.compositeKey(exerciseType, errorCode.name(), modality.name());
      double compositeScore = readScore(effectiveness.get(composite));
      double modalityScore = readScore(effectiveness.get(modality.name()));
      Object compositeRaw = effectiveness.get(composite);
      int n = readN(compositeRaw);
      double score = n >= 2 ? compositeScore : (0.7 * modalityScore + 0.3 * compositeScore);
      scores.put(modality, score);
    }
    return scores;
  }

  double estimateDelta(
      Map<String, Object> effectiveness,
      String exerciseType,
      FormErrorCode errorCode,
      FeedbackModality modality) {
    String composite =
        ProfileEffectivenessMath.compositeKey(exerciseType, errorCode.name(), modality.name());
    double specific = readScore(effectiveness.get(composite));
    if (specific != 0.0) {
      return specific;
    }
    return readScore(effectiveness.get(modality.name()));
  }

  @SuppressWarnings("unchecked")
  private static double readScore(Object raw) {
    if (!(raw instanceof Map<?, ?>)) {
      return 0.0;
    }
    Map<String, Object> bucket = (Map<String, Object>) raw;
    Object bayes = bucket.get("bayesianMeanDelta");
    if (bayes instanceof Number number) {
      return number.doubleValue();
    }
    return ((Number) bucket.getOrDefault("meanDelta", 0.0)).doubleValue();
  }

  @SuppressWarnings("unchecked")
  private static int readN(Object raw) {
    if (!(raw instanceof Map<?, ?>)) {
      return 0;
    }
    return ((Number) ((Map<String, Object>) raw).getOrDefault("n", 0)).intValue();
  }

  private static FeedbackModality argMax(
      EnumMap<FeedbackModality, Double> scores, FeedbackModality fallback) {
    FeedbackModality best = fallback != null ? fallback : FeedbackModality.VERBAL_TEXT;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (Map.Entry<FeedbackModality, Double> entry : scores.entrySet()) {
      if (entry.getValue() > bestScore) {
        bestScore = entry.getValue();
        best = entry.getKey();
      }
    }
    return best;
  }
}
