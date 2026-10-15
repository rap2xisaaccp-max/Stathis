package edu.cit.stathis.adaptive.entity;

import edu.cit.stathis.adaptive.enums.FormErrorCode;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
    name = "form_correction_evidence",
    indexes = {
      @Index(name = "idx_fce_student_captured", columnList = "student_id,captured_at"),
      @Index(name = "idx_fce_classroom_captured", columnList = "classroom_id,captured_at"),
      @Index(name = "idx_fce_task", columnList = "task_id")
    })
public class FormCorrectionEvidence {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "form_correction_evidence_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "intervention_physical_id", unique = true, nullable = false)
  private String interventionPhysicalId;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "task_id")
  private String taskId;

  @Column(name = "classroom_id")
  private String classroomId;

  @Column(name = "attempt_number")
  private Integer attemptNumber;

  @Column(name = "exercise_type", nullable = false)
  private String exerciseType;

  @Enumerated(EnumType.STRING)
  @Column(name = "error_code", nullable = false)
  private FormErrorCode errorCode;

  @Column(name = "error_description", columnDefinition = "TEXT")
  private String errorDescription;

  @Column(name = "correction_text", columnDefinition = "TEXT")
  private String correctionText;

  @Column(name = "captured_at", nullable = false)
  private OffsetDateTime capturedAt;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "byte_size", nullable = false)
  private int byteSize;

  @Column(name = "sha256")
  private String sha256;
}
