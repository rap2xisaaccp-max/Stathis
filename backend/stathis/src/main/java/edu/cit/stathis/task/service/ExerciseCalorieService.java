package edu.cit.stathis.task.service;

import edu.cit.stathis.auth.entity.UserProfile;
import edu.cit.stathis.auth.repository.UserProfileRepository;
import edu.cit.stathis.task.enums.ExerciseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Estimates calories burned from exercise type, repetition count, and student body weight.
 * Uses per-rep kcal estimates scaled to body weight (reference weight = 70 kg).
 */
@Service
public class ExerciseCalorieService {

    private static final double REFERENCE_WEIGHT_KG = 70.0;
    private static final double DEFAULT_WEIGHT_KG = 70.0;

    @Autowired
    private UserProfileRepository userProfileRepository;

    public double calculateCalories(String studentId, String exerciseType, int reps) {
        if (reps <= 0) {
            return 0.0;
        }
        double weightKg = resolveWeightKg(studentId);
        double perRep = caloriesPerRep(exerciseType);
        double calories = reps * perRep * (weightKg / REFERENCE_WEIGHT_KG);
        return Math.round(calories * 10.0) / 10.0;
    }

    public double resolveWeightKg(String studentId) {
        return userProfileRepository
                .findByUser_PhysicalId(studentId)
                .map(UserProfile::getWeightInKg)
                .filter(w -> w != null && w > 0)
                .orElse(DEFAULT_WEIGHT_KG);
    }

    private double caloriesPerRep(String exerciseType) {
        if (exerciseType == null || exerciseType.isBlank()) {
            return 0.25;
        }
        String normalized = exerciseType.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return caloriesPerRep(ExerciseType.valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            // Fall through to alias matching
        }
        return switch (normalized) {
            case "PUSHUP", "PUSHUPS", "PUSH_UPS", "WALL_PUSHUP", "WALL_PUSHUPS" -> 0.35;
            case "SQUAT", "SQUAT_S" -> 0.32;
            case "SIT_UP", "SIT_UPS", "SITUP", "SITUPS", "CRUNCH", "CRUNCHES" -> 0.25;
            case "GLUTE_BRIDGES" -> 0.20;
            case "STATIC_LUNGE", "LUNGE", "LUNGES" -> 0.30;
            case "LYING_LEG_RAISE", "LEG_RAISE", "LEG_RAISES" -> 0.20;
            default -> 0.25;
        };
    }

    private double caloriesPerRep(ExerciseType type) {
        return switch (type) {
            case PUSH_UP -> 0.35;
            case SQUATS -> 0.32;
            case GLUTE_BRIDGE -> 0.20;
            case STATIC_LUNGES -> 0.30;
            case LYING_LEG_RAISES -> 0.20;
        };
    }
}
