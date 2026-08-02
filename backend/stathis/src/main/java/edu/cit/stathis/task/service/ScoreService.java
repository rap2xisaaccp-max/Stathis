package edu.cit.stathis.task.service;

import edu.cit.stathis.task.dto.ScoreAttemptResponseDTO;
import edu.cit.stathis.task.dto.ScoreDTO;
import edu.cit.stathis.task.dto.ScoreResponseDTO;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.ScoreAttempt;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.repository.ScoreAttemptRepository;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreService {
    private final ScoreRepository scoreRepository;
    private final ScoreAttemptRepository scoreAttemptRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public Score createScore(String studentId, String taskId, String templateId, boolean isQuiz) {
        Score score = Score.builder()
                .physicalId(generatePhysicalId())
                .studentId(studentId)
                .taskId(taskId)
                .quizTemplateId(isQuiz ? templateId : null)
                .exerciseTemplateId(!isQuiz ? templateId : null)
                .score(0)
                .maxScore(0)
                .attempts(0)
                .isCompleted(false)
                .timeTaken(0L)
                .accuracy(0.0)
                .startedAt(OffsetDateTime.now())
                .build();
        return scoreRepository.save(score);
    }

    @Transactional
    public ScoreResponseDTO updateScore(String physicalId, ScoreDTO scoreDTO) {
        Score score = scoreRepository.findByPhysicalId(physicalId)
                .orElseThrow(() -> new EntityNotFoundException("Score not found with physical ID: " + physicalId));
        score.setScore(scoreDTO.getScore());
        score.setMaxScore(scoreDTO.getMaxScore());
        score.setAttempts(scoreDTO.getAttempts());
        score.setCompleted(scoreDTO.isCompleted());
        score.setTeacherFeedback(scoreDTO.getTeacherFeedback());
        score.setManualScore(scoreDTO.getManualScore());
        if (scoreDTO.isCompleted()) {
            score.setCompletedAt(OffsetDateTime.now());
        }
        return toResponse(scoreRepository.save(score));
    }

    @Transactional(readOnly = true)
    public Optional<ScoreResponseDTO> getScoreByPhysicalId(String physicalId) {
        return scoreRepository.findByPhysicalId(physicalId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ScoreResponseDTO> getScoresByStudent(String studentId) {
        return mapAll(scoreRepository.findByStudentId(studentId));
    }

    @Transactional(readOnly = true)
    public List<ScoreResponseDTO> getScoresByTask(String taskId) {
        return mapAll(scoreRepository.findByTaskId(taskId));
    }

    @Transactional(readOnly = true)
    public List<ScoreResponseDTO> getScoresByStudentAndTask(String studentId, String taskId) {
        return mapAll(scoreRepository.findByStudentIdAndTaskId(studentId, taskId));
    }

    @Transactional(readOnly = true)
    public List<ScoreAttemptResponseDTO> getAttemptsByStudentAndTask(String studentId, String taskId) {
        List<ScoreAttempt> attempts =
                scoreAttemptRepository.findByStudentIdAndTaskIdOrderByAttemptNumberAsc(studentId, taskId);
        if (!attempts.isEmpty()) {
            return attempts.stream().map(this::toAttemptResponse).collect(Collectors.toList());
        }
        // Fallback: surface latest aggregate Score as a single synthetic attempt so teachers
        // can still see accuracy for data recorded before attempt history existed.
        return scoreRepository.findByStudentIdAndTaskId(studentId, taskId).stream()
                .filter(s -> s.getAttempts() > 0 || s.isCompleted())
                .map(this::toSyntheticAttempt)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ScoreResponseDTO> getQuizScore(String studentId, String taskId, String quizTemplateId) {
        return scoreRepository.findQuizScore(studentId, taskId, quizTemplateId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<ScoreResponseDTO> getExerciseScore(String studentId, String taskId, String exerciseTemplateId) {
        return scoreRepository.findExerciseScore(studentId, taskId, exerciseTemplateId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Double getAverageQuizScore(String taskId, String quizTemplateId) {
        Double avg = scoreRepository.getAverageQuizScore(taskId, quizTemplateId);
        return avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
    }

    @Transactional(readOnly = true)
    public Double getAverageExerciseScore(String taskId, String exerciseTemplateId) {
        Double avg = scoreRepository.getAverageExerciseScore(taskId, exerciseTemplateId);
        return avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
    }

    @Transactional
    public ScoreResponseDTO updateManualScore(String physicalId, Integer manualScore, String teacherFeedback) {
        Score score = scoreRepository.findByPhysicalId(physicalId)
                .orElseThrow(() -> new EntityNotFoundException("Score not found with physical ID: " + physicalId));
        score.setManualScore(manualScore);
        score.setTeacherFeedback(teacherFeedback);
        score.setCompleted(true);
        if (score.getCompletedAt() == null) {
            score.setCompletedAt(OffsetDateTime.now());
        }
        return toResponse(scoreRepository.save(score));
    }

    @Transactional(readOnly = true)
    public boolean existsByPhysicalId(String physicalId) {
        return scoreRepository.existsByPhysicalId(physicalId);
    }

    @Transactional(readOnly = true)
    public boolean existsQuizScore(String studentId, String taskId, String quizTemplateId) {
        return scoreRepository.existsQuizScore(studentId, taskId, quizTemplateId);
    }

    @Transactional(readOnly = true)
    public boolean existsExerciseScore(String studentId, String taskId, String exerciseTemplateId) {
        return scoreRepository.existsExerciseScore(studentId, taskId, exerciseTemplateId);
    }

    public static int effectiveScore(Score score) {
        if (score.getManualScore() != null) {
            return score.getManualScore();
        }
        return score.getScore();
    }

    private List<ScoreResponseDTO> mapAll(List<Score> scores) {
        Map<String, Integer> maxAttemptsByTask = loadMaxAttempts(scores);
        return scores.stream()
                .map(s -> toResponse(s, maxAttemptsByTask.get(s.getTaskId())))
                .collect(Collectors.toList());
    }

    private Map<String, Integer> loadMaxAttempts(List<Score> scores) {
        Map<String, Integer> result = new HashMap<>();
        scores.stream()
                .map(Score::getTaskId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .forEach(taskId -> taskRepository.findByPhysicalId(taskId)
                        .ifPresent(task -> result.put(taskId, task.getMaxAttempts())));
        return result;
    }

    private ScoreResponseDTO toResponse(Score score) {
        Integer maxAttempts = null;
        if (score.getTaskId() != null) {
            maxAttempts = taskRepository.findByPhysicalId(score.getTaskId())
                    .map(Task::getMaxAttempts)
                    .orElse(null);
        }
        return toResponse(score, maxAttempts);
    }

    private ScoreResponseDTO toResponse(Score score, Integer maxAttempts) {
        int attempts = score.getAttempts();
        int remaining;
        if (maxAttempts == null || maxAttempts <= 0) {
            remaining = Math.max(0, 999 - attempts);
        } else {
            remaining = Math.max(0, maxAttempts - attempts);
        }

        String status;
        if (score.getManualScore() != null) {
            status = "GRADED";
        } else if (score.isCompleted() || attempts > 0) {
            status = "COMPLETED";
        } else {
            status = "PENDING";
        }

        String submissionDate = null;
        if (score.getCompletedAt() != null) {
            submissionDate = score.getCompletedAt().toString();
        } else if (score.getUpdatedAt() != null) {
            submissionDate = score.getUpdatedAt().toString();
        }

        return ScoreResponseDTO.builder()
                .physicalId(score.getPhysicalId())
                .studentId(score.getStudentId())
                .taskId(score.getTaskId())
                .quizTemplateId(score.getQuizTemplateId())
                .exerciseTemplateId(score.getExerciseTemplateId())
                .score(effectiveScore(score))
                .maxScore(score.getMaxScore())
                .attempts(attempts)
                .remainingAttempts(remaining)
                .isCompleted(score.isCompleted())
                .teacherFeedback(score.getTeacherFeedback())
                .feedback(score.getTeacherFeedback())
                .manualScore(score.getManualScore())
                .reps(score.getReps())
                .goalReps(score.getGoalReps())
                .accuracy(score.getAccuracy())
                .caloriesBurned(score.getCaloriesBurned())
                .status(status)
                .submissionDate(submissionDate)
                .startedAt(score.getStartedAt() != null ? score.getStartedAt().toString() : null)
                .completedAt(score.getCompletedAt() != null ? score.getCompletedAt().toString() : null)
                .build();
    }

    private ScoreAttemptResponseDTO toAttemptResponse(ScoreAttempt attempt) {
        return ScoreAttemptResponseDTO.builder()
                .physicalId(attempt.getPhysicalId())
                .scorePhysicalId(attempt.getScorePhysicalId())
                .studentId(attempt.getStudentId())
                .taskId(attempt.getTaskId())
                .quizTemplateId(attempt.getQuizTemplateId())
                .exerciseTemplateId(attempt.getExerciseTemplateId())
                .attemptNumber(attempt.getAttemptNumber())
                .score(attempt.getScore())
                .maxScore(attempt.getMaxScore())
                .accuracy(attempt.getAccuracy())
                .reps(attempt.getReps())
                .goalReps(attempt.getGoalReps())
                .caloriesBurned(attempt.getCaloriesBurned())
                .timeTaken(attempt.getTimeTaken())
                .completedAt(attempt.getCompletedAt() != null ? attempt.getCompletedAt().toString() : null)
                .createdAt(attempt.getCreatedAt() != null ? attempt.getCreatedAt().toString() : null)
                .build();
    }

    private ScoreAttemptResponseDTO toSyntheticAttempt(Score score) {
        return ScoreAttemptResponseDTO.builder()
                .physicalId(score.getPhysicalId() + "-LATEST")
                .scorePhysicalId(score.getPhysicalId())
                .studentId(score.getStudentId())
                .taskId(score.getTaskId())
                .quizTemplateId(score.getQuizTemplateId())
                .exerciseTemplateId(score.getExerciseTemplateId())
                .attemptNumber(Math.max(1, score.getAttempts()))
                .score(effectiveScore(score))
                .maxScore(score.getMaxScore())
                .accuracy(score.getAccuracy())
                .reps(score.getReps())
                .goalReps(score.getGoalReps())
                .caloriesBurned(score.getCaloriesBurned())
                .timeTaken(score.getTimeTaken())
                .completedAt(score.getCompletedAt() != null ? score.getCompletedAt().toString() : null)
                .createdAt(score.getUpdatedAt() != null ? score.getUpdatedAt().toString() : null)
                .build();
    }

    private String generatePhysicalId() {
        return "SCORE-" + UUID.randomUUID().toString().toUpperCase();
    }
}
