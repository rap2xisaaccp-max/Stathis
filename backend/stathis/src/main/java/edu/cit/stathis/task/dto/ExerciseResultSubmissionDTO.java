package edu.cit.stathis.task.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseResultSubmissionDTO {
    private int reps;
    private double accuracy;
    private long timeTaken; // in milliseconds
    private Integer goalReps;
    private Double caloriesBurned; // calories for this session; server may recompute
    private String exerciseType; // e.g. PUSH_UP, SQUATS
    private String classroomId; // optional, for live teacher sync
}
