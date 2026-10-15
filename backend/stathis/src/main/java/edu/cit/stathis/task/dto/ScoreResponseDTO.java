package edu.cit.stathis.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API-facing score payload for teacher/student clients.
 * Maps entity fields to the shape expected by the web dashboard
 * (status, remainingAttempts, submissionDate, effective score).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResponseDTO {
    private String physicalId;
    private String studentId;
    private String taskId;
    private String quizTemplateId;
    private String exerciseTemplateId;
    /** Effective score: manualScore if set, otherwise auto score. */
    private int score;
    private int maxScore;
    private int attempts;
    private int remainingAttempts;
    private boolean isCompleted;
    private String teacherFeedback;
    private Integer manualScore;
    private Integer reps;
    private Integer goalReps;
    private Double accuracy;
    private Double caloriesBurned;
    /** PENDING | COMPLETED | GRADED */
    private String status;
    private String submissionDate;
    private String startedAt;
    private String completedAt;
    private String feedback;
}
