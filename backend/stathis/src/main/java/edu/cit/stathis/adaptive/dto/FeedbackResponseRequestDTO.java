package edu.cit.stathis.adaptive.dto;

import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponseRequestDTO {
  private String physicalId;
  private String interventionPhysicalId;
  private String windowEndAt;
  private double postSeverity;
  private Double delta;
  private Integer repsInWindow;
  private Boolean success;
  private Map<String, Object> confoundersJson;
}
