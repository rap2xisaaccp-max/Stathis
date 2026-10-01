package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cit.stathis.task.service.DemonstrationRange;
import edu.cit.stathis.task.service.ExerciseDemonstrationService;
import edu.cit.stathis.task.service.ExerciseDemonstrationStorage;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
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
    assertTrue(
        SupabaseExerciseDemonstrationStorage.class
            .getConstructor(String.class, String.class)
            .isAnnotationPresent(Autowired.class));
  }

  @Test
  void springUsesTheConfiguredConstructorForSupabaseMode() {
    try (AnnotationConfigApplicationContext context = storageContext("supabase")) {
      assertTrue(context.getBean(ExerciseDemonstrationStorage.class) instanceof SupabaseExerciseDemonstrationStorage);
      assertFalse(context.containsBean("localExerciseDemonstrationStorage"));
    }
  }

  @Test
  void springUsesLocalStorageWhenModeIsLocal() {
    try (AnnotationConfigApplicationContext context = storageContext("local")) {
      assertTrue(context.getBean(ExerciseDemonstrationStorage.class) instanceof LocalExerciseDemonstrationStorage);
      assertFalse(context.containsBean("supabaseExerciseDemonstrationStorage"));
    }
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
    assertEquals(
        "server.tomcat.max-http-form-post-size=55MB",
        propertyLine(main, "server.tomcat.max-http-form-post-size"));
    assertEquals(
        "server.tomcat.max-swallow-size=55MB",
        propertyLine(main, "server.tomcat.max-swallow-size"));
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
    assertEquals("Demonstration storage upload failed: 500", ex.getReason());
    assertArrayEquals(original, memory.objects.get("demos/TASK-A/EXERCISE-PUSH/DEMO-OLD.mp4"));
    assertFalse(memory.objects.containsKey("demos/TASK-A/EXERCISE-PUSH/DEMO-NEW.mp4"));
  }

  @Test
  void earlySupabaseCloseBecomes502InsteadOfAnUncaughtIoException() throws Exception {
    com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    String objectPath = "/storage/v1/object/exercise-demonstrations/demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4";
    server.createContext(
        objectPath,
        exchange -> {
          byte[] body =
              "{\"statusCode\":\"404\",\"error\":\"Bucket not found\",\"message\":\"Bucket not found\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(404, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
          exchange.close();
        });
    server.start();
    try {
      SupabaseExerciseDemonstrationStorage storage =
          new SupabaseExerciseDemonstrationStorage(
              "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-key", null);
      byte[] video = new byte[256 * 1024];
      video[4] = 'f';
      video[5] = 't';
      video[6] = 'y';
      video[7] = 'p';
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () ->
                  storage.put(
                      "demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4",
                      new ByteArrayInputStream(video),
                      50L * 1024L * 1024L));
      assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
      assertTrue(ex.getReason().startsWith("Demonstration storage upload failed: 404"));
      assertFalse(ex.getReason().contains("test-service-key"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void completedSupabaseErrorIncludesStatusAndSafeMessage() throws Exception {
    com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    String objectPath = "/storage/v1/object/exercise-demonstrations/demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4";
    server.createContext(
        objectPath,
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              "{\"statusCode\":\"400\",\"error\":\"Invalid\",\"message\":\"Invalid Compact JWS\"}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(400, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
          exchange.close();
        });
    server.start();
    try {
      SupabaseExerciseDemonstrationStorage storage =
          new SupabaseExerciseDemonstrationStorage(
              "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-key", null);
      byte[] video = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'};
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () ->
                  storage.put(
                      "demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4",
                      new ByteArrayInputStream(video),
                      1024));
      assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
      assertEquals("Demonstration storage upload failed: 400 (Invalid Compact JWS)", ex.getReason());
      assertFalse(ex.getReason().contains("test-service-key"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void supabaseErrorBodyThatLooksLikeACredentialIsNotReturned() throws Exception {
    com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    String objectPath = "/storage/v1/object/exercise-demonstrations/demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4";
    server.createContext(
        objectPath,
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              "{\"message\":\"eyJhbGciOiJIUzI1NiJ9.secret\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(401, body.length);
          try (var out = exchange.getResponseBody()) {
            out.write(body);
          }
          exchange.close();
        });
    server.start();
    try {
      SupabaseExerciseDemonstrationStorage storage =
          new SupabaseExerciseDemonstrationStorage(
              "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-key", null);
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () ->
                  storage.put(
                      "demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4",
                      new ByteArrayInputStream(new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'}),
                      1024));
      assertEquals("Demonstration storage upload failed: 401", ex.getReason());
      assertFalse(ex.getReason().contains("eyJ"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void successfulSupabasePutSendsTheVideoWithoutLoadingItAsTheResponse() throws Exception {
    com.sun.net.httpserver.HttpServer server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    String objectPath = "/storage/v1/object/exercise-demonstrations/demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4";
    java.util.concurrent.atomic.AtomicInteger seenBytes = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicReference<String> seenType = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<String> seenAuth = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<String> seenApiKey = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<String> seenMethod = new java.util.concurrent.atomic.AtomicReference<>();
    server.createContext(
        objectPath,
        exchange -> {
          byte[] uploaded = exchange.getRequestBody().readAllBytes();
          seenBytes.set(uploaded.length);
          seenType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          seenApiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
          seenMethod.set(exchange.getRequestMethod());
          byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, ok.length);
          try (var out = exchange.getResponseBody()) {
            out.write(ok);
          }
          exchange.close();
        });
    server.start();
    try {
      SupabaseExerciseDemonstrationStorage storage =
          new SupabaseExerciseDemonstrationStorage(
              "http://127.0.0.1:" + server.getAddress().getPort(), "test-service-key", null);
      byte[] video = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 1, 2, 3, 4};
      storage.put("demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4", new ByteArrayInputStream(video), 1024);
      assertEquals(video.length, seenBytes.get());
      assertEquals("video/mp4", seenType.get());
      assertEquals("PUT", seenMethod.get());
      assertEquals("Bearer test-service-key", seenAuth.get());
      assertEquals("test-service-key", seenApiKey.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void ioFailureBeforeAResponseIs502AndDoesNotReplaceTheStoredObject() {
    MemoryObjects memory = new MemoryObjects();
    memory.throwOnPut = new IOException("fixed content-length: 76, bytes received: 0");
    SupabaseExerciseDemonstrationStorage storage =
        new SupabaseExerciseDemonstrationStorage(
            "https://example.supabase.co", "service-role-placeholder", memory);
    byte[] original = new byte[] {1, 2, 3, 4};
    memory.objects.put("demos/TASK-A/EXERCISE-PUSH/DEMO-OLD.mp4", original);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                storage.put(
                    "demos/TASK-A/EXERCISE-PUSH/DEMO-NEW.mp4",
                    new ByteArrayInputStream(new byte[] {9, 9, 9, 9}),
                    1024));
    assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    assertEquals("Demonstration storage upload failed", ex.getReason());
    assertFalse(ex.getReason().contains("fixed content-length"));
    assertArrayEquals(original, memory.objects.get("demos/TASK-A/EXERCISE-PUSH/DEMO-OLD.mp4"));
    assertFalse(memory.objects.containsKey("demos/TASK-A/EXERCISE-PUSH/DEMO-NEW.mp4"));
  }

  @Test
  void invalidSupabaseStatusIs502() {
    MemoryObjects memory = new MemoryObjects();
    memory.nextStatus = 0;
    SupabaseExerciseDemonstrationStorage storage =
        new SupabaseExerciseDemonstrationStorage(
            "https://example.supabase.co", "service-role-placeholder", memory);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                storage.put(
                    "demos/TASK-A/EXERCISE-PUSH/DEMO-1.mp4",
                    new ByteArrayInputStream(new byte[] {9, 9, 9, 9}),
                    1024));
    assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    assertEquals("Demonstration storage upload failed", ex.getReason());
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

  private static AnnotationConfigApplicationContext storageContext(String mode) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("apsle.demonstration.storage", mode);
    properties.put("apsle.demonstration.supabase-url", "https://example.supabase.co");
    properties.put("apsle.demonstration.supabase-service-key", "configured-key");
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("demonstration-storage", properties));
    context.register(LocalExerciseDemonstrationStorage.class, SupabaseExerciseDemonstrationStorage.class);
    context.refresh();
    return context;
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
    private IOException throwOnPut;
    private int nextStatus = 200;

    @Override
    public SupabaseExerciseDemonstrationStorage.DemonstrationObjectClient.PutResult put(
        String storageKey, String contentType, Path file) throws IOException {
      this.contentType = contentType;
      if (throwOnPut != null) {
        IOException ex = throwOnPut;
        throwOnPut = null;
        throw ex;
      }
      if (failNextPut) {
        failNextPut = false;
        return new SupabaseExerciseDemonstrationStorage.DemonstrationObjectClient.PutResult(500, null);
      }
      if (nextStatus < 200 || nextStatus >= 300) {
        return new SupabaseExerciseDemonstrationStorage.DemonstrationObjectClient.PutResult(
            nextStatus, null);
      }
      objects.put(storageKey, Files.readAllBytes(file));
      return new SupabaseExerciseDemonstrationStorage.DemonstrationObjectClient.PutResult(200, null);
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
