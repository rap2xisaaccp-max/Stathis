package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackInterventionRepository extends JpaRepository<FeedbackIntervention, UUID> {
  Optional<FeedbackIntervention> findByPhysicalId(String physicalId);

  List<FeedbackIntervention> findByStudentIdOrderByDeliveredAtDesc(String studentId);

  List<FeedbackIntervention> findByStudentIdAndExerciseTypeAndErrorCode(
      String studentId, String exerciseType, FormErrorCode errorCode);

  List<FeedbackIntervention> findBySessionId(String sessionId);

  long countByStudentId(String studentId);
}
