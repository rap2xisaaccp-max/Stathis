package edu.cit.stathis.task.service;

import edu.cit.stathis.task.dto.TaskProgressDTO;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskCompletion;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskCompletionService {
    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskRepository taskRepository;

    private void requireTaskStarted(String taskId) {
        Task task = taskRepository.findByPhysicalId(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found with ID: " + taskId));
        if (!task.isStarted()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Task has not been started by the teacher yet");
        }
        if (!task.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task is no longer active");
        }
    }

    @Transactional
    public TaskCompletion createTaskCompletion(String studentId, String taskId) {
        requireTaskStarted(taskId);
        // Idempotent: duplicate creates were producing multiple rows per student/task,
        // which broke findByStudentIdAndTaskId and rolled back completeExercise.
        java.util.List<TaskCompletion> existing =
                taskCompletionRepository.findAllByStudentIdAndTaskId(studentId, taskId);
        if (!existing.isEmpty()) {
            TaskCompletion keeper = existing.get(0);
            for (int i = 1; i < existing.size(); i++) {
                taskCompletionRepository.delete(existing.get(i));
            }
            return keeper;
        }
        TaskCompletion taskCompletion = TaskCompletion.builder()
                .physicalId(generatePhysicalId())
                .studentId(studentId)
                .taskId(taskId)
                .lessonCompleted(false)
                .quizCompleted(false)
                .exerciseCompleted(false)
                .isFullyCompleted(false)
                .startedAt(OffsetDateTime.now())
                .submittedForReview(false)
                .build();
        return taskCompletionRepository.save(taskCompletion);
    }

    @Transactional
    public TaskCompletion updateTaskProgress(String studentId, String taskId, TaskProgressDTO progressDTO) {
        requireTaskStarted(taskId);
        TaskCompletion taskCompletion = taskCompletionRepository.findByStudentIdAndTaskId(studentId, taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task completion not found for student: " + studentId + " and task: " + taskId));
        taskCompletion.setLessonCompleted(progressDTO.isLessonCompleted());
        taskCompletion.setExerciseCompleted(progressDTO.isExerciseCompleted());
        taskCompletion.setQuizCompleted(progressDTO.isQuizCompleted());
        taskCompletion.setTotalTimeTaken(progressDTO.getTotalTimeTaken());
        if (progressDTO.getCompletedAt() != null) {
            taskCompletion.setCompletedAt(OffsetDateTime.parse(progressDTO.getCompletedAt()));
        }
        boolean isFullyCompleted = taskCompletion.isLessonCompleted() && 
                                 taskCompletion.isExerciseCompleted() && 
                                 taskCompletion.isQuizCompleted();
        taskCompletion.setFullyCompleted(isFullyCompleted);
        return taskCompletionRepository.save(taskCompletion);
    }

    @Transactional(readOnly = true)
    public Optional<TaskCompletion> getTaskCompletion(String studentId, String taskId) {
        return taskCompletionRepository.findByStudentIdAndTaskId(studentId, taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskCompletion> getTaskCompletionsByStudent(String studentId) {
        return taskCompletionRepository.findByStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public List<TaskCompletion> getTaskCompletionsByTask(String taskId) {
        return taskCompletionRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskCompletion> getCompletedTasks(String taskId) {
        return taskCompletionRepository.findCompletedByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskCompletion> getSubmittedForReviewTasks(String taskId) {
        return taskCompletionRepository.findSubmittedForReviewByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public long countCompletedTasks(String taskId) {
        return taskCompletionRepository.countCompletedByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public long countSubmittedForReviewTasks(String taskId) {
        return taskCompletionRepository.countSubmittedForReviewByTaskId(taskId);
    }

    @Transactional
    public void submitForReview(String studentId, String taskId) {
        requireTaskStarted(taskId);
        TaskCompletion taskCompletion = taskCompletionRepository.findByStudentIdAndTaskId(studentId, taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task completion not found for student: " + studentId + " and task: " + taskId));
        taskCompletion.setSubmittedForReview(true);
        taskCompletion.setSubmittedAt(OffsetDateTime.now());
        taskCompletionRepository.save(taskCompletion);
    }

    @Transactional(readOnly = true)
    public boolean existsByStudentIdAndTaskId(String studentId, String taskId) {
        return taskCompletionRepository.existsByStudentIdAndTaskId(studentId, taskId);
    }

    private String generatePhysicalId() {
        return "COMPLETION-" + UUID.randomUUID().toString().toUpperCase();
    }
} 
