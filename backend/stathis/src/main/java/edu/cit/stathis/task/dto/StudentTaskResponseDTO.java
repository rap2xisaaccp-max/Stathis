package edu.cit.stathis.task.dto;

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
    /** Template physical IDs — keep in sync with Task entity so mobile completion uses the same IDs. */
    private String exerciseTemplateId;
    private String lessonTemplateId;
    private String quizTemplateId;
    private int maxAttempts;
    private LessonTemplateResponseDTO lessonTemplate;
    private QuizTemplateResponseDTO quizTemplate;
    private ExerciseTemplateResponseDTO exerciseTemplate;
    private ScoreDTO score;
    private boolean isCompleted;
    private boolean isStarted;
    private String createdAt;
    private String updatedAt;
} 