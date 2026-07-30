package edu.cit.stathis.adaptive.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
    name = "feedback_response",
    indexes = {
      @Index(name = "idx_fr_intervention", columnList = "intervention_physical_id"),
      @Index(name = "idx_fr_student", columnList = "student_id"),
      @Index(name = "idx_fr_student_created", columnList = "student_id,created_at")
    })
public class FeedbackResponse {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "feedback_response_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  /** Unique: one measured response per intervention (idempotent ingest). */
  @Column(name = "intervention_physical_id", nullable = false, unique = true)
  private String interventionPhysicalId;

  @Column(name = "window_end_at", nullable = false)
  private OffsetDateTime windowEndAt;

  @Column(name = "post_severity", nullable = false)
  private double postSeverity;

  /** baselineSeverity - postSeverity (positive = improved). */
  @Column(name = "delta", nullable = false)
  private double delta;

  @Column(name = "reps_in_window")
  private Integer repsInWindow;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Type(JsonType.class)
  @Column(name = "confounders_json", columnDefinition = "jsonb")
  private Map<String, Object> confoundersJson;
}
