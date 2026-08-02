package edu.cit.stathis.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAttemptResponseDTO {
    private String physicalId;
    private String scorePhysicalId;
    private String studentId;
    private String taskId;
    private String quizTemplateId;
    private String exerciseTemplateId;
    private int attemptNumber;
    private int score;
    private int maxScore;
    private Double accuracy;
    private Integer reps;
    private Integer goalReps;
    private Double caloriesBurned;
    private Long timeTaken;
    private String completedAt;
    private String createdAt;
}
