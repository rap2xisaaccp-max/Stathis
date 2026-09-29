package edu.cit.stathis.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
    name = "exercise_demonstration",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_exercise_demonstration_physical_id",
          columnNames = {"physical_id"}),
      @UniqueConstraint(
          name = "uq_exercise_demonstration_task_template",
          columnNames = {"task_id", "exercise_template_id"})
    })
public class ExerciseDemonstration {

  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "exercise_demonstration_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", nullable = false, unique = true, length = 255)
  private String physicalId;

  @Column(name = "task_id", nullable = false, length = 255)
  private String taskId;

  @Column(name = "exercise_template_id", nullable = false, length = 255)
  private String exerciseTemplateId;

  @Column(name = "uploaded_by", nullable = false, length = 11)
  private String uploadedBy;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "storage_key", nullable = false, length = 512)
  private String storageKey;

  @Column(name = "content_type", nullable = false, length = 128)
  private String contentType;

  @Column(name = "byte_size", nullable = false)
  private long byteSize;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
}
