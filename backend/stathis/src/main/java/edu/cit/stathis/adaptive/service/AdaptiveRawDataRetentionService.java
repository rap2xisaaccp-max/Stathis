package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.repository.AdaptiveArmSessionRollupRepository;
import edu.cit.stathis.adaptive.repository.FeedbackInterventionRepository;
import edu.cit.stathis.adaptive.repository.FeedbackResponseRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional raw FI/FR retention. Disabled by default — never deletes without explicit enable.
 *
 * <p>When enabled, only deletes raw rows older than {@code apsle.retention.raw-days} for sessions
 * that already have an {@code adaptive_arm_session_rollup} (so RCT variance stats survive).
 *
 * <p>Default {@code dry-run=true} logs candidates without deleting.
 */
@Service
public class AdaptiveRawDataRetentionService {

  private static final Logger log = LoggerFactory.getLogger(AdaptiveRawDataRetentionService.class);

  @Value("${apsle.retention.enabled:false}")
  private boolean enabled;

  @Value("${apsle.retention.raw-days:180}")
  private int rawDays;

  @Value("${apsle.retention.dry-run:true}")
  private boolean dryRun;

  @Autowired private FeedbackInterventionRepository interventionRepository;
  @Autowired private FeedbackResponseRepository responseRepository;
  @Autowired private AdaptiveArmSessionRollupRepository rollupRepository;

  /** Daily at 03:15 server time when retention is enabled. */
  @Scheduled(cron = "${apsle.retention.cron:0 15 3 * * *}")
  @Transactional
  public void purgeExpiredRawEvents() {
    if (!enabled) {
      return;
    }
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(Math.max(1, rawDays));
    Set<String> rolledKeys = new HashSet<>();
    rollupRepository
        .findAll()
        .forEach(r -> rolledKeys.add(r.getStudentId() + "|" + r.getSessionId()));

    List<FeedbackIntervention> oldInterventions = new ArrayList<>();
    for (FeedbackIntervention intervention : interventionRepository.findByCreatedAtBefore(cutoff)) {
      String key = intervention.getStudentId() + "|" + intervention.getSessionId();
      if (rolledKeys.contains(key)) {
        oldInterventions.add(intervention);
      }
    }

    Set<String> interventionPhysicalIds = new HashSet<>();
    oldInterventions.forEach(i -> interventionPhysicalIds.add(i.getPhysicalId()));

    List<FeedbackResponse> oldResponses = new ArrayList<>();
    for (FeedbackResponse response : responseRepository.findByCreatedAtBefore(cutoff)) {
      if (interventionPhysicalIds.contains(response.getInterventionPhysicalId())) {
        oldResponses.add(response);
      }
    }

    log.info(
        "APSLE retention cutoff={} dryRun={} interventions={} responses={} rolledSessions={}",
        cutoff,
        dryRun,
        oldInterventions.size(),
        oldResponses.size(),
        rolledKeys.size());

    if (dryRun || oldInterventions.isEmpty()) {
      return;
    }

    responseRepository.deleteAll(oldResponses);
    interventionRepository.deleteAll(oldInterventions);
    log.warn(
        "APSLE retention deleted raw events responses={} interventions={}",
        oldResponses.size(),
        oldInterventions.size());
  }
}
