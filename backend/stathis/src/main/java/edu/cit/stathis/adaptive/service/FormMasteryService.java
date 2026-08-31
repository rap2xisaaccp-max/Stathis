package edu.cit.stathis.adaptive.service;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.adaptive.dto.FormMasteryDTO;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import edu.cit.stathis.task.entity.ScoreAttempt;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.repository.ScoreAttemptRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Dedicated Form Mastery from completed classroom exercise {@code score_attempt} rows.
 * Independent of {@code exercise_mastery.mastery_level}, coaching interventions, TTS,
 * highlights, evidence snapshots, and Policy B cycles.
 */
@Service
public class FormMasteryService {

  @Autowired private ScoreAttemptRepository scoreAttemptRepository;
  @Autowired private ExerciseTemplateRepository exerciseTemplateRepository;

  public List<FormMasteryDTO> listForStudent(String studentId) {
    if (studentId == null || studentId.isBlank()) {
      return List.of();
    }
    List<ScoreAttempt> attempts =
        scoreAttemptRepository.findByStudentIdAndExerciseTemplateIdIsNotNull(studentId);
    if (attempts == null || attempts.isEmpty()) {
      return List.of();
    }

    Set<String> templateIds =
        attempts.stream()
            .map(ScoreAttempt::getExerciseTemplateId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
    Map<String, ExerciseTemplate> templates = new HashMap<>();
    if (!templateIds.isEmpty()) {
      for (ExerciseTemplate template : exerciseTemplateRepository.findByPhysicalIdIn(templateIds)) {
        if (template.getPhysicalId() != null) {
          templates.put(template.getPhysicalId(), template);
        }
      }
    }

    Map<String, List<Double>> accuraciesByExercise = new LinkedHashMap<>();
    Map<String, OffsetDateTime> lastAttemptByExercise = new HashMap<>();
    for (ScoreAttempt attempt : attempts) {
      if (!FormMasteryMath.isEligibleClassroomExerciseAttempt(
          attempt.getExerciseTemplateId(),
          attempt.getQuizTemplateId(),
          attempt.getReps(),
          attempt.getAccuracy())) {
        continue;
      }
      ExerciseTemplate template = templates.get(attempt.getExerciseTemplateId());
      if (template == null || template.getExerciseType() == null) {
        continue;
      }
      String exerciseType =
          CoachingInstructionCatalog.normalizeExercise(template.getExerciseType().name());
      if ("UNKNOWN".equals(exerciseType)) {
        continue;
      }
      accuraciesByExercise
          .computeIfAbsent(exerciseType, key -> new ArrayList<>())
          .add(attempt.getAccuracy());
      OffsetDateTime at =
          attempt.getCompletedAt() != null ? attempt.getCompletedAt() : attempt.getCreatedAt();
      if (at != null) {
        lastAttemptByExercise.merge(exerciseType, at, FormMasteryService::laterTimestamp);
      }
    }

    List<FormMasteryDTO> rows = new ArrayList<>();
    for (Map.Entry<String, List<Double>> entry : accuraciesByExercise.entrySet()) {
      Double level = FormMasteryMath.meanFormMasteryLevel(entry.getValue());
      if (level == null) {
        continue;
      }
      OffsetDateTime lastAt = lastAttemptByExercise.get(entry.getKey());
      rows.add(
          FormMasteryDTO.builder()
              .studentId(studentId)
              .exerciseType(entry.getKey())
              .formMasteryLevel(level)
              .formMasteryPercent(level * 100.0)
              .eligibleAttemptCount(entry.getValue().size())
              .lastAttemptAt(lastAt != null ? lastAt.toString() : null)
              .build());
    }
    rows.sort(
        Comparator.comparing(
                FormMasteryDTO::getLastAttemptAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(FormMasteryDTO::getExerciseType));
    return rows;
  }

  private static OffsetDateTime laterTimestamp(OffsetDateTime left, OffsetDateTime right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return right.isAfter(left) ? right : left;
  }
}
