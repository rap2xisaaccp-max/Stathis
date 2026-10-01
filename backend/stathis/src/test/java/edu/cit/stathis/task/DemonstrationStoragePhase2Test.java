package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cit.stathis.task.service.DemonstrationRange;
import edu.cit.stathis.task.service.ExerciseDemonstrationService;
import edu.cit.stathis.task.service.LocalExerciseDemonstrationStorage;
import edu.cit.stathis.task.service.SupabaseExerciseDemonstrationStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DemonstrationStoragePhase2Test {

  @TempDir Path tempDir;

  @Test
  void configurationSelectsLocalByDefaultAndSupabaseByValue() throws Exception {
    ConditionalOnProperty local =
        LocalExerciseDemonstrationStorage.class.getAnnotation(ConditionalOnProperty.class);
    ConditionalOnProperty remote =
        SupabaseExerciseDemonstrationStorage.class.getAnnotation(ConditionalOnProperty.class);
    assertEquals("apsle.demonstration.storage", local.name()[0]);
    assertEquals("local", local.havingValue());
    assertTrue(local.matchIfMissing());
    assertEquals("apsle.demonstration.storage", remote.name()[0]);
    assertEquals("supabase", remote.havingValue());
    assertFalse(remote.matchIfMissing());
    assertEquals("exercise-demonstrations", SupabaseExerciseDemonstrationStorage.DEFAULT_BUCKET);
    assertFalse(SupabaseExerciseDemonstrationStorage.DEFAULT_BUCKET.contains("form-correction"));
    assertEquals(52428800L, ExerciseDemonstrationService.DEFAULT_MAX_BYTES);
    java.lang.reflect.Parameter[] parameters =
        SupabaseExerciseDemonstrationStorage.class
            .getConstructor(String.class, String.class)
            .getParameters();
    assertEquals(
        "${apsle.demonstration.supabase-url:}",
        parameters[0].getAnnotation(Value.class).value());
    assertEquals(
        "${apsle.demonstration.supabase-service-key:}",
        parameters[1].getAnnotation(Value.class).value());
  }

  @Test
  void demonstrationSupabaseSettingsComeFromEnvironmentPlaceholders() throws Exception {
    String main = resourceText("/application.properties");
    String prod = resourceText("/application-prod.properties");
    assertEquals(
        "apsle.demonstration.supabase-url=${APSLE_DEMONSTRATION_SUPABASE_URL:}",
        propertyLine(main, "apsle.demonstration.supabase-url"));
    assertEquals(
        "apsle.demonstration.supabase-service-key=${APSLE_DEMONSTRATION_SUPABASE_SERVICE_KEY:}",
        propertyLine(main, "apsle.demonstration.supabase-service-key"));
    assertEquals(
        "apsle.demonstration.storage=${APSLE_DEMONSTRATION_STORAGE:supabase}",
        propertyLine(prod, "apsle.demonstration.storage"));
    assertEquals(
        "apsle.demonstration.supabase-url=${APSLE_DEMONSTRATION_SUPABASE_URL:}",
        propertyLine(prod, "apsle.demonstration.supabase-url"));
    assertEquals(
        "apsle.demonstration.supabase-service-key=${APSLE_DEMONSTRATION_SUPABASE_SERVICE_KEY:}",
        propertyLine(prod, "apsle.demonstration.supabase-service-key"));
    assertEquals(
        "spring.servlet.multipart.max-file-size=50MB",
        propertyLine(main, "spring.servlet.multipart.max-file-size"));
    assertEquals(
        "spring.servlet.multipart.max-request-size=55MB",
        propertyLine(main, "spring.servlet.multipart.max-request-size"));
  }

  @Test
  void supabaseModeRefusesToStartWithoutUrlOrKey() {
    IllegalStateException missingUrl =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SupabaseExerciseDemonstrationStorage(" ", "service-role-placeholder"));
    assertTrue(missingUrl.getMessage().contains("APSLE_DEMONSTRATION_SUPABASE_URL"));
    assertFalse(missingUrl.getMessage().contains("service-role-placeholder"));
    IllegalStateException missingKey =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SupabaseExerciseDemonstrationStorage("https://example.supabase.co", ""));
    assertTrue(missingKey.getMessage().contains("service key"));
    assertFalse(missingKey.getMessage().contains("https://example.supabase.co"));
  }

  @Test
  void supabaseStorageStreamsPutsRangesAndDeletesWithoutAPublicUrl() throws Exception {
    MemoryObjects memory = new MemoryObjects();
    SupabaseExerciseDemonstrationStorage storage =
        new SupabaseExerciseDemonstrationStorage(
            "https://example.supabase.co", "service-role-placeholder", memory);
    byte[] video = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 9, 8, 7, 6};
    storage.put("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4", new ByteArrayInputStream(video), 1024);
    assertEquals("video/mp4", memory.contentType);
    assertArrayEquals(video, memory.objects.get("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4"));
    assertFalse(memory.objects.keySet().stream().anyMatch(key -> key.contains("http") || key.contains("token")));
    try (InputStream slice = storage.openSlice("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4", 4, 7).orElseThrow()) {
      assertArrayEquals(new byte[] {'f', 't', 'y', 'p'}, slice.readAllBytes());
    }
    storage.delete("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4");
    assertTrue(storage.open("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4").isEmpty());
    storage.delete("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4");
  }

  @Test
  void supabasePutFailureDoesNotReplaceTheStoredObject() {
    MemoryObjects memory = new MemoryObjects();
    SupabaseExerciseDemonstrationStorage storage =
        new SupabaseExerciseDemonstrationStorage(
            "https://example.supabase.co", "service-role-placeholder", memory);
    byte[] original = new byte[] {1, 2, 3, 4};
    memory.objects.put("demos/TASK-A/EXERCISE-PUSH/DEMO-OLD.mp4", original);
    memory.failNextPut = true;
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                storage.put(
                    "demos/TASK-A/EXERCISE-PUSH/DEMO-NEW.mp4",
                    new ByteArrayInputStream(new byte[] {9, 9, 9, 9}),
                    1024));
    assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    assertArrayEquals(original, memory.objects.get("demos/TASK-A/EXERCISE-PUSH/DEMO-OLD.mp4"));
    assertFalse(memory.objects.containsKey("demos/TASK-A/EXERCISE-PUSH/DEMO-NEW.mp4"));
  }

  @Test
  void localSliceReadsOnlyTheRequestedBytes() throws Exception {
    LocalExerciseDemonstrationStorage storage = new LocalExerciseDemonstrationStorage(tempDir.toString());
    byte[] video = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
    storage.put("demos/TASK-A/EXERCISE-SQUAT/DEMO-2.mp4", new ByteArrayInputStream(video), 1024);
    try (InputStream slice =
        storage.openSlice("demos/TASK-A/EXERCISE-SQUAT/DEMO-2.mp4", 2, 4).orElseThrow()) {
      assertArrayEquals(new byte[] {2, 3, 4}, slice.readAllBytes());
    }
  }

  @Test
  void rangeHeaderParsing() {
    DemonstrationRange range = DemonstrationRange.parse("bytes=4-7", 12);
    assertEquals(4, range.start());
    assertEquals(7, range.endInclusive());
    assertEquals("bytes 4-7/12", range.contentRange(12));
    DemonstrationRange openEnded = DemonstrationRange.parse("bytes=10-", 12);
    assertEquals(11, openEnded.endInclusive());
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> DemonstrationRange.parse("bytes=20-30", 12));
    assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, ex.getStatusCode());
  }

  private static String resourceText(String path) throws Exception {
    try (InputStream input = DemonstrationStoragePhase2Test.class.getResourceAsStream(path)) {
      assertTrue(input != null);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String propertyLine(String properties, String key) {
    String prefix = key + "=";
    for (String line : properties.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith(prefix)) {
        return trimmed;
      }
    }
    throw new AssertionError("Missing property " + key);
  }

  private static final class MemoryObjects implements SupabaseExerciseDemonstrationStorage.DemonstrationObjectClient {
    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private String contentType = "";
    private boolean failNextPut;

    @Override
    public int put(String storageKey, String contentType, Path file) throws IOException {
      this.contentType = contentType;
      if (failNextPut) {
        failNextPut = false;
        return 500;
      }
      objects.put(storageKey, Files.readAllBytes(file));
      return 200;
    }

    @Override
    public GetResult get(String storageKey, String rangeHeader) {
      byte[] data = objects.get(storageKey);
      if (data == null) {
        return new GetResult(404, null);
      }
      if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
        String spec = rangeHeader.substring("bytes=".length());
        String[] parts = spec.split("-", 2);
        int start = Integer.parseInt(parts[0]);
        int end = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : data.length - 1;
        byte[] slice = java.util.Arrays.copyOfRange(data, start, end + 1);
        return new GetResult(206, new ByteArrayInputStream(slice));
      }
      return new GetResult(200, new ByteArrayInputStream(data));
    }

    @Override
    public void delete(String storageKey) {
      objects.remove(storageKey);
    }
  }
}
