package edu.cit.stathis.task.repository;

import edu.cit.stathis.task.entity.ExerciseDemonstration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseDemonstrationRepository extends JpaRepository<ExerciseDemonstration, UUID> {

  Optional<ExerciseDemonstration> findByTaskIdAndExerciseTemplateId(
      String taskId, String exerciseTemplateId);

  List<ExerciseDemonstration> findByTaskId(String taskId);
}
