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
    name = "learning_profile_history",
    indexes = {@Index(name = "idx_lph_student", columnList = "student_id")})
public class LearningProfileHistory {
  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "learning_profile_history_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", unique = true, nullable = false)
  private String physicalId;

  @Column(name = "student_id", nullable = false)
  private String studentId;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Type(JsonType.class)
  @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> snapshotJson;

  @Column(name = "reason")
  private String reason;
}
