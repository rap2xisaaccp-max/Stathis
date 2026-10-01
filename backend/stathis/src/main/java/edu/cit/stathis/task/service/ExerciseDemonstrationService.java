package edu.cit.stathis.task.service;

import edu.cit.stathis.auth.service.PhysicalIdService;
import edu.cit.stathis.classroom.entity.Classroom;
import edu.cit.stathis.classroom.repository.ClassroomRepository;
import edu.cit.stathis.classroom.service.ClassroomService;
import edu.cit.stathis.task.dto.ExerciseDemonstrationDTO;
import edu.cit.stathis.task.entity.ExerciseDemonstration;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskExercise;
import edu.cit.stathis.task.repository.ExerciseDemonstrationRepository;
import edu.cit.stathis.task.repository.TaskExerciseRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExerciseDemonstrationService implements ExerciseDemonstrationRetention {

  /** Fixed demonstration cap. Not read from configuration. */
  public static final long DEFAULT_MAX_BYTES = 50L * 1024L * 1024L;

  private static final Logger log = LoggerFactory.getLogger(ExerciseDemonstrationService.class);

  private final ExerciseDemonstrationRepository demonstrationRepository;
  private final ExerciseDemonstrationStorage storage;
  private final TaskRepository taskRepository;
  private final TaskExerciseRepository taskExerciseRepository;
  private final ClassroomRepository classroomRepository;
  private final ClassroomService classroomService;
  private final PhysicalIdService physicalIdService;
  private final long maxBytes;

  @Autowired
  public ExerciseDemonstrationService(
      ExerciseDemonstrationRepository demonstrationRepository,
      ExerciseDemonstrationStorage storage,
      TaskRepository taskRepository,
      TaskExerciseRepository taskExerciseRepository,
      ClassroomRepository classroomRepository,
      ClassroomService classroomService,
      PhysicalIdService physicalIdService) {
    this(
        demonstrationRepository,
        storage,
        taskRepository,
        taskExerciseRepository,
        classroomRepository,
        classroomService,
        physicalIdService,
        DEFAULT_MAX_BYTES);
  }

  /** Test hook for a smaller cap. The Spring constructor always uses {@link #DEFAULT_MAX_BYTES}. */
  public ExerciseDemonstrationService(
      ExerciseDemonstrationRepository demonstrationRepository,
      ExerciseDemonstrationStorage storage,
      TaskRepository taskRepository,
      TaskExerciseRepository taskExerciseRepository,
      ClassroomRepository classroomRepository,
      ClassroomService classroomService,
      PhysicalIdService physicalIdService,
      long maxBytes) {
    this.demonstrationRepository = demonstrationRepository;
    this.storage = storage;
    this.taskRepository = taskRepository;
    this.taskExerciseRepository = taskExerciseRepository;
    this.classroomRepository = classroomRepository;
    this.classroomService = classroomService;
    this.physicalIdService = physicalIdService;
    this.maxBytes = maxBytes;
  }

  @Transactional(readOnly = true)
  public ExerciseDemonstrationDTO metadata(String taskId, String exerciseTemplateId) {
    Task task = requireTask(taskId);
    String templateId = requireTemplateOnTask(task, exerciseTemplateId);
    requireCanView(task);
    return demonstrationRepository
        .findByTaskIdAndExerciseTemplateId(task.getPhysicalId(), templateId)
        .map(this::toDto)
        .orElseGet(() -> ExerciseDemonstrationDTO.absent(task.getPhysicalId(), templateId));
  }

  @Transactional(readOnly = true)
  public InputStream content(String taskId, String exerciseTemplateId) {
    ExerciseDemonstration row = requireRow(taskId, exerciseTemplateId);
    return storage
        .open(row.getStorageKey())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Demonstration video file is missing"));
  }

  @Transactional(readOnly = true)
  public DemonstrationBytes contentSlice(String taskId, String exerciseTemplateId, long start, long endInclusive) {
    ExerciseDemonstration row = requireRow(taskId, exerciseTemplateId);
    long total = row.getByteSize();
    if (start < 0 || endInclusive < start || start >= total) {
      throw new ResponseStatusException(
          HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Demonstration range is not satisfiable");
    }
    long end = Math.min(endInclusive, total - 1);
    InputStream body =
        storage
            .openSlice(row.getStorageKey(), start, end)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Demonstration video file is missing"));
    return new DemonstrationBytes(body, start, end, total);
  }

  public record DemonstrationBytes(InputStream body, long start, long endInclusive, long total) {}

  private ExerciseDemonstration requireRow(String taskId, String exerciseTemplateId) {
    Task task = requireTask(taskId);
    String templateId = requireTemplateOnTask(task, exerciseTemplateId);
    requireCanView(task);
    return demonstrationRepository
        .findByTaskIdAndExerciseTemplateId(task.getPhysicalId(), templateId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No demonstration video for this exercise"));
  }

  /**
   * Not transactional. The Supabase upload sits between the authorization reads and the metadata
   * insert. Production uses the transaction pooler, which will not keep that connection idle for
   * the whole video. Repository calls still use their own short transactions. Evidence uploads are
   * structured the same way.
   */
  public ExerciseDemonstrationDTO upload(
      String taskId,
      String exerciseTemplateId,
      String originalFilename,
      String contentType,
      InputStream body) {
    Task task = requireTask(taskId);
    String templateId = requireTemplateOnTask(task, exerciseTemplateId);
    requireOwner(task);
    byte[] header = readHeader(body);
    if (header.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demonstration video is empty");
    }
    String type = resolveType(contentType, header);
    String caller = physicalIdService.getCurrentUserPhysicalId();
    String physicalId = "DEMO-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    String storageKey = storageKey(task.getPhysicalId(), templateId, physicalId, type);
    InputStream payload = new SequenceInputStream(new ByteArrayInputStream(header), body);
    ExerciseDemonstrationStorage.StoredDemonstration stored =
        storage.put(storageKey, payload, maxBytes);
    try {
      ExerciseDemonstration existing =
          demonstrationRepository
              .findByTaskIdAndExerciseTemplateId(task.getPhysicalId(), templateId)
              .orElse(null);
      String previousKey = existing == null ? null : existing.getStorageKey();
      ExerciseDemonstration row = existing == null ? new ExerciseDemonstration() : existing;
      if (existing == null) {
        row.setId(UUID.randomUUID());
        row.setPhysicalId(physicalId);
        row.setTaskId(task.getPhysicalId());
        row.setExerciseTemplateId(templateId);
      }
      row.setUploadedBy(caller);
      row.setOriginalFilename(displayName(originalFilename));
      row.setStorageKey(stored.storageKey());
      row.setContentType(type);
      row.setByteSize(stored.byteSize());
      row.setCreatedAt(OffsetDateTime.now());
      ExerciseDemonstration saved = demonstrationRepository.save(row);
      if (previousKey != null && !previousKey.equals(saved.getStorageKey())) {
        storage.delete(previousKey);
      }
      return toDto(saved);
    } catch (RuntimeException ex) {
      storage.delete(stored.storageKey());
      if (ex instanceof ResponseStatusException) {
        throw ex;
      }
      if (ex instanceof org.springframework.dao.DataAccessException) {
        Throwable cause = ex.getCause();
        log.warn(
            "Demonstration metadata save failed taskId={} exerciseTemplateId={} exception={} cause={}",
            task.getPhysicalId(),
            templateId,
            ex.getClass().getSimpleName(),
            cause == null ? "none" : cause.getClass().getSimpleName());
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Demonstration metadata could not be saved", ex);
      }
      throw ex;
    }
  }

  @Transactional
  public void delete(String taskId, String exerciseTemplateId) {
    Task task = requireTask(taskId);
    String templateId = requireTemplateOnTask(task, exerciseTemplateId);
    requireOwner(task);
    demonstrationRepository
        .findByTaskIdAndExerciseTemplateId(task.getPhysicalId(), templateId)
        .ifPresent(this::deleteRow);
  }

  @Override
  @Transactional
  public void retainOnly(String taskId, Set<String> exerciseTemplateIds) {
    if (taskId == null || taskId.isBlank()) {
      return;
    }
    Set<String> keep = exerciseTemplateIds == null ? Set.of() : exerciseTemplateIds;
    for (ExerciseDemonstration row : demonstrationRepository.findByTaskId(taskId)) {
      if (!keep.contains(row.getExerciseTemplateId())) {
        deleteRow(row);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> templateIds(String taskId) {
    if (taskId == null || taskId.isBlank()) {
      return Set.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    for (ExerciseDemonstration row : demonstrationRepository.findByTaskId(taskId)) {
      ids.add(row.getExerciseTemplateId());
    }
    return ids;
  }

  private void deleteRow(ExerciseDemonstration row) {
    storage.delete(row.getStorageKey());
    demonstrationRepository.delete(row);
  }

  private Task requireTask(String taskId) {
    if (taskId == null || taskId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task id is required");
    }
    return taskRepository
        .findByPhysicalId(taskId.trim())
        .orElseThrow(() -> new EntityNotFoundException("Task not found with physical ID: " + taskId));
  }

  private String requireTemplateOnTask(Task task, String exerciseTemplateId) {
    if (exerciseTemplateId == null || exerciseTemplateId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise template id is required");
    }
    String wanted = exerciseTemplateId.trim();
    List<TaskExercise> rows =
        taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(task.getPhysicalId());
    if (!rows.isEmpty()) {
      for (TaskExercise row : rows) {
        if (row.getExerciseTemplateId() != null
            && row.getExerciseTemplateId().equalsIgnoreCase(wanted)) {
          return row.getExerciseTemplateId();
        }
      }
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Exercise template is not assigned to this task");
    }
    if (task.getExerciseTemplateId() != null
        && task.getExerciseTemplateId().equalsIgnoreCase(wanted)) {
      return task.getExerciseTemplateId();
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Exercise template is not assigned to this task");
  }

  private void requireOwner(Task task) {
    Classroom classroom = requireClassroom(task);
    String caller = physicalIdService.getCurrentUserPhysicalId();
    boolean owner = caller != null && caller.equals(classroom.getTeacherId());
    if (!owner) {
      log.warn(
          "Demonstration upload denied endpoint=POST taskId={} classroomId={} callerPhysicalId={} ownerMatch=false",
          task.getPhysicalId(),
          classroom.getPhysicalId(),
          caller);
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this classroom");
    }
  }

  private void requireCanView(Task task) {
    Classroom classroom = requireClassroom(task);
    String caller = physicalIdService.getCurrentUserPhysicalId();
    if (caller != null && caller.equals(classroom.getTeacherId())) {
      return;
    }
    if (caller == null
        || !classroomService.isUserEnrolledInClassroom(caller, classroom.getPhysicalId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this classroom");
    }
  }

  private Classroom requireClassroom(Task task) {
    if (task.getClassroomPhysicalId() == null || task.getClassroomPhysicalId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task has no classroom");
    }
    return classroomRepository
        .findByPhysicalId(task.getClassroomPhysicalId())
        .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
  }

  private ExerciseDemonstrationDTO toDto(ExerciseDemonstration row) {
    return ExerciseDemonstrationDTO.builder()
        .available(true)
        .physicalId(row.getPhysicalId())
        .taskId(row.getTaskId())
        .exerciseTemplateId(row.getExerciseTemplateId())
        .originalFilename(row.getOriginalFilename())
        .contentType(row.getContentType())
        .byteSize(row.getByteSize())
        .createdAt(row.getCreatedAt())
        .build();
  }

  static String resolveType(String contentType, byte[] header) {
    String declared = declaredVideoType(contentType);
    if (matches("video/mp4", header)) {
      if (declared != null && !"video/mp4".equals(declared)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Demonstration video must be an MP4 or WebM file");
      }
      return "video/mp4";
    }
    if (matches("video/webm", header)) {
      if (declared != null && !"video/webm".equals(declared)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Demonstration video must be an MP4 or WebM file");
      }
      return "video/webm";
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Demonstration video must be an MP4 or WebM file");
  }

  /**
   * A browser may send a blank type or {@code application/octet-stream} for an MP4. Those are
   * resolved from the file bytes. An explicit non-video type is still rejected.
   */
  static String declaredVideoType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    String type = contentType.trim().toLowerCase(Locale.ROOT);
    int separator = type.indexOf(';');
    if (separator >= 0) {
      type = type.substring(0, separator).trim();
    }
    if (type.isEmpty() || "application/octet-stream".equals(type)) {
      return null;
    }
    if (!type.equals("video/mp4") && !type.equals("video/webm")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Demonstration video must be an MP4 or WebM file");
    }
    return type;
  }

  static boolean matches(String contentType, byte[] header) {
    if ("video/mp4".equals(contentType)) {
      return header.length >= 12
          && header[4] == 'f'
          && header[5] == 't'
          && header[6] == 'y'
          && header[7] == 'p';
    }
    if ("video/webm".equals(contentType)) {
      return header.length >= 4
          && (header[0] & 0xFF) == 0x1A
          && (header[1] & 0xFF) == 0x45
          && (header[2] & 0xFF) == 0xDF
          && (header[3] & 0xFF) == 0xA3;
    }
    return false;
  }

  private static byte[] readHeader(InputStream body) {
    try {
      return body.readNBytes(12);
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read demonstration video");
    }
  }

  private static String storageKey(String taskId, String templateId, String physicalId, String type) {
    String ext = "video/webm".equals(type) ? "webm" : "mp4";
    return "demos/" + safe(taskId) + "/" + safe(templateId) + "/" + safe(physicalId) + "." + ext;
  }

  private static String safe(String raw) {
    return raw == null ? "unknown" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static String displayName(String originalFilename) {
    String name = originalFilename == null ? "video" : originalFilename.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    name = name.trim();
    if (name.isEmpty()) {
      name = "video";
    }
    return name.length() > 255 ? name.substring(name.length() - 255) : name;
  }
}
