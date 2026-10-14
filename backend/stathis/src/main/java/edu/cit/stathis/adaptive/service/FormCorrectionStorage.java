package edu.cit.stathis.adaptive.service;

import java.util.Optional;

/** Private object store for form-correction snapshots. Never public URLs. */
public interface FormCorrectionStorage {

  StoredObject put(String studentId, String interventionId, byte[] jpeg);

  Optional<byte[]> get(String storageKey);

  void delete(String storageKey);

  /**
   * Removes the object and returns only when it is confirmed absent. Throws when the object is
   * still present or the store cannot confirm removal. A missing object is success.
   */
  void deleteStrict(String storageKey);

  /** Writes bytes back to an existing key. Throws when the write fails. */
  void putAtKey(String storageKey, byte[] jpeg);

  record StoredObject(String storageKey, int byteSize, String sha256) {}
}
