package edu.cit.stathis.task.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseResultSubmissionDTO {
    @JsonAlias({"actualReps"})
    private int reps;

    @JsonAlias({"actualAccuracy"})
    private double accuracy;

    @JsonAlias({"actualTime"})
    private long timeTaken; // seconds from mobile exercise flow

    private int score;
    private int maxScore;
    private Boolean completed;
} 
