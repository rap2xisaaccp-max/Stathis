package edu.cit.stathis.task.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseResultSubmissionDTO {
    @NotNull(message = "Repetitions are required")
    @PositiveOrZero(message = "Repetitions cannot be negative")
    private Integer reps;

    @NotNull(message = "Accuracy is required")
    @DecimalMin(value = "0.0", message = "Accuracy cannot be negative")
    @DecimalMax(value = "100.0", message = "Accuracy cannot exceed 100")
    private Double accuracy;

    @NotNull(message = "Time taken is required")
    @PositiveOrZero(message = "Time taken cannot be negative")
    private Long timeTaken;
} 
