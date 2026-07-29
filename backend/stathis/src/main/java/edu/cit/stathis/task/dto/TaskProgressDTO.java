package edu.cit.stathis.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskProgressDTO {
    private boolean lessonCompleted;
    private boolean exerciseCompleted;
    private boolean quizCompleted;
    private int quizScore;
    private int maxQuizScore;
    private int quizAttempts;
    private int exerciseReps;
    private int goalExerciseReps;
    private int exerciseScore;
    private int maxExerciseScore;
    private int exerciseAttempts;
    @JsonProperty("isCompleted")
    private boolean isCompleted;
    private Long totalTimeTaken;
    private String startedAt;
    private String completedAt;
    private boolean submittedForReview;
    private String submittedAt;
} 
