package edu.cit.stathis.task.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "task_exercise",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_task_exercise_task_template",
          columnNames = {"task_id", "exercise_template_id"}),
      @UniqueConstraint(
          name = "uq_task_exercise_task_order",
          columnNames = {"task_id", "sort_order"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskExercise {

  @Id
  @GeneratedValue(generator = "UUID")
  @Column(name = "task_exercise_id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "physical_id", nullable = false, unique = true, length = 255)
  private String physicalId;

  @Column(name = "task_id", nullable = false, length = 255)
  private String taskId;

  @Column(name = "exercise_template_id", nullable = false, length = 255)
  private String exerciseTemplateId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @CreationTimestamp
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
}
