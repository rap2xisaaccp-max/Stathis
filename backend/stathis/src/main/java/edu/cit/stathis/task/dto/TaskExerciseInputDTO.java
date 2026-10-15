package edu.cit.stathis.task.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TaskExerciseInputDTO {
  @Pattern(regexp = "^EXERCISE-[A-Z0-9-]+$", message = "Invalid exercise template ID format")
  private String exerciseTemplateId;

  private Integer sortOrder;
}
