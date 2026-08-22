package edu.cit.stathis.adaptive.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = "apsle.evidence.storage",
    havingValue = "local",
    matchIfMissing = true)
public class LocalFormCorrectionStorage implements FormCorrectionStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalFormCorrectionStorage.class);

  private final Path root;

  public LocalFormCorrectionStorage(
      @Value("${apsle.evidence.local-dir:./data/evidence}") String localDir) {
    this.root = Path.of(localDir).toAbsolutePath().normalize();
  }

  @Override
  public StoredObject put(String studentId, String interventionId, byte[] jpeg) {
    try {
      Path studentDir = root.resolve(safe(studentId));
      Files.createDirectories(studentDir);
      Path file = studentDir.resolve(safe(interventionId) + ".jpg");
      Files.write(file, jpeg);
      return new StoredObject(root.relativize(file).toString().replace('\\', '/'), jpeg.length, sha256(jpeg));
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to store evidence image", ex);
    }
  }

  @Override
  public Optional<byte[]> get(String storageKey) {
    try {
      Path file = resolveSafe(storageKey);
      if (!Files.exists(file)) {
        return Optional.empty();
      }
      return Optional.of(Files.readAllBytes(file));
    } catch (IOException ex) {
      log.warn("Failed to read evidence {}", storageKey, ex);
      return Optional.empty();
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(resolveSafe(storageKey));
    } catch (IOException ex) {
      log.warn("Failed to delete evidence {}", storageKey, ex);
    }
  }

  private Path resolveSafe(String storageKey) {
    Path resolved = root.resolve(storageKey).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    return resolved;
  }

  private static String safe(String raw) {
    return raw == null ? "unknown" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      return null;
    }
  }
}
