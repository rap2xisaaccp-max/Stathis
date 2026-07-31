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
  private List<String> interventionPhysicalIds;
  private List<String> responsePhysicalIds;
  private StudentLearningProfileDTO updatedProfile;
}
