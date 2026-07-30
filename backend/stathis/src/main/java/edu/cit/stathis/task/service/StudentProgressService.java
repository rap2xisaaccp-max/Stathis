package edu.cit.stathis.task.service;

import edu.cit.stathis.classroom.service.ClassroomService;
import edu.cit.stathis.task.dto.StudentProgressDTO;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.repository.TaskRepository;
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
        Integer repsVal = null;
        Integer goalRepsVal = null;
        var completedAt = completionOpt.map(tc -> tc.getCompletedAt()).orElse(null);

        if ("QUIZ".equals(taskType) && task.getQuizTemplateId() != null) {
            var scoreOpt = scoreRepository.findQuizScore(studentId, task.getPhysicalId(), task.getQuizTemplateId());
            if (scoreOpt.isPresent()) {
                Score s = scoreOpt.get();
                scoreVal = ScoreService.effectiveScore(s);
                maxScoreVal = s.getMaxScore();
                attemptsVal = s.getAttempts();
                if (completedAt == null) completedAt = s.getCompletedAt();
            }
        }

        // Always attach exercise metrics when the task has an exercise template, even if the
        // primary type is QUIZ/LESSON (multi-component tasks) so Student Progress can show reps/score.
        if (task.getExerciseTemplateId() != null) {
            var scoreOpt = scoreRepository.findExerciseScore(studentId, task.getPhysicalId(), task.getExerciseTemplateId());
            if (scoreOpt.isPresent()) {
                Score s = scoreOpt.get();
                if ("EXERCISE".equals(taskType) || scoreVal == null) {
                    scoreVal = ScoreService.effectiveScore(s);
                    maxScoreVal = s.getMaxScore() > 0 ? s.getMaxScore() : 100;
                    attemptsVal = s.getAttempts();
                }
                repsVal = s.getReps();
                goalRepsVal = s.getGoalReps();
                if (completedAt == null) completedAt = s.getCompletedAt();
                if (!completed && (s.isCompleted() || (s.getAttempts() > 0))) {
                    completed = true;
                }
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
                .reps(repsVal)
                .goalReps(goalRepsVal)
                .completedAt(completedAt)
                .submissionDate(task.getSubmissionDate() != null ? task.getSubmissionDate().toLocalDate() : null)
                .closingDate(task.getClosingDate() != null ? task.getClosingDate().toLocalDate() : null)
                .build();
    }

    private String resolveTaskType(Task task) {
        boolean hasQuiz = task.getQuizTemplateId() != null;
        boolean hasExercise = task.getExerciseTemplateId() != null;
        boolean hasLesson = task.getLessonTemplateId() != null;
        int count = (hasQuiz ? 1 : 0) + (hasExercise ? 1 : 0) + (hasLesson ? 1 : 0);

        // Single-component tasks: use that component's type so exercise-only assignments
        // always appear under Exercises in Student Progress.
        if (count == 1) {
            if (hasQuiz) return "QUIZ";
            if (hasExercise) return "EXERCISE";
            return "LESSON";
        }

        if (hasQuiz) return "QUIZ";
        if (hasExercise) return "EXERCISE";
        if (hasLesson) return "LESSON";
        return "UNKNOWN";
    }
}

