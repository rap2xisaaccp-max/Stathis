package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.stathis.auth.service.PhysicalIdService;
import edu.cit.stathis.classroom.entity.Classroom;
import edu.cit.stathis.classroom.repository.ClassroomRepository;
import edu.cit.stathis.classroom.service.ClassroomService;
import edu.cit.stathis.task.dto.ExerciseDemonstrationDTO;
import edu.cit.stathis.task.entity.ExerciseDemonstration;
import edu.cit.stathis.task.entity.ExerciseTemplate;
import edu.cit.stathis.task.entity.Task;
import edu.cit.stathis.task.entity.TaskExercise;
import edu.cit.stathis.task.enums.ExerciseType;
import edu.cit.stathis.task.repository.ExerciseDemonstrationRepository;
import edu.cit.stathis.task.repository.ExerciseTemplateRepository;
import edu.cit.stathis.task.repository.TaskExerciseRepository;
import edu.cit.stathis.task.repository.TaskRepository;
import edu.cit.stathis.task.service.ExerciseDemonstrationService;
import edu.cit.stathis.task.service.ExerciseDemonstrationStorage;
import edu.cit.stathis.task.service.LocalExerciseDemonstrationStorage;
import edu.cit.stathis.task.service.TaskExerciseService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExerciseDemonstrationServiceTest {

  private static final String TASK = "TASK-A";
  private static final String OTHER_TASK = "TASK-B";
  private static final String PUSH = "EXERCISE-PUSH";
  private static final String SQUAT = "EXERCISE-SQUAT";
  private static final String TEACHER = "TCH00000001";
  private static final String OTHER = "TCH00000002";
  private static final String STUDENT = "STU00000001";
  private static final String STRANGER = "STU00000002";

  @TempDir Path tempDir;

  @Mock private ExerciseDemonstrationRepository demonstrationRepository;
  @Mock private TaskRepository taskRepository;
  @Mock private TaskExerciseRepository taskExerciseRepository;
  @Mock private ExerciseTemplateRepository exerciseTemplateRepository;
  @Mock private ClassroomRepository classroomRepository;
  @Mock private ClassroomService classroomService;
  @Mock private PhysicalIdService physicalIdService;

  private final List<ExerciseDemonstration> rows = new ArrayList<>();
  private LocalExerciseDemonstrationStorage storage;
  private ExerciseDemonstrationService service;
  private String caller = TEACHER;

  @BeforeEach
  void setUp() {
    storage = new LocalExerciseDemonstrationStorage(tempDir.toString());
    service =
        new ExerciseDemonstrationService(
            demonstrationRepository,
            storage,
            taskRepository,
            taskExerciseRepository,
            classroomRepository,
            classroomService,
            physicalIdService);
    lenient().when(demonstrationRepository.save(any()))
        .thenAnswer(
            invocation -> {
              ExerciseDemonstration saved = invocation.getArgument(0);
              rows.removeIf(
                  row ->
                      row.getTaskId().equals(saved.getTaskId())
                          && row.getExerciseTemplateId().equals(saved.getExerciseTemplateId()));
              rows.add(saved);
              return saved;
            });
    lenient()
        .doAnswer(
            invocation -> {
              rows.remove(invocation.getArgument(0));
              return null;
            })
        .when(demonstrationRepository)
        .delete(any());
    lenient()
        .when(demonstrationRepository.findByTaskIdAndExerciseTemplateId(any(), any()))
        .thenAnswer(
            invocation ->
                rows.stream()
                    .filter(row -> row.getTaskId().equals(invocation.getArgument(0)))
                    .filter(row -> row.getExerciseTemplateId().equals(invocation.getArgument(1)))
                    .findFirst());
    lenient().when(demonstrationRepository.findByTaskId(any()))
        .thenAnswer(
            invocation ->
                rows.stream()
                    .filter(row -> row.getTaskId().equals(invocation.getArgument(0)))
                    .toList());
    lenient().when(physicalIdService.getCurrentUserPhysicalId()).thenAnswer(invocation -> caller);
    lenient().when(taskRepository.findByPhysicalId(TASK)).thenReturn(Optional.of(task(TASK)));
    lenient().when(taskRepository.findByPhysicalId(OTHER_TASK)).thenReturn(Optional.of(task(OTHER_TASK)));
    lenient()
        .when(taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(any()))
        .thenAnswer(invocation -> exercises(invocation.getArgument(0)));
    Classroom classroom = new Classroom();
    classroom.setPhysicalId("CLASS-1");
    classroom.setTeacherId(TEACHER);
    lenient().when(classroomRepository.findByPhysicalId("CLASS-1")).thenReturn(Optional.of(classroom));
    lenient()
        .when(classroomService.isUserEnrolledInClassroom(any(), any()))
        .thenAnswer(
            invocation -> {
              String user = invocation.getArgument(0);
              return TEACHER.equals(user) || STUDENT.equals(user);
            });
  }

  @Test
  void assignedExerciseWithoutAVideoIsUnavailable() {
    ExerciseDemonstrationDTO meta = service.metadata(TASK, PUSH);
    assertFalse(meta.isAvailable());
    assertEquals(TASK, meta.getTaskId());
    assertEquals(PUSH, meta.getExerciseTemplateId());
  }

  @Test
  void owningTeacherCanReadMissingDemonstrationWithoutEnrollmentLookup() {
    ExerciseDemonstrationDTO meta = service.metadata(TASK, PUSH);
    assertFalse(meta.isAvailable());
    assertEquals(PUSH, meta.getExerciseTemplateId());
    verify(classroomService, never()).isUserEnrolledInClassroom(any(), any());
  }

  @Test
  void enrolledStudentCanReadButCannotUploadOrDelete() {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    caller = STUDENT;
    assertTrue(service.metadata(TASK, PUSH).isAvailable());
    ResponseStatusException upload =
        assertThrows(
            ResponseStatusException.class,
            () -> service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4()));
    assertEquals(HttpStatus.FORBIDDEN, upload.getStatusCode());
    ResponseStatusException delete =
        assertThrows(ResponseStatusException.class, () -> service.delete(TASK, PUSH));
    assertEquals(HttpStatus.FORBIDDEN, delete.getStatusCode());
    assertEquals(1, rows.size());
  }

  @Test
  void owningTeacherCanUploadMp4AndWebm() throws Exception {
    ExerciseDemonstrationDTO push =
        service.upload(TASK, PUSH, "pushup-demo.mp4", "video/mp4", mp4());
    ExerciseDemonstrationDTO squat =
        service.upload(TASK, SQUAT, "squat-demo.webm", "video/webm", webm());
    assertTrue(push.isAvailable());
    assertTrue(squat.isAvailable());
    assertEquals("video/mp4", push.getContentType());
    assertEquals("video/webm", squat.getContentType());
    assertTrue(push.getPhysicalId().startsWith("DEMO-"));
    assertEquals(2, rows.size());
    assertNull(rows.get(0).getId());
    assertNull(rows.get(1).getId());
    try (InputStream in = service.content(TASK, PUSH)) {
      assertArrayEquals(mp4Bytes(), in.readAllBytes());
    }
  }

  @Test
  void otherTeacherCannotUpload() {
    caller = OTHER;
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4()));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    assertTrue(rows.isEmpty());
  }

  @Test
  void enrolledStudentCanReadMetadataAndContent() throws Exception {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    caller = STUDENT;
    ExerciseDemonstrationDTO meta = service.metadata(TASK, PUSH);
    assertTrue(meta.isAvailable());
    assertEquals("push.mp4", meta.getOriginalFilename());
    try (InputStream in = service.content(TASK, PUSH)) {
      assertTrue(in.readAllBytes().length > 0);
    }
  }

  @Test
  void unauthorizedStudentIsRejected() {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    caller = STRANGER;
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.metadata(TASK, PUSH));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    assertThrows(ResponseStatusException.class, () -> service.content(TASK, PUSH));
  }

  @Test
  void wrongTemplateIsRejected() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.upload(TASK, "EXERCISE-GLUTE", "g.mp4", "video/mp4", mp4()));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void invalidMimeAndEmptyAndExecutableAreRejected() {
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> service.upload(TASK, PUSH, "x.txt", "text/plain", mp4()))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> service.upload(TASK, PUSH, "x.mp4", "video/mp4", new ByteArrayInputStream(new byte[0])))
            .getStatusCode());
    byte[] exe = new byte[] {0x4D, 0x5A, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> service.upload(TASK, PUSH, "x.mp4", "video/mp4", new ByteArrayInputStream(exe)))
            .getStatusCode());
    assertTrue(rows.isEmpty());
  }

  @Test
  void octetStreamOrBlankTypeIsAcceptedWhenTheBytesAreMp4() {
    ExerciseDemonstrationDTO saved =
        service.upload(TASK, PUSH, "push.mp4", "application/octet-stream", mp4());
    assertEquals("video/mp4", rows.get(0).getContentType());
    assertEquals(saved.getPhysicalId(), rows.get(0).getPhysicalId());
    service.delete(TASK, PUSH);
    service.upload(TASK, PUSH, "push.mp4", "  ", mp4());
    assertEquals("video/mp4", rows.get(0).getContentType());
  }

  @Test
  void missingTaskIsRejectedBeforeStorage() {
    assertThrows(
        jakarta.persistence.EntityNotFoundException.class,
        () -> service.upload("TASK-MISSING", PUSH, "push.mp4", "video/mp4", mp4()));
    assertTrue(rows.isEmpty());
  }

  @Test
  void replacementLeavesOneRowAndDeletesTheOldFile() throws Exception {
    ExerciseDemonstrationDTO first = service.upload(TASK, PUSH, "old.mp4", "video/mp4", mp4());
    Path firstFile = tempDir.resolve(rows.get(0).getStorageKey());
    assertTrue(Files.exists(firstFile));
    ExerciseDemonstrationDTO second = service.upload(TASK, PUSH, "new.mp4", "video/mp4", mp4());
    assertEquals(1, rows.size());
    assertEquals(first.getPhysicalId(), second.getPhysicalId());
    assertEquals("new.mp4", second.getOriginalFilename());
    assertFalse(Files.exists(firstFile));
    assertTrue(Files.exists(tempDir.resolve(rows.get(0).getStorageKey())));
  }

  @Test
  void deleteRemovesMetadataAndFile() {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    Path file = tempDir.resolve(rows.get(0).getStorageKey());
    service.delete(TASK, PUSH);
    assertTrue(rows.isEmpty());
    assertFalse(Files.exists(file));
    caller = STUDENT;
    assertFalse(service.metadata(TASK, PUSH).isAvailable());
  }

  @Test
  void pushAndSquatStayIsolatedAcrossTasks() {
    service.upload(TASK, PUSH, "push-a.mp4", "video/mp4", mp4());
    service.upload(TASK, SQUAT, "squat.mp4", "video/mp4", mp4());
    service.upload(OTHER_TASK, PUSH, "push-b.mp4", "video/mp4", mp4());
    assertEquals("push-a.mp4", service.metadata(TASK, PUSH).getOriginalFilename());
    assertEquals("squat.mp4", service.metadata(TASK, SQUAT).getOriginalFilename());
    assertEquals("push-b.mp4", service.metadata(OTHER_TASK, PUSH).getOriginalFilename());
    ResponseStatusException missing =
        assertThrows(
            ResponseStatusException.class, () -> service.metadata(TASK, "EXERCISE-GLUTE"));
    assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());
  }

  @Test
  void reorderKeepsVideosAndRemovalDeletesOnlyThatVideo() {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    service.upload(TASK, SQUAT, "squat.mp4", "video/mp4", mp4());
    Path squatFile = tempDir.resolve(storageKeyFor(SQUAT));
    service.retainOnly(TASK, new LinkedHashSet<>(List.of(SQUAT, PUSH)));
    assertEquals(2, rows.size());
    assertTrue(Files.exists(squatFile));
    service.retainOnly(TASK, Set.of(PUSH));
    assertEquals(1, rows.size());
    assertEquals(PUSH, rows.get(0).getExerciseTemplateId());
    assertFalse(Files.exists(squatFile));
  }

  @Test
  void clearingEveryTemplateRemovesDemonstrationsForTaskDeletion() {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    service.upload(OTHER_TASK, PUSH, "other.mp4", "video/mp4", mp4());
    service.retainOnly(TASK, Set.of());
    assertEquals(1, rows.size());
    assertEquals(OTHER_TASK, rows.get(0).getTaskId());
  }

  @Test
  void configuredMaxSizeIsEnforced() {
    assertEquals(50L * 1024L * 1024L, ExerciseDemonstrationService.DEFAULT_MAX_BYTES);
    ExerciseDemonstrationService limited =
        new ExerciseDemonstrationService(
            demonstrationRepository,
            storage,
            taskRepository,
            taskExerciseRepository,
            classroomRepository,
            classroomService,
            physicalIdService,
            16L);
    byte[] over = new byte[17];
    System.arraycopy(mp4Bytes(), 0, over, 0, mp4Bytes().length);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> limited.upload(TASK, PUSH, "big.mp4", "video/mp4", new ByteArrayInputStream(over)));
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
    assertTrue(rows.isEmpty());
  }

  @Test
  void uploadDoesNotKeepADatabaseTransactionOpen() throws Exception {
    Method upload =
        ExerciseDemonstrationService.class.getMethod(
            "upload", String.class, String.class, String.class, String.class, InputStream.class);
    assertNull(upload.getAnnotation(Transactional.class));
  }

  @Test
  void metadataSaveFailureKeepsTheExistingVideo() throws Exception {
    service.upload(TASK, PUSH, "old.mp4", "video/mp4", mp4());
    String oldKey = rows.get(0).getStorageKey();
    doThrow(new DataIntegrityViolationException("constraint"))
        .when(demonstrationRepository)
        .save(any());
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.upload(TASK, PUSH, "new.mp4", "video/mp4", mp4()));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    assertEquals("Demonstration metadata could not be saved", ex.getReason());
    assertFalse(ex.getReason().contains("constraint"));
    assertEquals(1, rows.size());
    assertTrue(Files.exists(tempDir.resolve(oldKey)));
    assertEquals(1, Files.list(tempDir.resolve("demos").resolve(TASK).resolve(PUSH)).count());
  }

  @Test
  void replacementStorageFailureKeepsTheExistingVideo() throws Exception {
    service.upload(TASK, PUSH, "old.mp4", "video/mp4", mp4());
    String oldKey = rows.get(0).getStorageKey();
    ExerciseDemonstrationStorage failing =
        new ExerciseDemonstrationStorage() {
          @Override
          public ExerciseDemonstrationStorage.StoredDemonstration put(
              String storageKey, InputStream body, long maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "storage failed");
          }

          @Override
          public Optional<InputStream> open(String storageKey) {
            return storage.open(storageKey);
          }

          @Override
          public void delete(String storageKey) {
            storage.delete(storageKey);
          }
        };
    ExerciseDemonstrationService guarded =
        new ExerciseDemonstrationService(
            demonstrationRepository,
            failing,
            taskRepository,
            taskExerciseRepository,
            classroomRepository,
            classroomService,
            physicalIdService);
    assertThrows(
        ResponseStatusException.class,
        () -> guarded.upload(TASK, PUSH, "new.mp4", "video/mp4", mp4()));
    assertEquals(1, rows.size());
    assertEquals("old.mp4", rows.get(0).getOriginalFilename());
    assertEquals(oldKey, rows.get(0).getStorageKey());
    assertTrue(Files.exists(tempDir.resolve(oldKey)));
    try (InputStream in = service.content(TASK, PUSH)) {
      assertArrayEquals(mp4Bytes(), in.readAllBytes());
    }
  }

  @Test
  void deleteSucceedsWhenTheStoredFileIsAlreadyGone() throws Exception {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    Files.deleteIfExists(tempDir.resolve(rows.get(0).getStorageKey()));
    service.delete(TASK, PUSH);
    assertTrue(rows.isEmpty());
  }

  @Test
  void replaceHookOnTaskExerciseServicePreservesReorderAndDropsRemoval() {
    service.upload(TASK, PUSH, "push.mp4", "video/mp4", mp4());
    service.upload(TASK, SQUAT, "squat.mp4", "video/mp4", mp4());
    when(exerciseTemplateRepository.findByPhysicalId(PUSH))
        .thenReturn(
            Optional.of(
                ExerciseTemplate.builder().physicalId(PUSH).exerciseType(ExerciseType.PUSH_UP).build()));
    when(exerciseTemplateRepository.findByPhysicalId(SQUAT))
        .thenReturn(
            Optional.of(
                ExerciseTemplate.builder().physicalId(SQUAT).exerciseType(ExerciseType.SQUATS).build()));
    TaskExerciseService tasks =
        new TaskExerciseService(taskExerciseRepository, exerciseTemplateRepository, service);
    Task task = task(TASK);
    when(taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(TASK))
        .thenReturn(new ArrayList<>(exercises(TASK)));
    when(taskExerciseRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    tasks.replace(task, List.of(SQUAT, PUSH));
    assertEquals(2, rows.size());
    when(taskExerciseRepository.findByTaskIdOrderBySortOrderAsc(TASK)).thenReturn(List.of());
    tasks.replace(task, List.of(PUSH));
    assertEquals(1, rows.size());
    assertEquals(PUSH, rows.get(0).getExerciseTemplateId());
  }

  private String storageKeyFor(String templateId) {
    return rows.stream()
        .filter(row -> templateId.equals(row.getExerciseTemplateId()) && TASK.equals(row.getTaskId()))
        .findFirst()
        .orElseThrow()
        .getStorageKey();
  }

  private static Task task(String id) {
    return Task.builder().physicalId(id).classroomPhysicalId("CLASS-1").exerciseTemplateId(PUSH).build();
  }

  private static List<TaskExercise> exercises(String taskId) {
    if (!TASK.equals(taskId) && !OTHER_TASK.equals(taskId)) {
      return List.of();
    }
    TaskExercise push = new TaskExercise();
    push.setTaskId(taskId);
    push.setExerciseTemplateId(PUSH);
    push.setSortOrder(1);
    TaskExercise squat = new TaskExercise();
    squat.setTaskId(taskId);
    squat.setExerciseTemplateId(SQUAT);
    squat.setSortOrder(2);
    if (OTHER_TASK.equals(taskId)) {
      return List.of(push);
    }
    return List.of(push, squat);
  }

  private static InputStream mp4() {
    return new ByteArrayInputStream(mp4Bytes());
  }

  private static byte[] mp4Bytes() {
    return new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 1, 2, 3};
  }

  private static InputStream webm() {
    return new ByteArrayInputStream(
        new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4});
  }
}
