package edu.cit.stathis.adaptive.dto;

import java.util.List;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdaptiveInsightsDTO {
  private String studentId;
  private StudentLearningProfileDTO profile;
  /**
   * Coaching-frequency rows from {@code exercise_mastery.mastery_level}. Not Form Mastery.
   * Kept for diagnostics / history isolation — teacher Form Mastery charts must use
   * {@link #formMastery}.
   */
  private List<ExerciseMasteryDTO> mastery;
  /** Attempt-level form quality from completed classroom score_attempt accuracy. */
  private List<FormMasteryDTO> formMastery;
  /** Convenience mirror of profile.preferredModalityByExercise for teacher widgets. */
  private Map<String, Object> preferredModalityByExercise;
  private Map<String, Double> modalityMeanDelta;
  private Map<String, Long> topRecurringErrors;
  private long totalInterventions;
  private long successfulInterventions;
  private double overallSuccessRate;
  private List<FeedbackInterventionResponseDTO> recentInterventions;
  /** Chronological learning-profile snapshots for mastery / consistency timelines. */
  private List<ProfileHistoryPointDTO> profileHistory;
}
