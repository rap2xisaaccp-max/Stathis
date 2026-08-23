package edu.cit.stathis.adaptive;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cit.stathis.adaptive.service.SupabaseFormCorrectionStorage;
import org.junit.jupiter.api.Test;

class SupabaseFormCorrectionStorageConfigTest {

  @Test
  void refusesToStartWhenUrlIsBlank() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SupabaseFormCorrectionStorage(
                    "  ", "not-a-real-key", "form-correction-evidence"));
    assertTrue(ex.getMessage().contains("supabase"));
    assertTrue(ex.getMessage().contains("APSLE_EVIDENCE_SUPABASE_URL"));
    assertFalse(ex.getMessage().contains("not-a-real-key"));
  }

  @Test
  void refusesToStartWhenServiceKeyIsBlank() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SupabaseFormCorrectionStorage(
                    "https://example.supabase.co", "", "form-correction-evidence"));
    assertTrue(ex.getMessage().contains("service key"));
    assertFalse(ex.getMessage().contains("https://example.supabase.co"));
  }

  @Test
  void constructsWhenUrlAndKeyPresentAndKeepsDefaultBucket() {
    assertDoesNotThrow(
        () ->
            new SupabaseFormCorrectionStorage(
                "https://example.supabase.co",
                "service-role-placeholder",
                "form-correction-evidence"));
  }
}
