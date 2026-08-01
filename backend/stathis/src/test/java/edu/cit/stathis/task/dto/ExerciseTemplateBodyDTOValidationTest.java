package edu.cit.stathis.task.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExerciseTemplateBodyDTOValidationTest {

    private final Validator validator;

    ExerciseTemplateBodyDTOValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void acceptsExpertDifficultyAndGluteBridgeExerciseType() {
        ExerciseTemplateBodyDTO dto = ExerciseTemplateBodyDTO.builder()
            .title("Glute Bridge Template")
            .description("A glute bridge template for class")
            .exerciseType("GLUTE_BRIDGE")
            .exerciseDifficulty("EXPERT")
            .goalReps("12")
            .goalAccuracy("85")
            .goalTime("45")
            .build();

        Set<ConstraintViolation<ExerciseTemplateBodyDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), () -> "Expected no validation errors, but got: " + violations);
    }
}
