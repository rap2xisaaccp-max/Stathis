package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cit.stathis.adaptive.service.LocalFormCorrectionStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFormCorrectionStorageTest {

  @TempDir Path temp;

  @Test
  void deleteStrictRemovesOnlyTheSelectedSnapshot() throws Exception {
    LocalFormCorrectionStorage storage = new LocalFormCorrectionStorage(temp.toString());
    storage.put("STUDENT-1", "FI-1", new byte[] {1, 2, 3});
    storage.put("STUDENT-1", "FI-2", new byte[] {4, 5, 6});

    storage.deleteStrict("STUDENT-1/FI-1.jpg");

    assertTrue(storage.get("STUDENT-1/FI-1.jpg").isEmpty());
    assertArrayEquals(new byte[] {4, 5, 6}, storage.get("STUDENT-1/FI-2.jpg").orElseThrow());
  }

  @Test
  void deleteStrictSucceedsWhenTheSnapshotIsAlreadyAbsent() {
    LocalFormCorrectionStorage storage = new LocalFormCorrectionStorage(temp.toString());

    storage.deleteStrict("STUDENT-1/missing.jpg");

    assertTrue(storage.get("STUDENT-1/missing.jpg").isEmpty());
  }

  @Test
  void putAtKeyRestoresTheSameObject() throws Exception {
    LocalFormCorrectionStorage storage = new LocalFormCorrectionStorage(temp.toString());
    byte[] jpeg = new byte[] {7, 8, 9};
    storage.put("STUDENT-1", "FI-1", new byte[] {1});
    storage.deleteStrict("STUDENT-1/FI-1.jpg");

    storage.putAtKey("STUDENT-1/FI-1.jpg", jpeg);

    assertArrayEquals(jpeg, storage.get("STUDENT-1/FI-1.jpg").orElseThrow());
    assertFalse(Files.exists(temp.resolve("STUDENT-1/FI-2.jpg")));
  }

  @Test
  void rejectsAStorageKeyThatEscapesTheEvidenceDirectory() {
    LocalFormCorrectionStorage storage = new LocalFormCorrectionStorage(temp.toString());

    assertThrows(IllegalArgumentException.class, () -> storage.deleteStrict("../outside.jpg"));
    assertThrows(IllegalArgumentException.class, () -> storage.putAtKey("../outside.jpg", new byte[] {1}));
  }
}
