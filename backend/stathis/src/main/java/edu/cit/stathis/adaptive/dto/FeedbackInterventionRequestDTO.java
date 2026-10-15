package edu.cit.stathis.adaptive.dto;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackInterventionRequestDTO {
  private String physicalId;
  private String sessionId;
  private String taskId;
  private String classroomId;
  private String exerciseType;
  private FormErrorCode errorCode;
  private FeedbackModality modality;
  private String messageCode;
  private String messageText;
  private String deliveredAt;
  private double baselineSeverity;
  private PolicySource policySource;
  private String experimentArm;
}
