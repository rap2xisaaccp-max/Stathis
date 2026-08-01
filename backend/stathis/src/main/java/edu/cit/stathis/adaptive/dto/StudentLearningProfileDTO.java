package edu.cit.stathis.adaptive.dto;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLearningProfileDTO {
  private String physicalId;
  private String studentId;
  private FeedbackModality preferredModality;
  private Map<String, Object> modalityEffectivenessJson;
  private Double learningRateEstimate;
  private Double consistencyScore;
  private Double fatigueSensitivity;
  private Integer totalInterventions;
  private Integer totalSuccessfulInterventions;
  private String updatedAt;
}
