package edu.cit.stathis.task.service;

import edu.cit.stathis.classroom.service.ClassroomService;
import edu.cit.stathis.task.dto.StudentProgressDTO;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudentProgressService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ScoreRepository scoreRepository;

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @Autowired
    private ExerciseTemplateRepository exerciseTemplateRepository;

    @Autowired
    private ClassroomService classroomService;

    public List<StudentProgressDTO> getStudentProgress(String studentId, @Nullable String classroomId) {
        List<Task> tasks = new ArrayList<>();

        if (classroomId != null && !classroomId.isBlank()) {
            tasks.addAll(taskRepository.findByClassroomPhysicalId(classroomId));
        } else {
            // Fetch all classrooms where the student is enrolled (and verified) then collect tasks
            classroomService.getClassroomsByCurrentStudent().stream()
                    .map(c -> c.getPhysicalId())
                    .forEach(cid -> tasks.addAll(taskRepository.findByClassroomPhysicalId(cid)));
        }

        return tasks.stream()
                .map(task -> buildProgressDTO(task, studentId))
                .sorted(Comparator.comparing(StudentProgressDTO::getClosingDate, Comparator.nullsLast(LocalDate::compareTo)))
                .collect(Collectors.toList());
    }

    private StudentProgressDTO buildProgressDTO(Task task, String studentId) {
        String taskType = resolveTaskType(task);

        // Completion flags
        var completionOpt = taskCompletionRepository.findByStudentIdAndTaskId(studentId, task.getPhysicalId());
        boolean completed = completionOpt.map(tc -> {
            switch (taskType) {
                case "QUIZ":
                    return tc.isQuizCompleted() || tc.isFullyCompleted();
                case "EXERCISE":
                    return tc.isExerciseCompleted() || tc.isFullyCompleted();
                case "LESSON":
                    return tc.isLessonCompleted() || tc.isFullyCompleted();
                default:
                    return tc.isFullyCompleted();
            }
        }).orElse(false);

        Integer scoreVal = null;
        Integer maxScoreVal = null;
        Integer attemptsVal = null;
        var completedAt = completionOpt.map(tc -> tc.getCompletedAt()).orElse(null);

        if ("QUIZ".equals(taskType) && task.getQuizTemplateId() != null) {
            var scoreOpt = scoreRepository.findQuizScore(studentId, task.getPhysicalId(), task.getQuizTemplateId());
            if (scoreOpt.isPresent()) {
                Score s = scoreOpt.get();
                scoreVal = s.getScore();
                maxScoreVal = s.getMaxScore();
                attemptsVal = s.getAttempts();
                if (completedAt == null) completedAt = s.getCompletedAt();
            }
        }

        if ("EXERCISE".equals(taskType) && task.getExerciseTemplateId() != null) {
            var scoreOpt = scoreRepository.findExerciseScore(studentId, task.getPhysicalId(), task.getExerciseTemplateId());
            if (scoreOpt.isPresent()) {
                Score s = scoreOpt.get();
                scoreVal = s.getScore();
                maxScoreVal = s.getMaxScore() > 0 ? s.getMaxScore() : 100;
                attemptsVal = s.getAttempts();
                if (completedAt == null) completedAt = s.getCompletedAt();
            } else if (completed) {
                maxScoreVal = 100;
                attemptsVal = 1;
                scoreVal = calculateFallbackExerciseScore(task, completionOpt.map(tc -> tc.getRepsPerformed()).orElse(null));
            }
        }

        return StudentProgressDTO.builder()
                .taskId(task.getPhysicalId())
                .taskName(task.getName())
                .taskType(taskType)
                .classroomPhysicalId(task.getClassroomPhysicalId())
                .completed(completed)
                .score(scoreVal)
                .maxScore(maxScoreVal)
                .attempts(attemptsVal)
                .completedAt(completedAt)
                .submissionDate(task.getSubmissionDate() != null ? task.getSubmissionDate().toLocalDate() : null)
                .closingDate(task.getClosingDate() != null ? task.getClosingDate().toLocalDate() : null)
                .build();
    }

    private String resolveTaskType(Task task) {
        if (task.getQuizTemplateId() != null) return "QUIZ";
        if (task.getExerciseTemplateId() != null) return "EXERCISE";
        if (task.getLessonTemplateId() != null) return "LESSON";
        return "UNKNOWN";
    }

    private Integer calculateFallbackExerciseScore(Task task, Integer repsPerformed) {
        if (task.getExerciseTemplateId() == null) return null;
        var templateOpt = exerciseTemplateRepository.findByPhysicalId(task.getExerciseTemplateId());
        if (templateOpt.isEmpty()) return null;

        var template = templateOpt.get();
        int goalReps = template.getGoalReps();
        if (goalReps <= 0) return 100;

        int reps = repsPerformed != null ? Math.max(0, repsPerformed) : 0;
        int repsScore = (int) Math.round(Math.min(100.0, (reps / (double) goalReps) * 100.0));
        return repsScore;
    }
}


