package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.StudentLearningProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentLearningProfileRepository
    extends JpaRepository<StudentLearningProfile, UUID> {
  Optional<StudentLearningProfile> findByStudentId(String studentId);

  Optional<StudentLearningProfile> findByPhysicalId(String physicalId);
}
