package edu.cit.stathis.task.controller;

import edu.cit.stathis.task.dto.ScoreDTO;
import edu.cit.stathis.task.dto.ScoreResponseDTO;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.service.ScoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/scores", "/api/scores"})
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Create a new score", description = "Create a new score")
    public ResponseEntity<Score> createScore(
            @RequestParam String studentId,
            @RequestParam String taskId,
            @RequestParam String templateId,
            @RequestParam boolean isQuiz) {
        return ResponseEntity.ok(scoreService.createScore(studentId, taskId, templateId, isQuiz));
    }

    @PutMapping("/{physicalId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Update a score", description = "Update a score")
    public ResponseEntity<ScoreResponseDTO> updateScore(
            @PathVariable String physicalId,
            @Valid @RequestBody ScoreDTO scoreDTO) {
        return ResponseEntity.ok(scoreService.updateScore(physicalId, scoreDTO));
    }

    @GetMapping("/{physicalId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    @Operation(summary = "Get a score by its physical ID", description = "Get a score by its physical ID")
    public ResponseEntity<ScoreResponseDTO> getScore(@PathVariable String physicalId) {
        return scoreService.getScoreByPhysicalId(physicalId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    @Operation(summary = "Get scores by student ID", description = "Get scores by student ID")
    public ResponseEntity<List<ScoreResponseDTO>> getScoresByStudent(@PathVariable String studentId) {
        return ResponseEntity.ok(scoreService.getScoresByStudent(studentId));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get scores by task ID", description = "Get scores by task ID")
    public ResponseEntity<List<ScoreResponseDTO>> getScoresByTask(@PathVariable String taskId) {
        return ResponseEntity.ok(scoreService.getScoresByTask(taskId));
    }

    @GetMapping("/student/{studentId}/task/{taskId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    @Operation(summary = "Get scores by student and task ID", description = "Get scores by student and task ID")
    public ResponseEntity<List<ScoreResponseDTO>> getScoresByStudentAndTask(
            @PathVariable String studentId,
            @PathVariable String taskId) {
        return ResponseEntity.ok(scoreService.getScoresByStudentAndTask(studentId, taskId));
    }

    @GetMapping("/quiz")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    @Operation(summary = "Get a quiz score by student ID, task ID, and quiz template ID", description = "Get a quiz score by student ID, task ID, and quiz template ID")
    public ResponseEntity<ScoreResponseDTO> getQuizScore(
            @RequestParam String studentId,
            @RequestParam String taskId,
            @RequestParam String quizTemplateId) {
        return scoreService.getQuizScore(studentId, taskId, quizTemplateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exercise")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    @Operation(summary = "Get an exercise score by student ID, task ID, and exercise template ID", description = "Get an exercise score by student ID, task ID, and exercise template ID")
    public ResponseEntity<ScoreResponseDTO> getExerciseScore(
            @RequestParam String studentId,
            @RequestParam String taskId,
            @RequestParam String exerciseTemplateId) {
        return scoreService.getExerciseScore(studentId, taskId, exerciseTemplateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/quiz/average")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get the average quiz score percentage by task ID and quiz template ID")
    public ResponseEntity<Double> getAverageQuizScore(
            @RequestParam String taskId,
            @RequestParam String quizTemplateId) {
        return ResponseEntity.ok(scoreService.getAverageQuizScore(taskId, quizTemplateId));
    }

    @GetMapping("/exercise/average")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Get the average exercise score percentage by task ID and exercise template ID")
    public ResponseEntity<Double> getAverageExerciseScore(
            @RequestParam String taskId,
            @RequestParam String exerciseTemplateId) {
        return ResponseEntity.ok(scoreService.getAverageExerciseScore(taskId, exerciseTemplateId));
    }

    @PutMapping("/{physicalId}/manual-grade")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Update a manual score", description = "Update a manual score")
    public ResponseEntity<ScoreResponseDTO> updateManualScore(
            @PathVariable String physicalId,
            @RequestParam Integer manualScore,
            @RequestParam(required = false) String teacherFeedback) {
        return ResponseEntity.ok(scoreService.updateManualScore(physicalId, manualScore, teacherFeedback));
    }
}
