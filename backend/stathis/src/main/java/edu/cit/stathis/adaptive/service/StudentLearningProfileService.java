package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.dto.ProfileHistoryPointDTO;
import edu.cit.stathis.adaptive.dto.StudentLearningProfileDTO;
import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
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
  private static final List<String> CANONICAL_EXERCISES =
      List.of("PUSH_UP", "SQUATS", "STATIC_LUNGES", "GLUTE_BRIDGE", "LYING_LEG_RAISES");

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
                        .preferredModalityByExerciseJson(defaultPreferredByExercise())
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
        .preferredModalityByExercise(
            ensurePreferredByExerciseDefaults(profile.getPreferredModalityByExerciseJson()))
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
    // Normalize exercise type so effectiveness attribution does not mix equivalent exercise labels.
    String normalizedExercise = edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog.normalizeExercise(intervention.getExerciseType());

    String compositeKeyNormalized =
        ProfileEffectivenessMath.compositeKey(
            normalizedExercise,
            intervention.getErrorCode().name(),
            modalityKey);

    ProfileEffectivenessMath.updateBucket(
        effectiveness, modalityKey, response.getDelta(), response.isSuccess());
    ProfileEffectivenessMath.updateBucket(
        effectiveness, compositeKeyNormalized, response.getDelta(), response.isSuccess());

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

    // Compute per-exercise preferred modalities and preserve stable learned choices unless
    // enough additional confident evidence supports a switch.
    Map<String, Object> newByExercise =
        ensurePreferredByExerciseDefaults(
            ProfileEffectivenessMath.derivePreferredByExercise(effectiveness));
    Map<String, Object> oldByExercise = profile.getPreferredModalityByExerciseJson() != null
        ? new HashMap<>(profile.getPreferredModalityByExerciseJson())
        : new HashMap<>();

    final int REASSIGN_MIN_ADDITIONAL_N = 3;
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
        String newSource = String.valueOf(newRow.getOrDefault("source", "DEFAULT"));
        if ("LEARNED".equals(oldSource) && !oldMod.equals(newMod)) {
          // Keep a learned choice unless the challenger is also learned and has extra evidence.
          if (!"LEARNED".equals(newSource) || newN < oldN + REASSIGN_MIN_ADDITIONAL_N) {
            newByExercise.put(ex, oldRow);
          }
        }
      }
    }

    profile.setPreferredModalityByExerciseJson(ensurePreferredByExerciseDefaults(newByExercise));

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

  private static Map<String, Object> defaultPreferredByExercise() {
    Map<String, Object> out = new HashMap<>();
    for (String exercise : CANONICAL_EXERCISES) {
      out.put(exercise, defaultPreferredRow());
    }
    return out;
  }

  private static Map<String, Object> defaultPreferredRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("modality", FeedbackModality.VERBAL_TEXT.name());
    row.put("n", 0);
    row.put("meanDelta", 0.0);
    row.put("confidence", 0.0);
    row.put("source", "DEFAULT");
    return row;
  }

  private static Map<String, Object> ensurePreferredByExerciseDefaults(Map<String, Object> raw) {
    Map<String, Object> merged = defaultPreferredByExercise();
    if (raw == null || raw.isEmpty()) {
      return merged;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (!(entry.getValue() instanceof Map<?, ?> row)) {
        continue;
      }
      String normalized = CoachingInstructionCatalog.normalizeExercise(entry.getKey());
      @SuppressWarnings("unchecked")
      Map<String, Object> typedRow = new HashMap<>((Map<String, Object>) row);
      typedRow.put("modality", String.valueOf(typedRow.getOrDefault("modality", FeedbackModality.VERBAL_TEXT.name())));
      typedRow.put("n", ((Number) typedRow.getOrDefault("n", 0)).intValue());
      typedRow.put("meanDelta", ((Number) typedRow.getOrDefault("meanDelta", 0.0)).doubleValue());
      typedRow.put("confidence", ((Number) typedRow.getOrDefault("confidence", 0.0)).doubleValue());
      typedRow.put("source", String.valueOf(typedRow.getOrDefault("source", "DEFAULT")));
      merged.put(normalized, typedRow);
    }
    return merged;
  }
}
