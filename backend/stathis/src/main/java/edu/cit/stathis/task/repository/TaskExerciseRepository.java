package edu.cit.stathis.task.repository;

import edu.cit.stathis.task.entity.TaskExercise;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskExerciseRepository extends JpaRepository<TaskExercise, UUID> {

  List<TaskExercise> findByTaskIdOrderBySortOrderAsc(String taskId);

  List<TaskExercise> findByTaskIdIn(Collection<String> taskIds);

  void deleteByTaskId(String taskId);
}
