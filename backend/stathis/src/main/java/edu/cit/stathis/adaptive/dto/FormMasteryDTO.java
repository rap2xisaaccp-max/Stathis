package edu.cit.stathis.adaptive.dto;

import lombok.*;

/**
 * Attempt-level form quality for one normalized exercise type.
 *
 * {@code formMasteryLevel} is the persistent recency-weighted Form Mastery for one exercise.
 * It is not {@link ExerciseMasteryDTO#getMasteryLevel()} (coaching-frequency) and not the
 * latest attempt alone. {@code state} is LEARNING, IMPROVING, or MASTERED.
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
  /** Persistent Form Mastery in [0, 1]. */
  private double formMasteryLevel;
  /** {@link #formMasteryLevel} as a percent in [0, 100]. */
  private double formMasteryPercent;
  private int eligibleAttemptCount;
  private String lastAttemptAt;
  /** LEARNING, IMPROVING, or MASTERED. */
  private String state;
}
