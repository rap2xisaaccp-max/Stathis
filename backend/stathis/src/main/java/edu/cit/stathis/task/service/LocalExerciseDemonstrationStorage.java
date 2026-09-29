package edu.cit.stathis.task.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Local development store. Does not use the form-correction JPEG pipeline. */
@Service
public class LocalExerciseDemonstrationStorage implements ExerciseDemonstrationStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalExerciseDemonstrationStorage.class);
  private static final int BUFFER = 8192;

  private final Path root;

  public LocalExerciseDemonstrationStorage(
      @Value("${apsle.demonstration.local-dir:./data/demonstrations}") String localDir) {
    this.root = Path.of(localDir).toAbsolutePath().normalize();
  }

  @Override
  public StoredDemonstration put(String storageKey, InputStream body, long maxBytes) {
    Path file = resolveSafe(storageKey);
    try {
      Files.createDirectories(file.getParent());
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
        Files.deleteIfExists(file);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demonstration video is empty");
      }
      return new StoredDemonstration(storageKey, total);
    } catch (ResponseStatusException ex) {
      deleteQuietly(file);
      throw ex;
    } catch (IOException ex) {
      deleteQuietly(file);
      throw new IllegalStateException("Failed to store demonstration video", ex);
    }
  }

  @Override
  public Optional<InputStream> open(String storageKey) {
    try {
      Path file = resolveSafe(storageKey);
      if (!Files.exists(file)) {
        return Optional.empty();
      }
      return Optional.of(Files.newInputStream(file));
    } catch (IOException ex) {
      log.warn("Failed to open demonstration {}", storageKey, ex);
      return Optional.empty();
    }
  }

  @Override
  public void delete(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }
    deleteQuietly(resolveSafe(storageKey));
  }

  private void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ex) {
      log.warn("Failed to delete demonstration {}", file, ex);
    }
  }

  private Path resolveSafe(String storageKey) {
    if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid demonstration storage key");
    }
    Path resolved = root.resolve(storageKey).normalize();
    if (!resolved.startsWith(root)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid demonstration storage key");
    }
    return resolved;
  }
}
