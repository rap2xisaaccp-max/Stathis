package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.AdaptiveArmSessionRollup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveArmSessionRollupRepository
    extends JpaRepository<AdaptiveArmSessionRollup, UUID> {
  Optional<AdaptiveArmSessionRollup> findByStudentIdAndSessionIdAndBaseArm(
      String studentId, String sessionId, String baseArm);

  List<AdaptiveArmSessionRollup> findByStudentId(String studentId);

  List<AdaptiveArmSessionRollup> findByClassroomId(String classroomId);
}
