package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackResponseRepository extends JpaRepository<FeedbackResponse, UUID> {
  Optional<FeedbackResponse> findByPhysicalId(String physicalId);

  Optional<FeedbackResponse> findByInterventionPhysicalId(String interventionPhysicalId);

  List<FeedbackResponse> findByStudentIdOrderByCreatedAtDesc(String studentId);

  long countByStudentIdAndSuccessTrue(String studentId);

  List<FeedbackResponse> findByCreatedAtBefore(java.time.OffsetDateTime cutoff);
}
