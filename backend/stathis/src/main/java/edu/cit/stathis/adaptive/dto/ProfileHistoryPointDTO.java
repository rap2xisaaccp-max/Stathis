package edu.cit.stathis.adaptive.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileHistoryPointDTO {
  private String physicalId;
  private String createdAt;
  private String reason;
  private Double learningRateEstimate;
  private Double consistencyScore;
  private Double meanMasteryLevel;
  private Integer totalInterventions;
  private String preferredModality;
}
