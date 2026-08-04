package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.dto.ProfileHistoryPointDTO;
import edu.cit.stathis.adaptive.dto.StudentLearningProfileDTO;
import edu.cit.stathis.adaptive.entity.ExerciseMastery;
import edu.cit.stathis.adaptive.entity.FeedbackIntervention;
import edu.cit.stathis.adaptive.entity.FeedbackResponse;
import edu.cit.stathis.adaptive.entity.LearningProfileHistory;
import edu.cit.stathis.adaptive.entity.StudentLearningProfile;
import edu.cit.stathis.adaptive.enums.FeedbackModality;
import edu.cit.stathis.adaptive.repository.ExerciseMasteryRepository;
import edu.cit.stathis.adaptive.repository.LearningProfileHistoryRepository;
import edu.cit.stathis.adaptive.repository.StudentLearningProfileRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains evidence-based StudentLearningProfile using EWMA / Bayesian-shrunk deltas.
 */
@Service
public class StudentLearningProfileService {

  @Autowired private StudentLearningProfileRepository profileRepository;

  @Autowired private LearningProfileHistoryRepository historyRepository;

  @Autowired private ExerciseMasteryRepository masteryRepository;

  @Transactional
  public StudentLearningProfile getOrCreate(String studentId) {
    return profileRepository
        .findByStudentId(studentId)
        .orElseGet(
            () ->
                profileRepository.save(
                    StudentLearningProfile.builder()
                        .physicalId("SLP-" + UUID.randomUUID().toString().toUpperCase())
                        .studentId(studentId)
                        .preferredModality(FeedbackModality.VERBAL_TEXT)
                        .modalityEffectivenessJson(new HashMap<>())
                        .learningRateEstimate(0.0)
                        .consistencyScore(0.5)
                        .fatigueSensitivity(0.0)
                        .totalInterventions(0)
                        .totalSuccessfulInterventions(0)
                        .build()));
  }

  public StudentLearningProfileDTO toDto(StudentLearningProfile profile) {
    return StudentLearningProfileDTO.builder()
        .physicalId(profile.getPhysicalId())
        .studentId(profile.getStudentId())
        .preferredModality(profile.getPreferredModality())
        .modalityEffectivenessJson(profile.getModalityEffectivenessJson())
        .preferredModalityByExercise(profile.getPreferredModalityByExerciseJson())
        .learningRateEstimate(profile.getLearningRateEstimate())
        .consistencyScore(profile.getConsistencyScore())
        .fatigueSensitivity(profile.getFatigueSensitivity())
        .totalInterventions(profile.getTotalInterventions())
        .totalSuccessfulInterventions(profile.getTotalSuccessfulInterventions())
        .updatedAt(
            profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : null)
        .build();
  }

  @Transactional
  public StudentLearningProfile applyResponse(
      FeedbackIntervention intervention, FeedbackResponse response) {
    StudentLearningProfile profile = getOrCreate(intervention.getStudentId());

    Map<String, Object> effectiveness =
        profile.getModalityEffectivenessJson() != null
            ? new HashMap<>(profile.getModalityEffectivenessJson())
            : new HashMap<>();

    String modalityKey = intervention.getModality().name();
    // Normalize exercise type for internal aggregated composite keys, but also keep raw composite key for
    // backward compatibility with existing data and tests.
    String normalizedExercise = edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise(intervention.getExerciseType());
    String rawExercise = intervention.getExerciseType() != null ? intervention.getExerciseType() : normalizedExercise;

    String compositeKeyNormalized =
        ProfileEffectivenessMath.compositeKey(
            normalizedExercise,
            intervention.getErrorCode().name(),
            modalityKey);
    String compositeKeyRaw =
        ProfileEffectivenessMath.compositeKey(
            rawExercise,
            intervention.getErrorCode().name(),
            modalityKey);

    ProfileEffectivenessMath.updateBucket(
        effectiveness, modalityKey, response.getDelta(), response.isSuccess());
    ProfileEffectivenessMath.updateBucket(
        effectiveness, compositeKeyNormalized, response.getDelta(), response.isSuccess());
    if (!compositeKeyNormalized.equals(compositeKeyRaw)) {
      ProfileEffectivenessMath.updateBucket(
          effectiveness, compositeKeyRaw, response.getDelta(), response.isSuccess());
    }

    profile.setModalityEffectivenessJson(effectiveness);

    int total =
        (profile.getTotalInterventions() == null ? 0 : profile.getTotalInterventions()) + 1;
    int successes =
        (profile.getTotalSuccessfulInterventions() == null
                ? 0
                : profile.getTotalSuccessfulInterventions())
            + (response.isSuccess() ? 1 : 0);
    profile.setTotalInterventions(total);
    profile.setTotalSuccessfulInterventions(successes);

    double priorRate =
        profile.getLearningRateEstimate() == null ? 0.0 : profile.getLearningRateEstimate();
    profile.setLearningRateEstimate(
        ProfileEffectivenessMath.ewma(priorRate, response.getDelta()));

    double successRate = total == 0 ? 0.5 : (double) successes / total;
    profile.setConsistencyScore(successRate);

    profile.setPreferredModality(
        ProfileEffectivenessMath.derivePreferredModality(effectiveness));

    // Compute per-exercise preferred modalities but preserve existing LEARNED rows unless
    // sufficient new evidence supports a change (hysteresis to avoid oscillation).
    Map<String, Object> newByExercise = ProfileEffectivenessMath.derivePreferredByExercise(effectiveness);
    Map<String, Object> oldByExercise = profile.getPreferredModalityByExerciseJson() != null
        ? new HashMap<>(profile.getPreferredModalityByExerciseJson())
        : new HashMap<>();

    final int REASSIGN_MIN_ADDITIONAL_N = 3; // require at least this many extra samples to switch a learned modality
    for (Map.Entry<String, Object> e : new HashMap<>(newByExercise).entrySet()) {
      String ex = e.getKey();
      Object newRowObj = e.getValue();
      if (!(newRowObj instanceof Map)) continue;
      @SuppressWarnings("unchecked")
      Map<String, Object> newRow = (Map<String, Object>) newRowObj;
      String newMod = String.valueOf(newRow.getOrDefault("modality", "VERBAL_TEXT"));
      int newN = ((Number) newRow.getOrDefault("n", 0)).intValue();

      Object oldRowObj = oldByExercise.get(ex);
      if (oldRowObj instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> oldRow = (Map<String, Object>) oldRowObj;
        String oldSource = String.valueOf(oldRow.getOrDefault("source", "DEFAULT"));
        String oldMod = String.valueOf(oldRow.getOrDefault("modality", "VERBAL_TEXT"));
        int oldN = ((Number) oldRow.getOrDefault("n", 0)).intValue();
        if ("LEARNED".equals(oldSource) && !oldMod.equals(newMod)) {
          // Not enough new evidence to switch learned modality?
          if (newN < oldN + REASSIGN_MIN_ADDITIONAL_N) {
            // Preserve old learned row
            newByExercise.put(ex, oldRow);
          }
        }
      }
    }

    // Ensure preferredByExercise keys align with the raw exercise string provided in the intervention
    String rawExerciseKey = intervention.getExerciseType() != null ? intervention.getExerciseType() : normalizedExercise;
    if (!rawExerciseKey.equals(normalizedExercise) && newByExercise.containsKey(normalizedExercise)) {
      if (!newByExercise.containsKey(rawExerciseKey)) {
        newByExercise.put(rawExerciseKey, newByExercise.get(normalizedExercise));
      }
      newByExercise.remove(normalizedExercise);
    }

    profile.setPreferredModalityByExerciseJson(newByExercise);

    StudentLearningProfile saved = profileRepository.save(profile);
    maybeSnapshot(saved, "response:" + response.getPhysicalId());
    return saved;
  }

  private void maybeSnapshot(StudentLearningProfile profile, String reason) {
    int total = profile.getTotalInterventions() == null ? 0 : profile.getTotalInterventions();
    if (total > 0 && total % 5 == 0) {
      List<ExerciseMastery> masteries = masteryRepository.findByStudentId(profile.getStudentId());
      double meanMastery =
          masteries.isEmpty()
              ? 0.0
              : masteries.stream().mapToDouble(ExerciseMastery::getMasteryLevel).average().orElse(0.0);
      Map<String, Object> masteryByExercise = new HashMap<>();
      for (ExerciseMastery mastery : masteries) {
        masteryByExercise.put(mastery.getExerciseType(), mastery.getMasteryLevel());
      }

      Map<String, Object> snapshot = new HashMap<>();
      snapshot.put("preferredModality", profile.getPreferredModality());
      snapshot.put("modalityEffectivenessJson", profile.getModalityEffectivenessJson());
      snapshot.put(
          "preferredModalityByExerciseJson", profile.getPreferredModalityByExerciseJson());
      snapshot.put("learningRateEstimate", profile.getLearningRateEstimate());
      snapshot.put("consistencyScore", profile.getConsistencyScore());
      snapshot.put("totalInterventions", profile.getTotalInterventions());
      snapshot.put("totalSuccessfulInterventions", profile.getTotalSuccessfulInterventions());
      snapshot.put("meanMasteryLevel", meanMastery);
      snapshot.put("masteryByExercise", masteryByExercise);
      historyRepository.save(
          LearningProfileHistory.builder()
              .physicalId("LPH-" + UUID.randomUUID().toString().toUpperCase())
              .studentId(profile.getStudentId())
              .snapshotJson(snapshot)
              .reason(reason)
              .build());
    }
  }

  public List<ProfileHistoryPointDTO> listHistory(String studentId) {
    List<LearningProfileHistory> rows =
        historyRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    List<ProfileHistoryPointDTO> points = new ArrayList<>();
    for (LearningProfileHistory row : rows) {
      points.add(toHistoryPoint(row));
    }
    Collections.reverse(points); // chronological for charts
    return points;
  }

  public ProfileHistoryPointDTO toHistoryPoint(LearningProfileHistory row) {
    Map<String, Object> snap =
        row.getSnapshotJson() != null ? row.getSnapshotJson() : Map.of();
    return ProfileHistoryPointDTO.builder()
        .physicalId(row.getPhysicalId())
        .createdAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null)
        .reason(row.getReason())
        .learningRateEstimate(asDouble(snap.get("learningRateEstimate")))
        .consistencyScore(asDouble(snap.get("consistencyScore")))
        .meanMasteryLevel(asDouble(snap.get("meanMasteryLevel")))
        .totalInterventions(asInteger(snap.get("totalInterventions")))
        .preferredModality(
            snap.get("preferredModality") != null
                ? String.valueOf(snap.get("preferredModality"))
                : null)
        .build();
  }

  private static Double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }

  private static Integer asInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return null;
  }

  public static boolean isSuccessfulDelta(double delta) {
    return ProfileEffectivenessMath.isSuccessfulDelta(delta);
  }
}
