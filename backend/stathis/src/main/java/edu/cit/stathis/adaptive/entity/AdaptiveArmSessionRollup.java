package edu.cit.stathis.adaptive.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Variance-preserving per-(student, session, baseArm) aggregate.
 *
 * <p>Stores {@code n}, {@code sumDelta}, {@code sumDeltaSq} so Cohen's d can be recomputed without
 * retaining every raw {@code feedback_response} forever.
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
    name = "adaptive_arm_session_rollup",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_aasr_student_session_arm",
            columnNames = {"student_id", "session_id", "base_arm"}),
    indexes = {
      @Index(name = "idx_aasr_student", columnList = "student_id"),
      @Index(name = "idx_aasr_classroom", columnList = "classroom_id"),
      @Index(name = "idx_aasr_base_arm", columnList = "base_arm")
    })
public class AdaptiveArmSessionRollup {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "adaptive_arm_session_rollup_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "classroom_id")
  private String classroomId;

  /** Normalized base arm: ADAPTIVE or STATIC. */
  @Column(name = "base_arm", nullable = false, length = 32)
  private String baseArm;

  @Column(name = "n_interventions", nullable = false)
  @Builder.Default
  private int nInterventions = 0;

  @Column(name = "n_responses", nullable = false)
  @Builder.Default
  private int nResponses = 0;

  @Column(name = "successes", nullable = false)
  @Builder.Default
  private int successes = 0;

  @Column(name = "sum_delta", nullable = false)
  @Builder.Default
  private double sumDelta = 0.0;

  @Column(name = "sum_delta_sq", nullable = false)
  @Builder.Default
  private double sumDeltaSq = 0.0;
}
