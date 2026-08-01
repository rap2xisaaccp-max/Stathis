package edu.cit.stathis.task.controller;

import edu.cit.stathis.auth.service.PhysicalIdService;
import edu.cit.stathis.task.dto.ExerciseProgressDTO;
import edu.cit.stathis.task.service.ExerciseCalorieService;
import edu.cit.stathis.task.service.ExerciseProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercise-progress")
@Tag(name = "Exercise Progress", description = "Real-time exercise progress for teacher dashboards")
public class ExerciseProgressController {

    @Autowired
    private ExerciseProgressService exerciseProgressService;

    @Autowired
    private ExerciseCalorieService exerciseCalorieService;

    @Autowired
    private PhysicalIdService physicalIdService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Publish live exercise progress", description = "Accepts in-progress or completed exercise stats and broadcasts to subscribed teachers")
    public ResponseEntity<ExerciseProgressDTO> publishProgress(@RequestBody ExerciseProgressDTO progress) {
        String studentId = physicalIdService.getCurrentUserPhysicalId();
        progress.setStudentId(studentId);

        if (progress.getSessionCaloriesBurned() == null || progress.getSessionCaloriesBurned() <= 0) {
            progress.setSessionCaloriesBurned(
                    exerciseCalorieService.calculateCalories(
                            studentId, progress.getExerciseType(), progress.getReps()));
        }

        if (progress.getScore() == null) {
            Integer goalReps = progress.getGoalReps();
            if (goalReps != null && goalReps > 0) {
                progress.setScore((int) Math.round(
                        Math.min(1.0, (double) progress.getReps() / goalReps) * 100.0));
            } else {
                progress.setScore(0);
            }
        }

        exerciseProgressService.publishProgress(progress);
        return ResponseEntity.ok(progress);
    }
}
