package edu.cit.stathis.task.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseProgressDTO {
    private String studentId;
    private String studentName;
    private String classroomId;
    private String taskId;
    private String exerciseTemplateId;
    private String exerciseType;
    private int reps;
    private Integer goalReps;
    private double accuracy;
    private long timeTakenMs;
    private Double sessionCaloriesBurned;
    private Double totalCaloriesBurned;
    private Integer score;
    private boolean completed;
    private String timestamp;
}
