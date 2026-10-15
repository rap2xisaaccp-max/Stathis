package edu.cit.stathis.adaptive.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdaptiveBatchIngestDTO {
  private List<FeedbackInterventionRequestDTO> interventions;
  private List<FeedbackResponseRequestDTO> responses;
}
