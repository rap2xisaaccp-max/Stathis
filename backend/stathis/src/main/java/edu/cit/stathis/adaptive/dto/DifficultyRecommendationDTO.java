package edu.cit.stathis.adaptive.dto;

import java.util.List;
import lombok.*;

/**
 * Soft difficulty / goal-reps suggestion for teachers. Never applied automatically to templates.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DifficultyRecommendationDTO {
  private String studentId;
  private String exerciseType;
  private double masteryLevel;
  private Integer sessionsCount;
  private String recommendedDifficulty;
  private Integer recommendedGoalReps;
  private String rationale;
  /** Always true for APSLE soft recommendations — teacher must approve. */
  private boolean requiresTeacherApproval;
  private List<String> topErrors;
}
