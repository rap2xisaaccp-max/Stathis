package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import edu.cit.stathis.adaptive.dto.AdaptiveRecommendationDTO;
import edu.cit.stathis.adaptive.dto.AdaptiveRecommendationRequestDTO;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.enums.FormErrorCode;
import edu.cit.stathis.adaptive.enums.PolicySource;
import edu.cit.stathis.adaptive.service.AdaptivePolicyService;
import edu.cit.stathis.adaptive.service.StudentLearningProfileService;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptivePolicyServiceTest {

  @Mock private StudentLearningProfileService profileService;

  @Test
  void coldStartDefaultsWhenNoEvidence() {
    var profile =
        edu.cit.stathis.adaptive.entity.StudentLearningProfile.builder()
            .studentId("STUDENT-1")
            .physicalId("SLP-1")
            .preferredModality(FeedbackModality.VERBAL_TEXT)
            .modalityEffectivenessJson(new HashMap<>())
            .totalInterventions(0)
            .build();
    when(profileService.getOrCreate(anyString())).thenReturn(profile);

    AdaptivePolicyService policyService = new AdaptivePolicyService(profileService, new Random(2));
    AdaptiveRecommendationDTO result =
        policyService.recommend(
            "STUDENT-1",
            AdaptiveRecommendationRequestDTO.builder()
                .exerciseType("SQUAT")
                .errorCode(FormErrorCode.CHEST_UP)
                .build());

    assertNotNull(result.getModality());
    assertTrue(
        result.getPolicySource() == PolicySource.EXPLORE
            || result.getPolicySource() == PolicySource.DEFAULT
            || result.getPolicySource() == PolicySource.EXPLOIT);
    assertEquals("ADAPTIVE", result.getExperimentArm());
    assertNotNull(result.getPolicySource());
  }

  @Test
  void exploitPrefersHigherMeanDeltaModality() {
    Map<String, Object> visual = new HashMap<>();
    visual.put("meanDelta", 0.45);
    visual.put("bayesianMeanDelta", 0.4);
    visual.put("n", 8);
    visual.put("successRate", 0.8);
    visual.put("successes", 6);

    Map<String, Object> verbal = new HashMap<>();
    verbal.put("meanDelta", 0.05);
    verbal.put("bayesianMeanDelta", 0.04);
    verbal.put("n", 8);
    verbal.put("successRate", 0.2);
    verbal.put("successes", 2);

    Map<String, Object> effectiveness = new HashMap<>();
    effectiveness.put(FeedbackModality.VISUAL_HIGHLIGHT.name(), visual);
    effectiveness.put(FeedbackModality.VERBAL_TEXT.name(), verbal);

    var profile =
        edu.cit.stathis.adaptive.entity.StudentLearningProfile.builder()
            .studentId("STUDENT-1")
            .physicalId("SLP-1")
            .preferredModality(FeedbackModality.VISUAL_HIGHLIGHT)
            .modalityEffectivenessJson(effectiveness)
            .totalInterventions(16)
            .build();
    when(profileService.getOrCreate(anyString())).thenReturn(profile);

    // epsilon=0 via always-exploit Random that returns >= epsilon for first call...
    // Use AdaptivePolicyService with Random that always returns 0.99 (>0.2) so always exploit
    AdaptivePolicyService policyService =
        new AdaptivePolicyService(profileService, new Random() {
          @Override
          public double nextDouble() {
            return 0.99; // never explore
          }
        });

    int visualWins = 0;
    for (int i = 0; i < 40; i++) {
      AdaptiveRecommendationDTO result =
          policyService.recommend(
              "STUDENT-1",
              AdaptiveRecommendationRequestDTO.builder()
                  .exerciseType("SQUAT")
                  .errorCode(FormErrorCode.CHEST_UP)
                  .build());
      if (result.getModality() == FeedbackModality.VISUAL_HIGHLIGHT) {
        visualWins++;
      }
      assertEquals(PolicySource.EXPLOIT, result.getPolicySource());
    }
    assertEquals(40, visualWins);
  }
}
