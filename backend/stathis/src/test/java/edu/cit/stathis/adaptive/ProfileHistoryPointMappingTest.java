package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import edu.cit.stathis.adaptive.dto.ProfileHistoryPointDTO;
import edu.cit.stathis.adaptive.entity.LearningProfileHistory;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ProfileHistoryPointMappingTest {

  @Test
  void mapsSnapshotFieldsForTimelineCharts() {
    StudentLearningProfileService service = new StudentLearningProfileService();
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("learningRateEstimate", 0.22);
    snapshot.put("consistencyScore", 0.71);
    snapshot.put("meanMasteryLevel", 0.55);
    snapshot.put("totalInterventions", 10);
    snapshot.put("preferredModality", "VISUAL_HIGHLIGHT");

    LearningProfileHistory row =
        LearningProfileHistory.builder()
            .physicalId("LPH-1")
            .studentId("STUDENT-1")
            .createdAt(OffsetDateTime.parse("2026-07-01T12:00:00Z"))
            .snapshotJson(snapshot)
            .reason("response:FR-1")
            .build();

    ProfileHistoryPointDTO point = service.toHistoryPoint(row);
    assertEquals("LPH-1", point.getPhysicalId());
    assertEquals(0.22, point.getLearningRateEstimate(), 1e-6);
    assertEquals(0.71, point.getConsistencyScore(), 1e-6);
    assertEquals(0.55, point.getMeanMasteryLevel(), 1e-6);
    assertEquals(10, point.getTotalInterventions());
    assertEquals("VISUAL_HIGHLIGHT", point.getPreferredModality());
    assertNotNull(point.getCreatedAt());
  }
}
