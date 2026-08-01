package edu.cit.stathis.adaptive.entity;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonType;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
    name = "student_learning_profile",
    indexes = {@Index(name = "idx_slp_student", columnList = "student_id", unique = true)})
public class StudentLearningProfile {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "student_learning_profile_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @Column(name = "student_id", unique = true, nullable = false)
  private String studentId;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_modality")
  private FeedbackModality preferredModality;

  /**
   * Nested map serialized as JSON. Keys are typically modality names or
   * "exerciseType|errorCode|modality" → {meanDelta, n, successRate}.
   */
  @Type(JsonType.class)
  @Column(name = "modality_effectiveness_json", columnDefinition = "jsonb")
  private Map<String, Object> modalityEffectivenessJson;

  @Column(name = "learning_rate_estimate")
  private Double learningRateEstimate;

  @Column(name = "consistency_score")
  private Double consistencyScore;

  @Column(name = "fatigue_sensitivity")
  private Double fatigueSensitivity;

  @Column(name = "total_interventions")
  private Integer totalInterventions;

  @Column(name = "total_successful_interventions")
  private Integer totalSuccessfulInterventions;

  @Version
  @Column(name = "version")
  private Long version;
}
