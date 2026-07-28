package edu.cit.stathis.task.service;

import edu.cit.stathis.task.dto.ExerciseResultSubmissionDTO;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskCompletion;
import edu.cit.stathis.task.repository.TaskCompletionRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext
public class StudentTaskServiceTest {

    @Autowired
    private StudentTaskService studentTaskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @Test
    @Transactional
    public void completeExercise_shouldSaveRepsAndTimeAndMarkCompleted() {
        // Arrange
        String studentId = "STU-TEST-1";
        Task task = Task.builder()
                .physicalId("TASK-TEST-EX-1")
                .name("Test Exercise Task")
                .exerciseTemplateId("EX-1")
                .submissionDate(OffsetDateTime.now())
                .closingDate(OffsetDateTime.now().plusDays(1))
                .isActive(true)
                .isStarted(true)
                .maxAttempts(0)
                .build();
        taskRepository.save(task);

        ExerciseResultSubmissionDTO submission = new ExerciseResultSubmissionDTO(10, 0.85, 45_000L);

        // Act
        studentTaskService.completeExercise(studentId, task.getPhysicalId(), task.getExerciseTemplateId(), submission);

        // Assert
        TaskCompletion completion = taskCompletionRepository.findByStudentIdAndTaskId(studentId, task.getPhysicalId()).orElse(null);
        assertNotNull(completion, "TaskCompletion should exist");
        assertTrue(completion.isExerciseCompleted(), "Exercise should be marked completed");
        assertEquals(10, completion.getRepsPerformed().intValue(), "Reps performed should be saved");
        assertNotNull(completion.getCompletedAt(), "completedAt should be set when fully completed");
        assertTrue(completion.isFullyCompleted(), "Completion should be fully completed when exercise is the only component");
        assertNotNull(completion.getTotalTimeTaken(), "totalTimeTaken should be set");
        assertEquals(45L, completion.getTotalTimeTaken().longValue(), "totalTimeTaken should be 45 seconds (from submission timeTaken)");
    }
}
