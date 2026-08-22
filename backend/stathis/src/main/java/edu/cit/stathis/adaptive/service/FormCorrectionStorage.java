package edu.cit.stathis.adaptive.service;

import java.util.Optional;

/** Private object store for form-correction snapshots. Never public URLs. */
public interface FormCorrectionStorage {

  StoredObject put(String studentId, String interventionId, byte[] jpeg);

  Optional<byte[]> get(String storageKey);

  void delete(String storageKey);

  record StoredObject(String storageKey, int byteSize, String sha256) {}
}
