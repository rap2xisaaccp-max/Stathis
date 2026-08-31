package edu.cit.stathis.adaptive.dto;

import lombok.*;

/**
 * Attempt-level form quality for one normalized exercise type.
 *
 * {@code formMasteryLevel} is the mean of eligible classroom {@code score_attempt.accuracy}
 * values scaled to 0–1. It is not {@link ExerciseMasteryDTO#getMasteryLevel()}
 * (coaching-frequency) and not percent of correct reps.
 *
 * Rows are omitted when there are no eligible attempts; clients must show
 * "Not enough data" instead of 0% or 100%.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormMasteryDTO {
  private String studentId;
  private String exerciseType;
  /** Mean classroom attempt accuracy / 100, in [0, 1]. */
  private double formMasteryLevel;
  /** Mean of recorded accuracy values, in [0, 100]. */
  private double formMasteryPercent;
  private int eligibleAttemptCount;
  private String lastAttemptAt;
}
