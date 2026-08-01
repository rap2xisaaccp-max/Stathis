package edu.cit.stathis.adaptive.dto;

import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponseResponseDTO {
  private String physicalId;
  private String studentId;
  private String interventionPhysicalId;
  private String windowEndAt;
  private double postSeverity;
  private double delta;
  private Integer repsInWindow;
  private boolean success;
  private Map<String, Object> confoundersJson;
}
