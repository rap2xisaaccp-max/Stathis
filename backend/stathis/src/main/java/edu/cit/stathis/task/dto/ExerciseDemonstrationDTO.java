package edu.cit.stathis.task.dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExerciseDemonstrationDTO {
  private boolean available;
  private String physicalId;
  private String taskId;
  private String exerciseTemplateId;
  private String originalFilename;
  private String contentType;
  private Long byteSize;
  private OffsetDateTime createdAt;

  public static ExerciseDemonstrationDTO absent(String taskId, String exerciseTemplateId) {
    return ExerciseDemonstrationDTO.builder()
        .available(false)
        .taskId(taskId)
        .exerciseTemplateId(exerciseTemplateId)
        .build();
  }
}
