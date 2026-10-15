package edu.cit.stathis.adaptive.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormCorrectionEvidenceDTO {
  private String physicalId;
  private String interventionPhysicalId;
  private String studentId;
  private String sessionId;
  private String taskId;
  private String classroomId;
  private Integer attemptNumber;
  private String exerciseType;
  private String errorCode;
  private String errorLabel;
  private String errorDescription;
  private String correctionText;
  private String capturedAt;
  private String createdAt;
  private int byteSize;
  private String imageUrl;
}
