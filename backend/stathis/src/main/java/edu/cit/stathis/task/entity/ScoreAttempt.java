package edu.cit.stathis.task.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One row per student submission/attempt against a Score aggregate.
 * Score.attempts remains the counter; this table stores per-attempt stats (accuracy, score, reps).
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "score_attempt", indexes = {
        @Index(name = "idx_score_attempt_student_task", columnList = "student_id, task_id"),
        @Index(name = "idx_score_attempt_score", columnList = "score_physical_id")
})
public class ScoreAttempt {
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "score_attempt_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "physical_id", unique = true, nullable = false)
    private String physicalId;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "score_physical_id", nullable = false)
    private String scorePhysicalId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "quiz_template_id")
    private String quizTemplateId;

    @Column(name = "exercise_template_id")
    private String exerciseTemplateId;

    /** 1-based attempt number within the parent Score. */
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "score")
    private int score;

    @Column(name = "max_score")
    private int maxScore;

    @Column(name = "accuracy")
    private double accuracy;

    @Column(name = "reps")
    private Integer reps;

    @Column(name = "goal_reps")
    private Integer goalReps;

    @Column(name = "calories_burned")
    private Double caloriesBurned;

    @Column(name = "time_taken")
    private Long timeTaken;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
