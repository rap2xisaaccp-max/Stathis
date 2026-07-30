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
import java.time.OffsetDateTime;
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
                .flatMap(task -> buildProgressRows(task, studentId).stream())
                .sorted(Comparator.comparing(StudentProgressDTO::getClosingDate, Comparator.nullsLast(LocalDate::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * One progress row per task component so multi-template tasks (quiz+exercise)
     * appear in both teacher Quizzes and Exercises sections with correct scores/attempts.
     */
    private List<StudentProgressDTO> buildProgressRows(Task task, String studentId) {
        List<StudentProgressDTO> rows = new ArrayList<>();
        if (task.getQuizTemplateId() != null) {
            rows.add(buildComponentProgress(task, studentId, "QUIZ"));
        }
        if (task.getExerciseTemplateId() != null) {
            rows.add(buildComponentProgress(task, studentId, "EXERCISE"));
        }
        if (task.getLessonTemplateId() != null) {
            rows.add(buildComponentProgress(task, studentId, "LESSON"));
        }
        if (rows.isEmpty()) {
            rows.add(buildComponentProgress(task, studentId, "UNKNOWN"));
        }
        return rows;
    }

    private StudentProgressDTO buildComponentProgress(Task task, String studentId, String taskType) {
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
        OffsetDateTime completedAt = completionOpt.map(tc -> tc.getCompletedAt()).orElse(null);

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

        if ("EXERCISE".equals(taskType) && task.getExerciseTemplateId() != null) {
            var scoreOpt = scoreRepository.findExerciseScore(studentId, task.getPhysicalId(), task.getExerciseTemplateId());
            if (scoreOpt.isPresent()) {
                Score s = scoreOpt.get();
                scoreVal = ScoreService.effectiveScore(s);
                maxScoreVal = s.getMaxScore() > 0 ? s.getMaxScore() : 100;
                attemptsVal = s.getAttempts();
                repsVal = s.getReps();
                goalRepsVal = s.getGoalReps();
                if (completedAt == null) completedAt = s.getCompletedAt();
            }
            // #region agent log
            try {
                String payload = "{\"sessionId\":\"b7147e\",\"runId\":\"pre-fix\",\"hypothesisId\":\"B,E\",\"location\":\"StudentProgressService.buildComponentProgress\",\"message\":\"exercise progress row\",\"data\":{\"studentId\":\""
                        + studentId + "\",\"taskId\":\"" + task.getPhysicalId()
                        + "\",\"templateId\":\"" + task.getExerciseTemplateId()
                        + "\",\"scoreFound\":" + scoreOpt.isPresent()
                        + ",\"scoreVal\":" + scoreVal
                        + ",\"maxScoreVal\":" + maxScoreVal
                        + ",\"attempts\":" + attemptsVal
                        + ",\"reps\":" + repsVal
                        + ",\"goalReps\":" + goalRepsVal
                        + ",\"completed\":" + completed
                        + ",\"rawScore\":" + (scoreOpt.map(Score::getScore).orElse(null))
                        + ",\"manualScore\":" + (scoreOpt.map(Score::getManualScore).orElse(null))
                        + "},\"timestamp\":" + System.currentTimeMillis() + "}\n";
                for (String p : new String[]{
                        System.getProperty("user.dir") + java.io.File.separator + "debug-b7147e.log",
                        System.getProperty("user.dir") + java.io.File.separator + ".." + java.io.File.separator + ".." + java.io.File.separator + "debug-b7147e.log",
                        "C:\\Users\\ASUS\\Stathis\\debug-b7147e.log"
                }) {
                    try {
                        java.nio.file.Files.writeString(
                                java.nio.file.Path.of(p),
                                payload,
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND);
                        break;
                    } catch (Exception ignoredPath) {}
                }
            } catch (Exception ignored) {}
            // #endregion
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
}
