package edu.cit.stathis.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceEmbeddingDTO {

  /** JSON array of floats, e.g. "[0.12,-0.03,...]" */
  @NotBlank(message = "Face embedding is required")
  private String embedding;
}
