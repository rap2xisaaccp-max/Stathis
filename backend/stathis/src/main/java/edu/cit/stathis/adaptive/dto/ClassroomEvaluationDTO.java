package edu.cit.stathis.adaptive.dto;

import java.util.List;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomEvaluationDTO {
  private String classroomId;
  private int studentCount;
  private long totalInterventions;
  private long successfulInterventions;
  private double overallSuccessRate;
  private double meanDelta;
  private double meanMasteryLevel;
  private long practiceInterventions;
  private long taskInterventions;
  /** Aggregated counts by full experimentArm label (includes *_PRACTICE). */
  private Map<String, Long> interventionsByArm;
  /** Ablation contrast using base arms ADAPTIVE vs STATIC. */
  private Double adaptiveMeanDelta;
  private Double staticMeanDelta;
  private Double meanDeltaLift;
  private Double successRateLift;
  private Double cohensD;
  private boolean adaptiveOutperformsOnDelta;
  private List<AdaptiveEvaluationSummaryDTO> students;
}
