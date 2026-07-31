package edu.cit.stathis.adaptive.dto;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdaptiveRecommendationDTO {
  private FeedbackModality modality;
  private FormErrorCode errorCode;
  private String messageCode;
  private String messageText;
  private PolicySource policySource;
  private double expectedDelta;
  private String experimentArm;
  private int cooldownMs;
}
