package edu.cit.stathis.task.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExerciseTemplateBodyDTOValidationTest {

    private final Validator validator;

    ExerciseTemplateBodyDTOValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void acceptsAdvancedDifficultyAndGluteBridgeExerciseType() {
        ExerciseTemplateBodyDTO dto = ExerciseTemplateBodyDTO.builder()
            .title("Glute Bridge Template")
            .description("A glute bridge template for class")
            .exerciseType("GLUTE_BRIDGE")
            .exerciseDifficulty("ADVANCED")
            .goalReps("30")
            .goalAccuracy("85")
            .goalTime("45")
            .build();

        Set<ConstraintViolation<ExerciseTemplateBodyDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), () -> "Expected no validation errors, but got: " + violations);
    }

    @Test
    void rejectsExpertDifficultyForNewTemplates() {
        ExerciseTemplateBodyDTO dto = ExerciseTemplateBodyDTO.builder()
            .title("Push Up Template")
            .description("A push up template for class")
            .exerciseType("PUSH_UP")
            .exerciseDifficulty("EXPERT")
            .goalReps("20")
            .goalAccuracy("80")
            .goalTime("60")
            .build();

        Set<ConstraintViolation<ExerciseTemplateBodyDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(
            violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("exerciseDifficulty")));
    }
}
