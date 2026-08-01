package edu.cit.stathis.task.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import edu.cit.stathis.task.repository.TaskRepository;
import edu.cit.stathis.task.repository.ScoreRepository;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.TaskCompletion;
import edu.cit.stathis.task.dto.StudentTaskResponseDTO;
import edu.cit.stathis.task.dto.TaskProgressDTO;
import edu.cit.stathis.task.dto.LessonTemplateResponseDTO;
import edu.cit.stathis.task.dto.QuizTemplateResponseDTO;
import edu.cit.stathis.task.dto.ExerciseTemplateResponseDTO;
import edu.cit.stathis.task.dto.ScoreDTO;
import edu.cit.stathis.task.repository.LessonTemplateRepository;
import edu.cit.stathis.task.repository.QuizTemplateRepository;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.dto.QuizSubmissionDTO;
import edu.cit.stathis.task.dto.ExerciseResultSubmissionDTO;
import edu.cit.stathis.task.dto.ExerciseProgressDTO;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import edu.cit.stathis.classroom.service.ClassroomService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import edu.cit.stathis.common.debug.AgentDebugLog;
import java.util.Map;

@Service
public class StudentTaskService {
    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ScoreRepository scoreRepository;

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @Autowired
    private LessonTemplateRepository lessonTemplateRepository;

    @Autowired
    private QuizTemplateRepository quizTemplateRepository;

    @Autowired
    private ExerciseTemplateRepository exerciseTemplateRepository;

    @Autowired
    private ExerciseCalorieService exerciseCalorieService;

    @Autowired
    private ExerciseProgressService exerciseProgressService;

    @Autowired
    private ClassroomService classroomService;

    @Transactional(readOnly = true)
    public List<StudentTaskResponseDTO> getStudentTasks(String classroomPhysicalId, String studentId) {
        // Only allow if student is enrolled and verified
        if (!classroomService.isUserEnrolledAndVerifiedInClassroom(studentId, classroomPhysicalId)) {
            throw new RuntimeException("You are not authorized to view tasks for this classroom (not verified)");
        }
        List<Task> tasks = taskRepository.findByClassroomPhysicalId(classroomPhysicalId);
        return tasks.stream()
            .map(task -> buildStudentTaskResponse(task, studentId))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StudentTaskResponseDTO getStudentTask(String taskId, String studentId) {
        Task task = taskRepository.findByPhysicalId(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        return buildStudentTaskResponse(task, studentId);
    }

    @Transactional(readOnly = true)
    public TaskProgressDTO getTaskProgress(String taskId, String studentId) {
        Task task = taskRepository.findByPhysicalId(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        java.util.List<TaskCompletion> completionRows =
                taskCompletionRepository.findAllByStudentIdAndTaskId(studentId, taskId);
        // #region agent log
        AgentDebugLog.log("B", "StudentTaskService.getTaskProgress", "progress lookup",
                Map.of("taskId", taskId, "studentId", studentId, "completionRowCount", completionRows.size(),
                        "taskExerciseTemplateId", String.valueOf(task.getExerciseTemplateId())));
        // #endregion
        TaskCompletion completion = completionRows.isEmpty() ? null : completionRows.get(0);
        Score quizScore = task.getQuizTemplateId() != null
            ? scoreRepository.findQuizScore(studentId, taskId, task.getQuizTemplateId()).orElse(null)
            : null;
        Score exerciseScore = task.getExerciseTemplateId() != null
            ? scoreRepository.findExerciseScore(studentId, taskId, task.getExerciseTemplateId()).orElse(null)
            : null;

        if (completion == null) {
            return TaskProgressDTO.builder()
                .lessonCompleted(false)
                .exerciseCompleted(false)
                .quizCompleted(false)
                .quizScore(0)
                .maxQuizScore(0)
                .quizAttempts(0)
                .exerciseAttempts(exerciseScore != null ? exerciseScore.getAttempts() : 0)
                .exerciseScore(exerciseScore != null ? exerciseScore.getScore() : 0)
                .maxExerciseScore(exerciseScore != null ? exerciseScore.getMaxScore() : 100)
                .exerciseReps(exerciseScore != null ? exerciseScore.getReps() : 0)
                .exerciseGoalReps(exerciseScore != null ? exerciseScore.getGoalReps() : null)
                .totalTimeTaken(0L)
                .build();
        }

        return TaskProgressDTO.builder()
            .lessonCompleted(completion.isLessonCompleted())
            .exerciseCompleted(completion.isExerciseCompleted())
            .quizCompleted(completion.isQuizCompleted())
            .quizScore(quizScore != null ? quizScore.getScore() : 0)
            .maxQuizScore(quizScore != null ? quizScore.getMaxScore() : 0)
            .quizAttempts(quizScore != null ? quizScore.getAttempts() : 0)
            .exerciseAttempts(exerciseScore != null ? exerciseScore.getAttempts() : 0)
            .exerciseScore(exerciseScore != null ? exerciseScore.getScore() : 0)
            .maxExerciseScore(exerciseScore != null ? exerciseScore.getMaxScore() : 100)
            .exerciseReps(exerciseScore != null ? exerciseScore.getReps() : 0)
            .exerciseGoalReps(exerciseScore != null ? exerciseScore.getGoalReps() : null)
            .totalTimeTaken(completion.getTotalTimeTaken())
            .startedAt(completion.getStartedAt().toString())
            .completedAt(completion.getCompletedAt() != null ? completion.getCompletedAt().toString() : null)
            .build();
    }

    @Transactional
    public Score submitQuizScore(String studentId, String taskId, String quizTemplateId, int score) {
        Score existingScore = scoreRepository.findQuizScore(studentId, taskId, quizTemplateId)
            .orElse(null);

        if (existingScore == null) {
            existingScore = new Score();
            existingScore.setPhysicalId(provideUniquePhysicalId());
            existingScore.setStudentId(studentId);
            existingScore.setTaskId(taskId);
            existingScore.setQuizTemplateId(quizTemplateId);
            existingScore.setAttempts(0);
        }

        // Enforce max attempts based on Task configuration
        Task task = taskRepository.findByPhysicalId(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        validateAttempts(task, existingScore);

        // Ensure maxScore is populated from quiz template if available
        if (existingScore.getMaxScore() <= 0) {
            var optTemplate = quizTemplateRepository.findByPhysicalId(quizTemplateId);
            if (optTemplate.isPresent()) {
                var quizTemplate = optTemplate.get();
                int templateMax = 0;
                try {
                    Object quizNode = quizTemplate.getContent() != null ? quizTemplate.getContent().get("quiz") : null;
                    if (quizNode instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        var quizMap = (java.util.Map<String, Object>) quizNode;
                        Object maxScoreNode = quizMap.get("maxScore");
                        if (maxScoreNode instanceof Number) {
                            templateMax = ((Number) maxScoreNode).intValue();
                        } else {
                            Object contentNode = quizMap.get("content");
                            if (contentNode instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                var contentMap = (java.util.Map<String, Object>) contentNode;
                                Object questionsNode = contentMap.get("questions");
                                if (questionsNode instanceof java.util.List) {
                                    templateMax = ((java.util.List<?>) questionsNode).size();
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (templateMax <= 0) {
                    templateMax = quizTemplate.getMaxScore();
                }
                existingScore.setMaxScore(templateMax);
            }
        }

        existingScore.setScore(score);
        existingScore.setAttempts(existingScore.getAttempts() + 1);
        existingScore.setCompleted(true);
        existingScore.setCompletedAt(OffsetDateTime.now());

        Score savedScore = scoreRepository.save(existingScore);
        updateTaskCompletion(studentId, taskId, "quiz", true);
        return savedScore;
    }

    @Transactional
    public Score autoCheckQuiz(String studentId, String taskId, String quizTemplateId, QuizSubmissionDTO submission) {
        // Load quiz template JSON
        var quizTemplate = quizTemplateRepository.findByPhysicalId(quizTemplateId)
            .orElseThrow(() -> new EntityNotFoundException("Quiz template not found with ID: " + quizTemplateId));

        // Accept either:
        // A) { "quiz": { "content": { "questions": [...] }, "maxScore": n } }
        // B) { "content": { "questions": [...] }, "maxScore": n }
        int computedScore = 0;
        int maxScore = 0;
        try {
            var contentMapRoot = quizTemplate.getContent();
            if (contentMapRoot != null) {
                Object quizNode = contentMapRoot.get("quiz");
                // Determine quiz root (support both shapes)
                @SuppressWarnings("unchecked")
                var quizRoot = (quizNode instanceof java.util.Map)
                        ? (java.util.Map<String, Object>) quizNode
                        : contentMapRoot; // fallback to root if no "quiz"

                Object contentNode = quizRoot.get("content");
                @SuppressWarnings("unchecked")
                var effectiveContent = (contentNode instanceof java.util.Map)
                        ? (java.util.Map<String, Object>) contentNode
                        : quizRoot; // some templates may place questions directly under root

                Object questionsNode = effectiveContent.get("questions");
                if (questionsNode instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    var questions = (java.util.List<Object>) questionsNode;
                    maxScore = questions.size();
                    for (int i = 0; i < questions.size(); i++) {
                        Object qNode = questions.get(i);
                        if (!(qNode instanceof java.util.Map)) continue;
                        @SuppressWarnings("unchecked")
                        var qMap = (java.util.Map<String, Object>) qNode;
                        Object answerObj = qMap.get("answer");
                        if (answerObj instanceof Number) {
                            int correctIndex = ((Number) answerObj).intValue();
                            int studentIndex = (submission.getAnswers() != null && submission.getAnswers().size() > i)
                                ? submission.getAnswers().get(i)
                                : -1;
                            if (studentIndex == correctIndex) {
                                computedScore += 1; // one point per question
                            }
                        }
                    }
                }

                // Allow maxScore override from quizRoot if provided
                Object maxScoreNode = quizRoot.get("maxScore");
                if (maxScoreNode instanceof Number) {
                    maxScore = ((Number) maxScoreNode).intValue();
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to compute quiz score from template content", ex);
        }

        // Final fallback: if still not positive, use template header maxScore
        if (maxScore <= 0) {
            maxScore = quizTemplate.getMaxScore();
        }

        // Persist using existing flow
        Score existingScore = scoreRepository.findQuizScore(studentId, taskId, quizTemplateId).orElse(null);
        if (existingScore == null) {
            existingScore = new Score();
            existingScore.setPhysicalId(provideUniquePhysicalId());
            existingScore.setStudentId(studentId);
            existingScore.setTaskId(taskId);
            existingScore.setQuizTemplateId(quizTemplateId);
            existingScore.setAttempts(0);
        }

        // Enforce max attempts based on Task configuration
        Task task = taskRepository.findByPhysicalId(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        validateAttempts(task, existingScore);

        existingScore.setScore(computedScore);
        // If template carries maxScore, set it; else fall back to question count
        existingScore.setMaxScore(maxScore);
        existingScore.setAttempts(existingScore.getAttempts() + 1);
        existingScore.setCompleted(true);
        existingScore.setCompletedAt(OffsetDateTime.now());

        Score savedScore = scoreRepository.save(existingScore);
        updateTaskCompletion(studentId, taskId, "quiz", true);
        return savedScore;
    }

    private void validateAttempts(Task task, Score existingScore) {
        if (task.getMaxAttempts() > 0 && existingScore.getAttempts() >= task.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum attempts reached for this task");
        }
    }

    @Transactional
    public void completeLesson(String studentId, String taskId, String lessonTemplateId) {
        updateTaskCompletion(studentId, taskId, "lesson", true);
    }

    @Transactional
    public Score completeExercise(
            String studentId,
            String taskId,
            String exerciseTemplateId,
            ExerciseResultSubmissionDTO result) {
        // #region agent log
        AgentDebugLog.log("A", "StudentTaskService.completeExercise:entry", "completeExercise called",
                Map.of("studentId", studentId, "taskId", taskId, "exerciseTemplateId", exerciseTemplateId,
                        "hasBody", result != null));
        // #endregion
        ExerciseTemplate template;
        try {
            template = exerciseTemplateRepository.findByPhysicalId(exerciseTemplateId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Exercise template not found with ID: " + exerciseTemplateId));
            // #region agent log
            AgentDebugLog.log("A", "StudentTaskService.completeExercise:template", "template hydrated",
                    Map.of("exerciseTemplateId", exerciseTemplateId,
                            "exerciseType", template.getExerciseType() != null ? template.getExerciseType().name() : "null",
                            "difficulty", template.getExerciseDifficulty() != null ? template.getExerciseDifficulty().name() : "null"));
            // #endregion
        } catch (RuntimeException ex) {
            // #region agent log
            AgentDebugLog.log("A", "StudentTaskService.completeExercise:templateFail", "template load failed",
                    Map.of("exerciseTemplateId", exerciseTemplateId, "error", ex.getClass().getSimpleName(),
                            "message", String.valueOf(ex.getMessage())));
            // #endregion
            throw ex;
        }

        int reps = result != null ? Math.max(0, result.getReps()) : 0;
        double accuracy = result != null ? result.getAccuracy() : 0.0;
        long timeTaken = result != null ? Math.max(0L, result.getTimeTaken()) : 0L;
        int goalReps = result != null && result.getGoalReps() != null
                ? result.getGoalReps()
                : template.getGoalReps();
        String exerciseType = result != null && result.getExerciseType() != null
                ? result.getExerciseType()
                : (template.getExerciseType() != null ? template.getExerciseType().name() : null);

        double sessionCalories = result != null && result.getCaloriesBurned() != null && result.getCaloriesBurned() > 0
                ? result.getCaloriesBurned()
                : exerciseCalorieService.calculateCalories(studentId, exerciseType, reps);

        Task task = taskRepository.findByPhysicalId(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        // #region agent log
        AgentDebugLog.log("C", "StudentTaskService.completeExercise:taskMatch", "task vs path template",
                Map.of("taskId", taskId,
                        "taskExerciseTemplateId", String.valueOf(task.getExerciseTemplateId()),
                        "pathExerciseTemplateId", exerciseTemplateId,
                        "idsMatch", exerciseTemplateId != null && exerciseTemplateId.equals(task.getExerciseTemplateId())));
        // #endregion

        Score existingScore = scoreRepository
                .findExerciseScore(studentId, taskId, exerciseTemplateId)
                .orElse(null);

        if (existingScore == null) {
            existingScore = Score.builder()
                    .physicalId("SCORE-" + UUID.randomUUID().toString().toUpperCase())
                    .studentId(studentId)
                    .taskId(taskId)
                    .exerciseTemplateId(exerciseTemplateId)
                    .score(0)
                    .maxScore(100)
                    .attempts(0)
                    .isCompleted(false)
                    .timeTaken(0L)
                    .accuracy(0.0)
                    .reps(0)
                    .goalReps(goalReps)
                    .caloriesBurned(0.0)
                    .startedAt(OffsetDateTime.now())
                    .build();
        }

        validateAttempts(task, existingScore);

        int previousReps = existingScore.getReps() != null ? existingScore.getReps() : 0;
        double previousCalories =
                existingScore.getCaloriesBurned() != null ? existingScore.getCaloriesBurned() : 0.0;
        int previousScore = existingScore.getScore();

        // Accumulate reps and calories across repeated attempts
        existingScore.setReps(previousReps + reps);
        existingScore.setCaloriesBurned(
                Math.round((previousCalories + sessionCalories) * 10.0) / 10.0);
        existingScore.setGoalReps(goalReps);
        existingScore.setAccuracy(accuracy);
        existingScore.setTimeTaken(timeTaken);
        existingScore.setAttempts(existingScore.getAttempts() + 1);
        existingScore.setCompleted(true);
        existingScore.setCompletedAt(OffsetDateTime.now());
        // Session score from this attempt; keep best score across attempts for teacher progress
        int sessionScore = computeExerciseScore(reps, accuracy, goalReps, template.getGoalAccuracy());
        existingScore.setScore(Math.max(previousScore, sessionScore));
        existingScore.setMaxScore(100);

        Score savedScore = scoreRepository.save(existingScore);
        updateTaskCompletion(studentId, taskId, "exercise", true);
        // #region agent log
        AgentDebugLog.log("D", "StudentTaskService.completeExercise:saved", "score and task_completions updated",
                Map.of("scorePhysicalId", String.valueOf(savedScore.getPhysicalId()),
                        "attempts", savedScore.getAttempts(),
                        "scoreValue", savedScore.getScore(),
                        "reps", savedScore.getReps() != null ? savedScore.getReps() : 0,
                        "exerciseTemplateId", String.valueOf(savedScore.getExerciseTemplateId()),
                        "taskId", taskId));
        // #endregion

        String classroomId = result != null ? result.getClassroomId() : null;
        if (classroomId == null || classroomId.isBlank()) {
            classroomId = task.getClassroomPhysicalId();
        }

        exerciseProgressService.publishProgress(ExerciseProgressDTO.builder()
                .studentId(studentId)
                .classroomId(classroomId)
                .taskId(taskId)
                .exerciseTemplateId(exerciseTemplateId)
                .exerciseType(exerciseType)
                .reps(savedScore.getReps() != null ? savedScore.getReps() : reps)
                .goalReps(goalReps)
                .accuracy(accuracy)
                .timeTakenMs(timeTaken)
                .sessionCaloriesBurned(sessionCalories)
                .totalCaloriesBurned(savedScore.getCaloriesBurned())
                .score(savedScore.getScore())
                .completed(true)
                .timestamp(OffsetDateTime.now().toString())
                .build());

        return savedScore;
    }

    private int computeExerciseScore(int reps, double accuracy, int goalReps, int goalAccuracy) {
        if (goalReps <= 0) {
            return 0;
        }
        // Score = completed reps / target reps × 100 (capped at 100)
        return (int) Math.round(Math.min(1.0, (double) reps / goalReps) * 100.0);
    }

    @Transactional
    private void updateTaskCompletion(String studentId, String taskId, String componentType, boolean completed) {
        // Collapse duplicate (student, task) rows that break Optional single-result lookups
        // and roll back completeExercise transactions.
        java.util.List<TaskCompletion> rows =
                taskCompletionRepository.findAllByStudentIdAndTaskId(studentId, taskId);
        // #region agent log
        AgentDebugLog.log("B", "StudentTaskService.updateTaskCompletion", "resolve completion rows",
                Map.of("studentId", studentId, "taskId", taskId, "rowCount", rows.size(),
                        "componentType", componentType));
        // #endregion
        TaskCompletion completion;
        if (rows.isEmpty()) {
            completion = TaskCompletion.builder()
                .physicalId(provideUniquePhysicalId())
                .studentId(studentId)
                .taskId(taskId)
                .lessonCompleted(false)
                .quizCompleted(false)
                .exerciseCompleted(false)
                .isFullyCompleted(false)
                .submittedForReview(false)
                .startedAt(OffsetDateTime.now())
                .build();
        } else {
            completion = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                TaskCompletion dup = rows.get(i);
                if (dup.isLessonCompleted()) completion.setLessonCompleted(true);
                if (dup.isQuizCompleted()) completion.setQuizCompleted(true);
                if (dup.isExerciseCompleted()) completion.setExerciseCompleted(true);
                if (dup.isFullyCompleted()) completion.setFullyCompleted(true);
                if (dup.isSubmittedForReview()) completion.setSubmittedForReview(true);
                taskCompletionRepository.delete(dup);
            }
        }

        switch (componentType.toLowerCase()) {
            case "lesson":
                completion.setLessonCompleted(completed);
                break;
            case "quiz":
                completion.setQuizCompleted(completed);
                break;
            case "exercise":
                completion.setExerciseCompleted(completed);
                break;
        }

        // Get the task to check which components are required
        Task task = taskRepository.findByPhysicalId(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        boolean hasLesson = task.getLessonTemplateId() != null;
        boolean hasQuiz = task.getQuizTemplateId() != null;
        boolean hasExercise = task.getExerciseTemplateId() != null;

        // Check if all required components are completed
        boolean allRequiredCompleted = true;
        if (hasLesson && !completion.isLessonCompleted()) allRequiredCompleted = false;
        if (hasQuiz && !completion.isQuizCompleted()) allRequiredCompleted = false;
        if (hasExercise && !completion.isExerciseCompleted()) allRequiredCompleted = false;

        if (allRequiredCompleted) {
            completion.setFullyCompleted(true);
            completion.setCompletedAt(OffsetDateTime.now());
            completion.setTotalTimeTaken(
                completion.getCompletedAt().toEpochSecond() - completion.getStartedAt().toEpochSecond()
            );
        }

        taskCompletionRepository.save(completion);
    }

    private String provideUniquePhysicalId() {
        String year = String.valueOf(OffsetDateTime.now().getYear()).substring(2);
        Random random = new Random();
        String secondPart = String.format("%04d", random.nextInt(10000));
        String thirdPart = String.format("%03d", random.nextInt(1000));
        return String.format("TASK-%s-%s-%s", year, secondPart, thirdPart);
    }

    private StudentTaskResponseDTO buildStudentTaskResponse(Task task, String studentId) {
        Score score = null;
        if (task.getQuizTemplateId() != null) {
            score = scoreRepository.findQuizScore(studentId, task.getPhysicalId(), task.getQuizTemplateId())
                .orElse(null);
        }

        return StudentTaskResponseDTO.builder()
            .physicalId(task.getPhysicalId())
            .name(task.getName())
            .description(task.getDescription())
            .submissionDate(task.getSubmissionDate().toString())
            .closingDate(task.getClosingDate().toString())
            .imageUrl(task.getImageUrl())
            .classroomPhysicalId(task.getClassroomPhysicalId())
            .exerciseTemplateId(task.getExerciseTemplateId())
            .lessonTemplateId(task.getLessonTemplateId())
            .quizTemplateId(task.getQuizTemplateId())
            .maxAttempts(task.getMaxAttempts())
            .lessonTemplate(task.getLessonTemplateId() != null ? 
                buildLessonTemplateDTO(task.getLessonTemplateId()) : null)
            .quizTemplate(task.getQuizTemplateId() != null ? 
                buildQuizTemplateDTO(task.getQuizTemplateId()) : null)
            .exerciseTemplate(task.getExerciseTemplateId() != null ? 
                buildExerciseTemplateDTO(task.getExerciseTemplateId()) : null)
            .score(score != null ? buildScoreDTO(score) : null)
            .isCompleted(score != null && score.isCompleted())
            .isStarted(task.isStarted())
            .createdAt(task.getCreatedAt().toString())
            .updatedAt(task.getUpdatedAt().toString())
            .build();
    }

    private LessonTemplateResponseDTO buildLessonTemplateDTO(String lessonTemplateId) {
        return lessonTemplateRepository.findByPhysicalId(lessonTemplateId)
            .map(lessonTemplate -> LessonTemplateResponseDTO.builder()
                .physicalId(lessonTemplate.getPhysicalId())
                .title(lessonTemplate.getTitle())
                .description(lessonTemplate.getDescription())
                .content(lessonTemplate.getContent())
                .build())
            .orElse(null);
    }

    private QuizTemplateResponseDTO buildQuizTemplateDTO(String quizTemplateId) {
        return quizTemplateRepository.findByPhysicalId(quizTemplateId)
            .map(quizTemplate -> QuizTemplateResponseDTO.builder()
                .physicalId(quizTemplate.getPhysicalId())
                .title(quizTemplate.getTitle())
                .instruction(quizTemplate.getInstruction())
                .maxScore(quizTemplate.getMaxScore())
                .content(quizTemplate.getContent())
                .build())
            .orElse(null);
    }

    private ExerciseTemplateResponseDTO buildExerciseTemplateDTO(String exerciseTemplateId) {
        return exerciseTemplateRepository.findByPhysicalId(exerciseTemplateId)
            .map(exerciseTemplate -> ExerciseTemplateResponseDTO.builder()
                .physicalId(exerciseTemplate.getPhysicalId())
                .title(exerciseTemplate.getTitle())
                .description(exerciseTemplate.getDescription())
                .exerciseType(exerciseTemplate.getExerciseType())
                .exerciseDifficulty(exerciseTemplate.getExerciseDifficulty())
                .goalReps(exerciseTemplate.getGoalReps())
                .goalAccuracy(exerciseTemplate.getGoalAccuracy())
                .goalTime(exerciseTemplate.getGoalTime())
                .build())
            .orElse(null);
    }

    private ScoreDTO buildScoreDTO(Score score) {
        return ScoreDTO.builder()
            .physicalId(score.getPhysicalId())
            .score(score.getScore())
            .maxScore(score.getMaxScore())
            .attempts(score.getAttempts())
            .isCompleted(score.isCompleted())
            .teacherFeedback(score.getTeacherFeedback())
            .manualScore(score.getManualScore())
            .reps(score.getReps())
            .goalReps(score.getGoalReps())
            .accuracy(score.getAccuracy())
            .caloriesBurned(score.getCaloriesBurned())
            .build();
    }
} 
