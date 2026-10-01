package edu.cit.stathis.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cit.stathis.task.controller.ExerciseDemonstrationController;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigCorsTest {

  private CorsConfigurationSource source;

  @BeforeEach
  void setUp() {
    source = new SecurityConfig().corsConfigurationSource();
  }

  @Test
  void productionTeacherWebOriginIsAllowedWithExistingOrigins() {
    CorsConfiguration cors = configurationFor("/api/tasks/task/exercises/template/demonstration");

    assertEquals("http://localhost:3000", cors.checkOrigin("http://localhost:3000"));
    assertEquals(
        "https://stathis-x68s.onrender.com",
        cors.checkOrigin("https://stathis-x68s.onrender.com"));
    assertEquals(
        "https://stathis-backend-fresh.onrender.com",
        cors.checkOrigin("https://stathis-backend-fresh.onrender.com"));
    assertEquals("https://stathis.ryne.dev", cors.checkOrigin("https://stathis.ryne.dev"));
    assertEquals(Boolean.TRUE, cors.getAllowCredentials());
    assertTrue(cors.getAllowedMethods().containsAll(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
    assertFalse(cors.getAllowedOrigins().contains("*"));
    assertTrue(cors.getAllowedOriginPatterns() == null || cors.getAllowedOriginPatterns().isEmpty());
  }

  @Test
  void unknownOriginsStayRejected() {
    CorsConfiguration cors = configurationFor("/api/tasks/task/exercises/template/demonstration");

    assertNull(cors.checkOrigin("https://evil.example"));
    assertNull(cors.checkOrigin("http://stathis.ryne.dev"));
    assertNull(cors.checkOrigin("https://stathis.ryne.dev.evil.com"));
    assertNull(cors.checkOrigin("https://api-stathis.ryne.dev"));
  }

  @Test
  void demonstrationUploadStillRequiresTeacherRole() throws Exception {
    PreAuthorize upload =
        ExerciseDemonstrationController.class
            .getMethod(
                "upload", String.class, String.class, org.springframework.web.multipart.MultipartFile.class)
            .getAnnotation(PreAuthorize.class);
    PreAuthorize metadata =
        ExerciseDemonstrationController.class
            .getMethod("metadata", String.class, String.class)
            .getAnnotation(PreAuthorize.class);

    assertEquals("hasRole('TEACHER')", upload.value());
    assertEquals("hasAnyRole('TEACHER', 'STUDENT')", metadata.value());
  }

  private CorsConfiguration configurationFor(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.addHeader(HttpHeaders.ORIGIN, "https://stathis.ryne.dev");
    CorsConfiguration cors = source.getCorsConfiguration(request);
    assertNotNull(cors);
    return cors;
  }
}
