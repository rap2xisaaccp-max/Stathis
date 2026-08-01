package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.entity.AdaptiveArmSessionRollup;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.repository.AdaptiveArmSessionRollupRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains variance-preserving session/arm rollups used for RCT metrics after raw-event
 * retention windows expire.
 */
@Service
public class AdaptiveArmRollupService {

  @Autowired private AdaptiveArmSessionRollupRepository rollupRepository;

  @Transactional
  public AdaptiveArmSessionRollup recordResponse(
      FeedbackIntervention intervention, FeedbackResponse response) {
    String baseArm = RctEvaluationMetrics.baseArm(intervention.getExperimentArm());
    AdaptiveArmSessionRollup rollup =
        rollupRepository
            .findByStudentIdAndSessionIdAndBaseArm(
                intervention.getStudentId(), intervention.getSessionId(), baseArm)
            .orElseGet(
                () ->
                    AdaptiveArmSessionRollup.builder()
                        .physicalId("AASR-" + UUID.randomUUID().toString().toUpperCase())
                        .studentId(intervention.getStudentId())
                        .sessionId(intervention.getSessionId())
                        .classroomId(intervention.getClassroomId())
                        .baseArm(baseArm)
                        .build());

    rollup.setNInterventions(rollup.getNInterventions() + 1);
    rollup.setNResponses(rollup.getNResponses() + 1);
    if (response.isSuccess()) {
      rollup.setSuccesses(rollup.getSuccesses() + 1);
    }
    double delta = response.getDelta();
    rollup.setSumDelta(rollup.getSumDelta() + delta);
    rollup.setSumDeltaSq(rollup.getSumDeltaSq() + (delta * delta));
    if (rollup.getClassroomId() == null && intervention.getClassroomId() != null) {
      rollup.setClassroomId(intervention.getClassroomId());
    }
    return rollupRepository.save(rollup);
  }

  /** Sample variance from stored sum / sum-of-squares (Bessel's correction). */
  public static double sampleVariance(int n, double sum, double sumSq) {
    if (n < 2) {
      return 0.0;
    }
    double mean = sum / n;
    return (sumSq - n * mean * mean) / (n - 1);
  }

  public static double mean(int n, double sum) {
    return n <= 0 ? 0.0 : sum / n;
  }
}
