package edu.cit.stathis.adaptive.dto;

import java.util.Map;
import lombok.*;

/**
 * Compact evaluation export for RCT analysis of adaptive vs static feedback.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdaptiveEvaluationSummaryDTO {
  private String studentId;
  private String experimentArm;
  private long totalInterventions;
  private long successfulInterventions;
  private double successRate;
  private double meanDelta;
  private Map<String, Double> meanDeltaByModality;
  private Map<String, Long> errorFrequency;
  private Double meanMasteryLevel;
  private Integer sessionsTracked;
  /** Interventions from ungraded practice sessions (experimentArm ends with _PRACTICE). */
  private long practiceInterventions;
  /** Interventions from classroom tasks (not practice). */
  private long taskInterventions;
  private Map<String, Long> interventionsByArm;
}
