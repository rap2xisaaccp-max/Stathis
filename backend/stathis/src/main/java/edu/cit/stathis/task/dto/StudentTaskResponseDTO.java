package edu.cit.stathis.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentTaskResponseDTO {
    private String physicalId;
    private String name;
    private String description;
    private String submissionDate;
    private String closingDate;
    private String imageUrl;
    private String classroomPhysicalId;
    private LessonTemplateResponseDTO lessonTemplate;
    private QuizTemplateResponseDTO quizTemplate;
    private ExerciseTemplateResponseDTO exerciseTemplate;
    private ScoreDTO score;
    private boolean isCompleted;
    @JsonProperty("started")
    private boolean isStarted;
    @JsonProperty("active")
    private boolean isActive;
    private String createdAt;
    private String updatedAt;
} 