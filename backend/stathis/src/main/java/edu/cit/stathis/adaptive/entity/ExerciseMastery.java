package edu.cit.stathis.adaptive.entity;

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
    name = "exercise_mastery",
    indexes = {
      @Index(
          name = "idx_em_student_exercise",
          columnList = "student_id,exercise_type",
          unique = true)
    })
public class ExerciseMastery {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "exercise_mastery_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  @Column(name = "exercise_type", nullable = false)
  private String exerciseType;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  /**
   * 0.0–1.0 coaching-frequency estimate from sessions and claimed form-correction events.
   * Not Form Mastery (classroom attempt accuracy). See FormMasteryService.
   */
  @Column(name = "mastery_level", nullable = false)
  private double masteryLevel;

  @Type(JsonType.class)
  @Column(name = "common_errors_json", columnDefinition = "jsonb")
  private Map<String, Object> commonErrorsJson;

  @Column(name = "sessions_count")
  private Integer sessionsCount;

  @Column(name = "median_time_to_correction_ms")
  private Long medianTimeToCorrectionMs;

  @Column(name = "recommended_difficulty")
  private String recommendedDifficulty;

  @Column(name = "last_session_at")
  private OffsetDateTime lastSessionAt;
}
