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
  private List<ExerciseMasteryDTO> mastery;
  private Map<String, Double> modalityMeanDelta;
  private Map<String, Long> topRecurringErrors;
  private long totalInterventions;
  private long successfulInterventions;
  private double overallSuccessRate;
  private List<FeedbackInterventionResponseDTO> recentInterventions;
  /** Chronological learning-profile snapshots for mastery / consistency timelines. */
  private List<ProfileHistoryPointDTO> profileHistory;
}
