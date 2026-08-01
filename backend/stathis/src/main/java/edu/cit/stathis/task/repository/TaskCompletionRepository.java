package edu.cit.stathis.task.repository;

import edu.cit.stathis.task.entity.TaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, String> {

    /**
     * Prefer {@link #findAllByStudentIdAndTaskId} — duplicate rows exist in prod and
     * a single-result Optional query throws NonUniqueResultException.
     */
    @Query("SELECT tc FROM TaskCompletion tc WHERE tc.studentId = :studentId AND tc.taskId = :taskId ORDER BY tc.startedAt ASC")
    List<TaskCompletion> findAllByStudentIdAndTaskId(
            @Param("studentId") String studentId,
            @Param("taskId") String taskId);

    default Optional<TaskCompletion> findByStudentIdAndTaskId(String studentId, String taskId) {
        List<TaskCompletion> rows = findAllByStudentIdAndTaskId(studentId, taskId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }
    
    List<TaskCompletion> findByStudentId(String studentId);
    
    List<TaskCompletion> findByTaskId(String taskId);
    
    @Query("SELECT tc FROM TaskCompletion tc WHERE tc.taskId = :taskId AND tc.isFullyCompleted = true")
    List<TaskCompletion> findCompletedByTaskId(@Param("taskId") String taskId);
    
    @Query("SELECT tc FROM TaskCompletion tc WHERE tc.taskId = :taskId AND tc.submittedForReview = true")
    List<TaskCompletion> findSubmittedForReviewByTaskId(@Param("taskId") String taskId);
    
    @Query("SELECT COUNT(tc) FROM TaskCompletion tc WHERE tc.taskId = :taskId AND tc.isFullyCompleted = true")
    long countCompletedByTaskId(@Param("taskId") String taskId);
    
    @Query("SELECT COUNT(tc) FROM TaskCompletion tc WHERE tc.taskId = :taskId AND tc.submittedForReview = true")
    long countSubmittedForReviewByTaskId(@Param("taskId") String taskId);
    
    boolean existsByStudentIdAndTaskId(String studentId, String taskId);
}
