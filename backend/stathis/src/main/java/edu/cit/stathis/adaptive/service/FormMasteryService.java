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

    Map<String, List<TimedAttempt>> attemptsByExercise = new LinkedHashMap<>();
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
      OffsetDateTime at =
          attempt.getCompletedAt() != null ? attempt.getCompletedAt() : attempt.getCreatedAt();
      attemptsByExercise
          .computeIfAbsent(exerciseType, key -> new ArrayList<>())
          .add(
              new TimedAttempt(
                  at,
                  new FormMasteryMath.AttemptInput(
                      attempt.getAccuracy(),
                      attempt.getReps() == null ? 0 : attempt.getReps(),
                      attempt.getGoalReps(),
                      attempt.getAttemptedReps())));
    }

    List<FormMasteryDTO> rows = new ArrayList<>();
    for (Map.Entry<String, List<TimedAttempt>> entry : attemptsByExercise.entrySet()) {
      List<TimedAttempt> ordered = new ArrayList<>(entry.getValue());
      ordered.sort(
          Comparator.comparing(
              TimedAttempt::at, Comparator.nullsLast(Comparator.naturalOrder())));
      List<FormMasteryMath.AttemptInput> inputs = new ArrayList<>();
      OffsetDateTime lastAt = null;
      for (TimedAttempt timed : ordered) {
        inputs.add(timed.input());
        if (timed.at() != null) {
          lastAt = timed.at();
        }
      }
      FormMasteryMath.Evaluation evaluation = FormMasteryMath.evaluate(inputs);
      if (evaluation == null) {
        continue;
      }
      rows.add(
          FormMasteryDTO.builder()
              .studentId(studentId)
              .exerciseType(entry.getKey())
              .formMasteryLevel(evaluation.level())
              .formMasteryPercent(evaluation.level() * 100.0)
              .eligibleAttemptCount(evaluation.qualifyingCount())
              .lastAttemptAt(lastAt != null ? lastAt.toString() : null)
              .state(evaluation.state().name())
              .build());
    }
    rows.sort(
        Comparator.comparing(
                FormMasteryDTO::getLastAttemptAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(FormMasteryDTO::getExerciseType));
    return rows;
  }

  private record TimedAttempt(OffsetDateTime at, FormMasteryMath.AttemptInput input) {}
}
