package edu.cit.stathis.task.service;

import java.io.InputStream;
import java.util.Optional;

/**
 * Private store for exercise demonstration videos. Local disk is the development
 * implementation. Supabase is a separate implementation selected by configuration.
 */
public interface ExerciseDemonstrationStorage {

  StoredDemonstration put(String storageKey, InputStream body, long maxBytes);

  Optional<InputStream> open(String storageKey);

  /**
   * Opens a byte slice. The returned stream contains only {@code startInclusive}
   * through {@code endInclusive}. The default reads from {@link #open} and skips,
   * which downloads leading bytes. Supabase overrides this and sends a Range header.
   */
  default Optional<InputStream> openSlice(String storageKey, long startInclusive, long endInclusive) {
    return open(storageKey).map(in -> slice(in, startInclusive, endInclusive));
  }

  static InputStream slice(InputStream in, long startInclusive, long endInclusive) {
    try {
      long remaining = startInclusive;
      while (remaining > 0) {
        long skipped = in.skip(remaining);
        if (skipped <= 0) {
          if (in.read() < 0) {
            break;
          }
          skipped = 1;
        }
        remaining -= skipped;
      }
    } catch (java.io.IOException ex) {
      try {
        in.close();
      } catch (java.io.IOException ignored) {
        // The caller receives the original failure.
      }
      throw new java.io.UncheckedIOException(ex);
    }
    long length = Math.max(0, endInclusive - startInclusive + 1);
    return new BoundedInputStream(in, length);
  }

  void delete(String storageKey);

  record StoredDemonstration(String storageKey, long byteSize) {}
}
