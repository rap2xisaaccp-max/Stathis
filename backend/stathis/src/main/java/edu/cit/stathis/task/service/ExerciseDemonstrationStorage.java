package edu.cit.stathis.task.service;

import java.io.InputStream;
import java.util.Optional;

/**
 * Private store for exercise demonstration videos. Phase 1 is local disk.
 * A later Supabase implementation can satisfy this interface without changing
 * the demonstration API.
 */
public interface ExerciseDemonstrationStorage {

  StoredDemonstration put(String storageKey, InputStream body, long maxBytes);

  Optional<InputStream> open(String storageKey);

  void delete(String storageKey);

  record StoredDemonstration(String storageKey, long byteSize) {}
}
