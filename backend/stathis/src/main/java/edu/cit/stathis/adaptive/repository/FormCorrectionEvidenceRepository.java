package edu.cit.stathis.adaptive.repository;

import edu.cit.stathis.adaptive.entity.FormCorrectionEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormCorrectionEvidenceRepository
    extends JpaRepository<FormCorrectionEvidence, UUID> {
  Optional<FormCorrectionEvidence> findByPhysicalId(String physicalId);

  Optional<FormCorrectionEvidence> findByInterventionPhysicalId(String interventionPhysicalId);

  List<FormCorrectionEvidence> findByStudentIdOrderByCapturedAtDesc(String studentId);

  List<FormCorrectionEvidence> findByCapturedAtBefore(java.time.OffsetDateTime cutoff);
}
