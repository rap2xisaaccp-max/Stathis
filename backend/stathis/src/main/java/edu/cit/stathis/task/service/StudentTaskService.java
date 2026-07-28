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
import edu.cit.stathis.task.dto.ExerciseResultSubmissionDTO;
import edu.cit.stathis.task.repository.LessonTemplateRepository;
import edu.cit.stathis.task.repository.QuizTemplateRepository;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.dto.QuizSubmissionDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import edu.cit.stathis.classroom.service.ClassroomService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
        TaskCompletion completion = taskCompletionRepository.findByStudentIdAndTaskId(studentId, taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task completion not found for student ID: " + studentId + " and task ID: " + taskId));
        Score score = task.getQuizTemplateId() != null ? 
            scoreRepository.findQuizScore(studentId, taskId, task.getQuizTemplateId()).orElse(null) : null;

        if (completion == null) {
            return TaskProgressDTO.builder()
                .lessonCompleted(false)
                .exerciseCompleted(false)
                .quizCompleted(false)
                .quizScore(0)
                .maxQuizScore(0)
                .quizAttempts(0)
                .totalTimeTaken(0L)
                .repsPerformed(0)
                .build();
        }

        return TaskProgressDTO.builder()
            .lessonCompleted(completion.isLessonCompleted())
            .exerciseCompleted(completion.isExerciseCompleted())
            .quizCompleted(completion.isQuizCompleted())
            .quizScore(score != null ? score.getScore() : 0)
            .maxQuizScore(score != null ? score.getMaxScore() : 0)
            .quizAttempts(score != null ? score.getAttempts() : 0)
            .totalTimeTaken(completion.getTotalTimeTaken())
            .repsPerformed(completion.getRepsPerformed() != null ? completion.getRepsPerformed() : 0)
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum quiz attempts reached for this task");
        }
    }

    @Transactional
    public void completeLesson(String studentId, String taskId, String lessonTemplateId) {
        updateTaskCompletion(studentId, taskId, "lesson", true);
    }

    @Transactional
    public void completeExercise(String studentId, String taskId, String exerciseTemplateId) {
        updateTaskCompletion(studentId, taskId, "exercise", true);
    }

    @Transactional
    public Score submitExerciseResult(String studentId, String taskId, String exerciseTemplateId, ExerciseResultSubmissionDTO submission) {
        var exerciseTemplate = exerciseTemplateRepository.findByPhysicalId(exerciseTemplateId)
            .orElseThrow(() -> new EntityNotFoundException("Exercise template not found with ID: " + exerciseTemplateId));

        Score existingScore = scoreRepository.findExerciseScore(studentId, taskId, exerciseTemplateId)
            .orElse(null);

        if (existingScore == null) {
            existingScore = new Score();
            existingScore.setPhysicalId(provideUniquePhysicalId());
            existingScore.setStudentId(studentId);
            existingScore.setTaskId(taskId);
            existingScore.setExerciseTemplateId(exerciseTemplateId);
            existingScore.setAttempts(0);
            existingScore.setStartedAt(OffsetDateTime.now());
        }

        int computedScore = calculateExerciseScore(
            submission.getReps(),
            submission.getAccuracy(),
            submission.getTimeTaken(),
            exerciseTemplate.getGoalReps(),
            exerciseTemplate.getGoalAccuracy(),
            exerciseTemplate.getGoalTime()
        );

        existingScore.setScore(computedScore);
        existingScore.setMaxScore(100);
        existingScore.setAttempts(existingScore.getAttempts() + 1);
        existingScore.setAccuracy(clamp(submission.getAccuracy(), 0.0, 100.0));
        existingScore.setTimeTaken(submission.getTimeTaken());
        existingScore.setCompleted(true);
        existingScore.setCompletedAt(OffsetDateTime.now());

        Score savedScore = scoreRepository.save(existingScore);
        TaskCompletion completion = updateTaskCompletion(studentId, taskId, "exercise", true);
        completion.setRepsPerformed(Math.max(0, submission.getReps()));
        taskCompletionRepository.save(completion);

        return savedScore;
    }

    private int calculateExerciseScore(int actualReps, double actualAccuracy, long timeTakenMillis, int goalReps, int goalAccuracy, int goalTimeSeconds) {
        double repsScore = goalReps > 0
            ? Math.min(40.0, (Math.max(0, actualReps) / (double) goalReps) * 40.0)
            : 40.0;

        double accuracyScore = goalAccuracy > 0
            ? Math.min(40.0, (clamp(actualAccuracy, 0.0, 100.0) / (double) goalAccuracy) * 40.0)
            : 40.0;

        long actualTimeSeconds = Math.max(0L, timeTakenMillis) / 1000L;
        double timeScore;
        if (goalTimeSeconds <= 0 || actualTimeSeconds <= goalTimeSeconds) {
            timeScore = 20.0;
        } else {
            timeScore = Math.max(0.0, (goalTimeSeconds / (double) actualTimeSeconds) * 20.0);
        }

        return (int) Math.round(Math.min(100.0, repsScore + accuracyScore + timeScore));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Transactional
    private TaskCompletion updateTaskCompletion(String studentId, String taskId, String componentType, boolean completed) {
        TaskCompletion completion = taskCompletionRepository.findByStudentIdAndTaskId(studentId, taskId)
            .orElse(null);
        if (completion == null) {
            completion = TaskCompletion.builder()
                .physicalId(provideUniquePhysicalId())
                .studentId(studentId)
                .taskId(taskId)
                .repsPerformed(0)
                .startedAt(OffsetDateTime.now())
                .build();
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

        return taskCompletionRepository.save(completion);
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
        } else if (task.getExerciseTemplateId() != null) {
            score = scoreRepository.findExerciseScore(studentId, task.getPhysicalId(), task.getExerciseTemplateId())
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
            .build();
    }
} 
