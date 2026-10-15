package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.ExerciseMastery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseMasteryRepository extends JpaRepository<ExerciseMastery, UUID> {
  Optional<ExerciseMastery> findByStudentIdAndExerciseType(String studentId, String exerciseType);

  List<ExerciseMastery> findByStudentId(String studentId);

  Optional<ExerciseMastery> findByPhysicalId(String physicalId);
}
