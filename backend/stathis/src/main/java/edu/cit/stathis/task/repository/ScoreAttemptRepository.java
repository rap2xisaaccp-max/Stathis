package edu.cit.stathis.task.repository;

import edu.cit.stathis.task.entity.ScoreAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoreAttemptRepository extends JpaRepository<ScoreAttempt, UUID> {

    List<ScoreAttempt> findByStudentIdAndTaskIdOrderByAttemptNumberAsc(String studentId, String taskId);

    List<ScoreAttempt> findByScorePhysicalIdOrderByAttemptNumberAsc(String scorePhysicalId);

    long countByScorePhysicalId(String scorePhysicalId);
}
