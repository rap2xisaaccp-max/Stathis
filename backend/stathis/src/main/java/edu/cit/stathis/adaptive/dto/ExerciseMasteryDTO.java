package edu.cit.stathis.adaptive.dto;

import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseMasteryDTO {
  private String physicalId;
  private String studentId;
  private String exerciseType;
  /**
   * Coaching-frequency estimate from sessions and claimed form-correction events.
   * Not Form Mastery and not percent of correct reps. See {@link FormMasteryDTO}.
   */
  private double masteryLevel;
  private Map<String, Object> commonErrorsJson;
  private Integer sessionsCount;
  private Long medianTimeToCorrectionMs;
  private String recommendedDifficulty;
  /** Soft suggested goal reps derived from mastery — teacher must approve. */
  private Integer recommendedGoalReps;
  private String recommendationRationale;
  /** Soft recommendations never auto-apply to templates. */
  private boolean requiresTeacherApproval;
  private String lastSessionAt;
}
