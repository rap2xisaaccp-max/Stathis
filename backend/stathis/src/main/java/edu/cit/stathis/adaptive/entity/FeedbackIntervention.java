package edu.cit.stathis.adaptive.entity;

import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
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
    name = "feedback_intervention",
    indexes = {
      @Index(name = "idx_fi_student", columnList = "student_id"),
      @Index(name = "idx_fi_session", columnList = "session_id"),
      @Index(name = "idx_fi_student_exercise_error", columnList = "student_id,exercise_type,error_code")
    })
public class FeedbackIntervention {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "feedback_intervention_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "task_id")
  private String taskId;

  @Column(name = "classroom_id")
  private String classroomId;

  @Column(name = "exercise_type", nullable = false)
  private String exerciseType;

  @Enumerated(EnumType.STRING)
  @Column(name = "error_code", nullable = false)
  private FormErrorCode errorCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "modality", nullable = false)
  private FeedbackModality modality;

  @Column(name = "message_code")
  private String messageCode;

  @Column(name = "message_text")
  private String messageText;

  @Column(name = "delivered_at", nullable = false)
  private OffsetDateTime deliveredAt;

  @Column(name = "baseline_severity", nullable = false)
  private double baselineSeverity;

  @Enumerated(EnumType.STRING)
  @Column(name = "policy_source", nullable = false)
  private PolicySource policySource;

  /** Optional RCT arm label: ADAPTIVE or STATIC. */
  @Column(name = "experiment_arm")
  private String experimentArm;
}
