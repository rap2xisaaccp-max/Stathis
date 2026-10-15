package edu.cit.stathis.task.dto;

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
    private int exerciseAttempts;
    private int exerciseScore;
    private int maxExerciseScore;
    private Integer exerciseReps;
    private Integer exerciseGoalReps;
    private java.util.List<edu.cit.stathis.task.dto.TaskExerciseProgressDTO> exercises;
    private int exercisesCompleted;
    private int exercisesRequired;
    private Long totalTimeTaken;
    private String startedAt;
    private String completedAt;
    private boolean submittedForReview;
    private String submittedAt;
    /** Fully complete for the templates this task actually requires. */
    private boolean completed;
} 
