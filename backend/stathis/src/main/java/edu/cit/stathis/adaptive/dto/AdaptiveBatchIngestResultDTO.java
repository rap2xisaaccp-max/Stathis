package edu.cit.stathis.adaptive.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdaptiveBatchIngestResultDTO {
  private int interventionsSaved;
  private int responsesSaved;
  private int interventionsFailed;
  private int responsesFailed;
  private List<String> interventionPhysicalIds;
  private List<String> responsePhysicalIds;
  private List<String> errors;
  private StudentLearningProfileDTO updatedProfile;
}
