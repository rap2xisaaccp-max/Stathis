package edu.cit.stathis.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.stathis.task.entity.ExerciseTemplate;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.Collection;

@Repository
public interface ExerciseTemplateRepository extends JpaRepository<ExerciseTemplate, UUID> {
    ExerciseTemplate findByTitle(String title);
    Optional<ExerciseTemplate> findByPhysicalId(String physicalId);
    List<ExerciseTemplate> findByPhysicalIdIn(Collection<String> physicalIds);
    List<ExerciseTemplate> findByTeacherPhysicalId(String teacherPhysicalId);
    void deleteByPhysicalId(String physicalId);
}
