package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.LearningProfileHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProfileHistoryRepository
    extends JpaRepository<LearningProfileHistory, UUID> {
  List<LearningProfileHistory> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
