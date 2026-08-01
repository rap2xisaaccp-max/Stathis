package edu.cit.stathis.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceEmbeddingResponseDTO {

  private boolean faceRegistered;
  private String embedding;
}
