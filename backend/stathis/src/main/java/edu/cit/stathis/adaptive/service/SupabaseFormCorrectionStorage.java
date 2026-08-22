package edu.cit.stathis.adaptive.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "apsle.evidence.storage", havingValue = "supabase")
public class SupabaseFormCorrectionStorage implements FormCorrectionStorage {

  private static final Logger log = LoggerFactory.getLogger(SupabaseFormCorrectionStorage.class);

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;
  private final String serviceKey;
  private final String bucket;

  public SupabaseFormCorrectionStorage(
      @Value("${apsle.evidence.supabase-url:}") String supabaseUrl,
      @Value("${apsle.evidence.supabase-service-key:}") String serviceKey,
      @Value("${apsle.evidence.supabase-bucket:form-correction-evidence}") String bucket) {
    this.baseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/$", "");
    this.serviceKey = serviceKey == null ? "" : serviceKey;
    this.bucket = bucket;
  }

  @Override
  public StoredObject put(String studentId, String interventionId, byte[] jpeg) {
    String objectPath = safe(studentId) + "/" + safe(interventionId) + ".jpg";
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + objectPath))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + serviceKey)
            .header("apikey", serviceKey)
            .header("Content-Type", "image/jpeg")
            .header("x-upsert", "true")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(jpeg))
            .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Supabase storage upload failed: " + response.statusCode());
      }
      return new StoredObject(objectPath, jpeg.length, sha256(jpeg));
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Supabase storage upload failed", ex);
    }
  }

  @Override
  public Optional<byte[]> get(String storageKey) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + storageKey))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + serviceKey)
            .header("apikey", serviceKey)
            .GET()
            .build();
    try {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() >= 300) {
        return Optional.empty();
      }
      return Optional.ofNullable(response.body());
    } catch (Exception ex) {
      log.warn("Supabase storage read failed for {}", storageKey, ex);
      return Optional.empty();
    }
  }

  @Override
  public void delete(String storageKey) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + storageKey))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + serviceKey)
            .header("apikey", serviceKey)
            .DELETE()
            .build();
    try {
      http.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (Exception ex) {
      log.warn("Supabase storage delete failed for {}", storageKey, ex);
    }
  }

  private static String safe(String raw) {
    return raw == null ? "unknown" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ex) {
      return null;
    }
  }
}
