package edu.cit.stathis.task.service;

import edu.cit.stathis.adaptive.coaching.CoachingInstructionCatalog;
import edu.cit.stathis.task.ExerciseCompletionPolicy;
import edu.cit.stathis.task.dto.TaskBodyDTO;
import edu.cit.stathis.task.dto.TaskExerciseInputDTO;
import edu.cit.stathis.task.dto.TaskExerciseProgressDTO;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import edu.cit.stathis.task.entity.Score;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskExercise;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.repository.TaskExerciseRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ordered exercise templates on a task. Goals stay on {@link ExerciseTemplate}.
 * {@code task.exerciseTemplateId} mirrors exercise 1 for older clients.
 */
@Service
public class TaskExerciseService {

  private final TaskExerciseRepository taskExerciseRepository;
  private final ExerciseTemplateRepository exerciseTemplateRepository;
  private final ExerciseDemonstrationRetention demonstrations;

  @Autowired
  public TaskExerciseService(
      TaskExerciseRepository taskExerciseRepository,
      ExerciseTemplateRepository exerciseTemplateRepository,
      @Autowired(required = false) ExerciseDemonstrationRetention demonstrations) {
    this.taskExerciseRepository = taskExerciseRepository;
    this.exerciseTemplateRepository = exerciseTemplateRepository;
    this.demonstrations = demonstrations;
  }

  public TaskExerciseService(
      TaskExerciseRepository taskExerciseRepository,
      ExerciseTemplateRepository exerciseTemplateRepository) {
    this(taskExerciseRepository, exerciseTemplateRepository, null);
  }

  public record AssignedExercise(String exerciseTemplateId, int sortOrder, ExerciseTemplate template) {}

  /** Child rows win. An older task with only {@code exerciseTemplateId} is one exercise. */
  @Transactional(readOnly = true)
  public List<AssignedExercise> assigned(Task task) {
    if (task == null || task.getPhysicalId() == null) {
      return List.of();
    }
    List<TaskExercise> rows =
        taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(task.getPhysicalId());
    if (!rows.isEmpty()) {
      return withTemplates(rows);
    }
    if (task.getExerciseTemplateId() != null && !task.getExerciseTemplateId().isBlank()) {
      ExerciseTemplate template =
          exerciseTemplateRepository.findByPhysicalId(task.getExerciseTemplateId()).orElse(null);
      return List.of(new AssignedExercise(task.getExerciseTemplateId(), 1, template));
    }
    return List.of();
  }

  @Transactional(readOnly = true)
  public void attachCatalog(List<Task> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    List<String> ids =
        tasks.stream().map(Task::getPhysicalId).filter(id -> id != null && !id.isBlank()).toList();
    Map<String, List<TaskExercise>> byTask = new HashMap<>();
    if (!ids.isEmpty()) {
      for (TaskExercise row : taskExerciseRepository.findByTaskIdIn(ids)) {
        byTask.computeIfAbsent(row.getTaskId(), key -> new ArrayList<>()).add(row);
      }
    }
    for (Task task : tasks) {
      List<TaskExercise> rows = new ArrayList<>(byTask.getOrDefault(task.getPhysicalId(), List.of()));
      rows.sort(Comparator.comparingInt(TaskExercise::getSortOrder));
      List<AssignedExercise> assigned;
      if (!rows.isEmpty()) {
        assigned = withTemplates(rows);
      } else if (task.getExerciseTemplateId() != null && !task.getExerciseTemplateId().isBlank()) {
        ExerciseTemplate template =
            exerciseTemplateRepository.findByPhysicalId(task.getExerciseTemplateId()).orElse(null);
        assigned = List.of(new AssignedExercise(task.getExerciseTemplateId(), 1, template));
      } else {
        assigned = List.of();
      }
      task.setExercises(toCatalog(assigned, demoTemplates(task.getPhysicalId())));
    }
  }

  @Transactional(readOnly = true)
  public void attachCatalog(Task task) {
    if (task != null) {
      attachCatalog(List.of(task));
    }
  }

  /**
   * {@code exercises} wins when the client sends the array, including an empty array.
   * Otherwise a lone {@code exerciseTemplateId} is a one-item list.
   */
  public List<String> resolveRequestedTemplateIds(TaskBodyDTO body) {
    if (body.getExercises() != null) {
      List<TaskExerciseInputDTO> inputs = new ArrayList<>(body.getExercises());
      inputs.sort(
          Comparator.comparingInt(
              (TaskExerciseInputDTO input) ->
                  input.getSortOrder() == null ? Integer.MAX_VALUE : input.getSortOrder()));
      List<String> ids = new ArrayList<>();
      for (TaskExerciseInputDTO input : inputs) {
        if (input.getExerciseTemplateId() == null || input.getExerciseTemplateId().isBlank()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exerciseTemplateId is required");
        }
        ids.add(input.getExerciseTemplateId().trim().toUpperCase(Locale.ROOT));
      }
      return ids;
    }
    if (body.getExerciseTemplateId() != null && !body.getExerciseTemplateId().isBlank()) {
      return List.of(body.getExerciseTemplateId().trim().toUpperCase(Locale.ROOT));
    }
    return List.of();
  }

  @Transactional
  public void applyRequestedExercises(Task task, TaskBodyDTO body) {
    List<String> requested = resolveRequestedTemplateIds(body);
    List<TaskExercise> persisted =
        task.getPhysicalId() == null
            ? List.of()
            : taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(task.getPhysicalId());
    List<String> current =
        persisted.isEmpty()
            ? (task.getExerciseTemplateId() == null || task.getExerciseTemplateId().isBlank()
                ? List.of()
                : List.of(task.getExerciseTemplateId()))
            : persisted.stream().map(TaskExercise::getExerciseTemplateId).toList();
    if (task.isStarted() && !current.equals(requested)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Exercise list cannot change after the task has started");
    }
    if (!persisted.isEmpty() && current.equals(requested)) {
      task.setExerciseTemplateId(requested.isEmpty() ? null : requested.get(0));
      return;
    }
    replace(task, requested);
  }

  @Transactional
  public void replace(Task task, List<String> orderedTemplateIds) {
    List<String> ids = orderedTemplateIds == null ? List.of() : orderedTemplateIds;
    validate(ids);
    List<TaskExercise> existing =
        taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(task.getPhysicalId());
    if (!existing.isEmpty()) {
      taskExerciseRepository.deleteAll(existing);
      taskExerciseRepository.flush();
    }
    int order = 1;
    for (String templateId : ids) {
      taskExerciseRepository.save(
          TaskExercise.builder()
              .physicalId("TEX-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT))
              .taskId(task.getPhysicalId())
              .exerciseTemplateId(templateId)
              .sortOrder(order++)
              .createdAt(OffsetDateTime.now())
              .build());
    }
    task.setExerciseTemplateId(ids.isEmpty() ? null : ids.get(0));
    if (demonstrations != null && task.getPhysicalId() != null) {
      demonstrations.retainOnly(task.getPhysicalId(), new LinkedHashSet<>(ids));
    }
  }

  public boolean belongsToTask(Task task, String exerciseTemplateId) {
    if (exerciseTemplateId == null || exerciseTemplateId.isBlank()) {
      return false;
    }
    String wanted = exerciseTemplateId.trim();
    return assigned(task).stream().anyMatch(row -> wanted.equals(row.exerciseTemplateId()));
  }

  public List<TaskExerciseProgressDTO> progressFor(Task task, List<Score> scores) {
    Map<String, Score> byTemplate = new LinkedHashMap<>();
    if (scores != null) {
      for (Score score : scores) {
        if (score.getExerciseTemplateId() != null && !score.getExerciseTemplateId().isBlank()) {
          byTemplate.putIfAbsent(score.getExerciseTemplateId(), score);
        }
      }
    }
    Set<String> withVideo = demoTemplates(task.getPhysicalId());
    List<TaskExerciseProgressDTO> rows = new ArrayList<>();
    for (AssignedExercise assigned : assigned(task)) {
      Score score = byTemplate.get(assigned.exerciseTemplateId());
      ExerciseTemplate template = assigned.template();
      rows.add(
          TaskExerciseProgressDTO.builder()
              .exerciseTemplateId(assigned.exerciseTemplateId())
              .sortOrder(assigned.sortOrder())
              .title(template != null ? template.getTitle() : null)
              .exerciseType(
                  template != null && template.getExerciseType() != null
                      ? template.getExerciseType().name()
                      : null)
              .goalReps(template != null ? template.getGoalReps() : score != null ? score.getGoalReps() : null)
              .goalAccuracy(template != null ? template.getGoalAccuracy() : null)
              .goalTime(template != null ? template.getGoalTime() : null)
              .completed(ExerciseCompletionPolicy.qualifies(score))
              .attempts(score != null ? score.getAttempts() : 0)
              .latestValidReps(score != null ? score.getReps() : 0)
              .score(score != null ? score.getScore() : null)
              .completionStatus(ExerciseCompletionPolicy.status(score))
              .demonstrationAvailable(withVideo.contains(assigned.exerciseTemplateId()))
              .build());
    }
    return rows;
  }

  public int qualifiedCount(List<TaskExerciseProgressDTO> rows) {
    int count = 0;
    for (TaskExerciseProgressDTO row : rows) {
      if (row.isCompleted()) {
        count++;
      }
    }
    return count;
  }

  private void validate(List<String> ids) {
    Map<String, Integer> seenIds = new HashMap<>();
    Map<String, String> seenTypes = new HashMap<>();
    for (String id : ids) {
      if (seenIds.put(id, 1) != null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Duplicate exercise template in this task");
      }
      ExerciseTemplate template =
          exerciseTemplateRepository
              .findByPhysicalId(id)
              .orElseThrow(() -> new EntityNotFoundException("Exercise template not found with ID: " + id));
      if (template.getExerciseType() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise template has no type");
      }
      String type = CoachingInstructionCatalog.normalizeExercise(template.getExerciseType().name());
      String previous = seenTypes.put(type, id);
      if (previous != null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Duplicate exercise type in this task");
      }
    }
  }

  private List<AssignedExercise> withTemplates(List<TaskExercise> rows) {
    List<AssignedExercise> assigned = new ArrayList<>();
    for (TaskExercise row : rows) {
      ExerciseTemplate template =
          exerciseTemplateRepository.findByPhysicalId(row.getExerciseTemplateId()).orElse(null);
      assigned.add(new AssignedExercise(row.getExerciseTemplateId(), row.getSortOrder(), template));
    }
    return assigned;
  }

  private Set<String> demoTemplates(String taskId) {
    if (demonstrations == null || taskId == null || taskId.isBlank()) {
      return Set.of();
    }
    return demonstrations.templateIds(taskId);
  }

  private List<TaskExerciseProgressDTO> toCatalog(
      List<AssignedExercise> assigned, Set<String> withVideo) {
    List<TaskExerciseProgressDTO> rows = new ArrayList<>();
    for (AssignedExercise item : assigned) {
      ExerciseTemplate template = item.template();
      rows.add(
          TaskExerciseProgressDTO.builder()
              .exerciseTemplateId(item.exerciseTemplateId())
              .sortOrder(item.sortOrder())
              .title(template != null ? template.getTitle() : null)
              .exerciseType(
                  template != null && template.getExerciseType() != null
                      ? template.getExerciseType().name()
                      : null)
              .goalReps(template != null ? template.getGoalReps() : null)
              .goalAccuracy(template != null ? template.getGoalAccuracy() : null)
              .goalTime(template != null ? template.getGoalTime() : null)
              .completed(false)
              .attempts(0)
              .latestValidReps(0)
              .completionStatus(ExerciseCompletionPolicy.NOT_STARTED)
              .demonstrationAvailable(withVideo.contains(item.exerciseTemplateId()))
              .build());
    }
    return rows;
  }
}
