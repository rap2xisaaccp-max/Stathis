package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.entity.FormCorrectionEvidence;
import edu.cit.stathis.adaptive.repository.FormCorrectionEvidenceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional evidence retention. Disabled by default — never deletes without explicit enable.
 *
 * <p>Default {@code dry-run=true} logs candidates without deleting. Deleting a row also deletes
 * the stored JPEG.
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

  @Autowired private FormCorrectionEvidenceRepository evidenceRepository;
  @Autowired private FormCorrectionStorage storage;

  @Scheduled(cron = "${apsle.retention.cron:0 15 3 * * *}")
  @Transactional
  public void purgeExpiredRawEvents() {
    if (!enabled) {
      return;
    }
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(Math.max(1, rawDays));
    List<FormCorrectionEvidence> expired = evidenceRepository.findByCapturedAtBefore(cutoff);
    log.info(
        "APSLE evidence retention cutoff={} dryRun={} evidence={}",
        cutoff,
        dryRun,
        expired.size());
    if (dryRun || expired.isEmpty()) {
      return;
    }
    for (FormCorrectionEvidence row : expired) {
      storage.delete(row.getStorageKey());
    }
    evidenceRepository.deleteAll(expired);
    log.warn("APSLE retention deleted evidence rows={}", expired.size());
  }
}
