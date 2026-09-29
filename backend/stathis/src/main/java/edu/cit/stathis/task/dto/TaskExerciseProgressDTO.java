package edu.cit.stathis.task.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskExerciseProgressDTO {
  private String exerciseTemplateId;
  private int sortOrder;
  private String title;
  private String exerciseType;
  private Integer goalReps;
  private Integer goalAccuracy;
  private Integer goalTime;
  private boolean completed;
  private int attempts;
  private Integer latestValidReps;
  private Integer score;
  /** NOT_STARTED, IN_PROGRESS, or COMPLETED. */
  private String completionStatus;

  /** True when this assigned exercise has a demonstration video. */
  @Builder.Default private boolean demonstrationAvailable = false;
}
