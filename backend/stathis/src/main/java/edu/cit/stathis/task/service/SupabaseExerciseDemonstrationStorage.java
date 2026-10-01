package edu.cit.stathis.task.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Private Supabase Storage for demonstration videos. The bucket is separate from
 * form-correction evidence. Objects are never given a public URL.
 *
 * <p>Upload copies the video to a temporary file in 8 KB chunks, then streams that
 * file to Supabase with a known content length. The video is not held as a byte array.
 * Supabase's object API is called with {@code Content-Length}; chunked upload without
 * a length is not used.
 */
@Service
@ConditionalOnProperty(name = "apsle.demonstration.storage", havingValue = "supabase")
public class SupabaseExerciseDemonstrationStorage implements ExerciseDemonstrationStorage {

  public static final String DEFAULT_BUCKET = "exercise-demonstrations";

  private static final Logger log = LoggerFactory.getLogger(SupabaseExerciseDemonstrationStorage.class);
  private static final int BUFFER = 8192;
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  private final String baseUrl;
  private final String bucket;
  private final DemonstrationObjectClient client;

  @Autowired
  public SupabaseExerciseDemonstrationStorage(
      @Value("${apsle.demonstration.supabase-url:}") String supabaseUrl,
      @Value("${apsle.demonstration.supabase-service-key:}") String serviceKey) {
    this(supabaseUrl, serviceKey, null);
  }

  public SupabaseExerciseDemonstrationStorage(
      String supabaseUrl, String serviceKey, DemonstrationObjectClient client) {
    this.baseUrl = supabaseUrl == null ? "" : supabaseUrl.trim().replaceAll("/$", "");
    String key = serviceKey == null ? "" : serviceKey.trim();
    this.bucket = DEFAULT_BUCKET;
    if (this.baseUrl.isBlank() || key.isBlank()) {
      throw new IllegalStateException(
          "apsle.demonstration.storage=supabase requires a non-blank Supabase URL and service key "
              + "(set apsle.demonstration.supabase-url / apsle.demonstration.supabase-service-key, or "
              + "APSLE_DEMONSTRATION_SUPABASE_URL / APSLE_DEMONSTRATION_SUPABASE_SERVICE_KEY). "
              + "Refusing to start so demonstration uploads cannot fail later.");
    }
    this.client = client == null ? new JdkDemonstrationObjectClient(this.baseUrl, key, this.bucket) : client;
  }

  @Override
  public StoredDemonstration put(String storageKey, InputStream body, long maxBytes) {
    Path temp = null;
    try {
      temp = Files.createTempFile("stathis-demo-", ".bin");
      long size = copyLimited(body, temp, maxBytes);
      int status = client.put(storageKey, contentTypeFor(storageKey), temp);
      if (status >= 300) {
        client.delete(storageKey);
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Demonstration storage upload failed: " + status);
      }
      return new StoredDemonstration(storageKey, size);
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to store demonstration video", ex);
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ex) {
          log.warn("Failed to delete temporary demonstration upload");
        }
      }
    }
  }

  @Override
  public Optional<InputStream> open(String storageKey) {
    return openInternal(storageKey, null, Long.MAX_VALUE);
  }

  @Override
  public Optional<InputStream> openSlice(String storageKey, long startInclusive, long endInclusive) {
    String range = "bytes=" + startInclusive + "-" + endInclusive;
    long length = Math.max(0, endInclusive - startInclusive + 1);
    return openInternal(storageKey, range, length);
  }

  @Override
  public void delete(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }
    try {
      client.delete(storageKey);
    } catch (IOException ex) {
      log.warn("Demonstration storage delete failed for {}", storageKey);
    }
  }

  private Optional<InputStream> openInternal(String storageKey, String range, long limit) {
    try {
      DemonstrationObjectClient.GetResult result = client.get(storageKey, range);
      if (result.status() == 404) {
        return Optional.empty();
      }
      if (result.status() >= 300 || result.body() == null) {
        if (result.body() != null) {
          result.body().close();
        }
        log.warn("Demonstration storage read failed with status {}", result.status());
        return Optional.empty();
      }
      return Optional.of(new BoundedInputStream(result.body(), limit));
    } catch (IOException ex) {
      log.warn("Demonstration storage read failed for {}", storageKey);
      return Optional.empty();
    }
  }

  private static long copyLimited(InputStream body, Path file, long maxBytes) throws IOException {
    long total = 0;
    try (InputStream in = body; var out = Files.newOutputStream(file)) {
      byte[] buffer = new byte[BUFFER];
      int read;
      while ((read = in.read(buffer)) >= 0) {
        total += read;
        if (total > maxBytes) {
          throw new ResponseStatusException(
              HttpStatus.PAYLOAD_TOO_LARGE, "Demonstration video is too large");
        }
        out.write(buffer, 0, read);
      }
    }
    if (total == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demonstration video is empty");
    }
    return total;
  }

  private static String contentTypeFor(String storageKey) {
    return storageKey != null && storageKey.endsWith(".webm") ? "video/webm" : "video/mp4";
  }

  public interface DemonstrationObjectClient {
    int put(String storageKey, String contentType, Path file) throws IOException;

    GetResult get(String storageKey, String rangeHeader) throws IOException;

    void delete(String storageKey) throws IOException;

    record GetResult(int status, InputStream body) {}
  }

  /**
   * Streams the temp file with {@code BodyPublishers.ofFile} and downloads with
   * {@code BodyHandlers.ofInputStream}. Neither call loads the video into a byte array.
   */
  static final class JdkDemonstrationObjectClient implements DemonstrationObjectClient {
    private final HttpClient http =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String serviceKey;
    private final String bucket;

    JdkDemonstrationObjectClient(String baseUrl, String serviceKey, String bucket) {
      this.baseUrl = baseUrl;
      this.serviceKey = serviceKey;
      this.bucket = bucket;
    }

    @Override
    public int put(String storageKey, String contentType, Path file) throws IOException {
      HttpRequest request =
          authorized(storageKey)
              .timeout(TIMEOUT)
              .header("Content-Type", contentType)
              .header("x-upsert", "true")
              .PUT(HttpRequest.BodyPublishers.ofFile(file))
              .build();
      HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode();
    }

    @Override
    public GetResult get(String storageKey, String rangeHeader) throws IOException {
      HttpRequest.Builder builder = authorized(storageKey).timeout(TIMEOUT);
      if (rangeHeader != null && !rangeHeader.isBlank()) {
        builder.header("Range", rangeHeader);
      }
      HttpResponse<InputStream> response =
          send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() >= 300) {
        response.body().close();
        return new GetResult(response.statusCode(), null);
      }
      return new GetResult(response.statusCode(), response.body());
    }

    @Override
    public void delete(String storageKey) throws IOException {
      HttpRequest request = authorized(storageKey).timeout(TIMEOUT).DELETE().build();
      HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 300 && response.statusCode() != 404) {
        log.warn("Demonstration storage delete returned {}", response.statusCode());
      }
    }

    private HttpRequest.Builder authorized(String storageKey) {
      return HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/storage/v1/object/" + encode(bucket) + "/" + encodePath(storageKey)))
          .header("Authorization", "Bearer " + serviceKey)
          .header("apikey", serviceKey);
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException {
      try {
        return http.send(request, handler);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException("Demonstration storage request was interrupted", ex);
      }
    }

    private static String encodePath(String storageKey) {
      String[] parts = storageKey.split("/");
      StringBuilder path = new StringBuilder();
      for (String part : parts) {
        if (part.isEmpty()) {
          continue;
        }
        if (!path.isEmpty()) {
          path.append('/');
        }
        path.append(encode(part));
      }
      return path.toString();
    }

    private static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
  }
}
