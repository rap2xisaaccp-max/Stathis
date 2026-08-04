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
public class AdaptiveRecommendationRequestDTO {
  private String exerciseType;
  private FormErrorCode errorCode;
  private Double currentSeverity;
}
