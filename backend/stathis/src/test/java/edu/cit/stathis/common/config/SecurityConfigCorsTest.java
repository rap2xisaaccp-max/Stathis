package edu.cit.stathis.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cit.stathis.task.controller.ExerciseDemonstrationController;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

class SecurityConfigCorsTest {

  private static final String CONFIGURED_ORIGINS =
      " https://stathis.ryne.dev, https://stathis-x68s.onrender.com, "
          + "https://stathis-backend-fresh.onrender.com, http://localhost:3000 ";

  private CorsConfigurationSource source;

  @BeforeEach
  void setUp() throws Exception {
    assertEquals(
        "${cors.allowed-origins}",
        SecurityConfig.class.getDeclaredField("allowedOrigins").getAnnotation(Value.class).value());
    source = new SecurityConfig().corsConfigurationSource(CONFIGURED_ORIGINS);
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

    assertNull(cors.checkOrigin("https://evil.example.com"));
    assertNull(cors.checkOrigin("http://stathis.ryne.dev"));
    assertNull(cors.checkOrigin("https://api-stathis.ryne.dev"));
    assertEquals(
        List.of(
            "https://stathis.ryne.dev",
            "https://stathis-x68s.onrender.com",
            "https://stathis-backend-fresh.onrender.com",
            "http://localhost:3000"),
        cors.getAllowedOrigins());
  }

  @Test
  void loginPreflightReturnsAllowOriginForEachConfiguredOrigin() throws Exception {
    for (String origin : List.of(
        "https://stathis-x68s.onrender.com",
        "https://stathis.ryne.dev",
        "https://stathis-backend-fresh.onrender.com",
        "http://localhost:3000")) {
      MockHttpServletResponse response = preflight(origin);
      assertEquals(200, response.getStatus(), origin);
      assertEquals(origin, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
      assertEquals("true", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
      String allowedHeaders = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
      assertNotNull(allowedHeaders);
      assertTrue(allowedHeaders.toLowerCase().contains("authorization"));
      assertTrue(allowedHeaders.toLowerCase().contains("content-type"));
    }
  }

  @Test
  void loginPreflightRejectsUnknownOriginWithoutAllowOriginHeader() throws Exception {
    MockHttpServletResponse response = preflight("https://evil.example.com");
    assertEquals(403, response.getStatus());
    assertNull(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void corsEnvironmentVariableBindsAsSeparateOrigins() {
    Map<String, Object> env = new HashMap<>();
    env.put(
        "CORS_ALLOWED_ORIGINS",
        "https://stathis.ryne.dev,https://stathis-x68s.onrender.com,"
            + "https://stathis-backend-fresh.onrender.com,http://localhost:3000");
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().replace(
        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        new SystemEnvironmentPropertySource(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));

    String bound = environment.getProperty("cors.allowed-origins");
    assertEquals(
        List.of(
            "https://stathis.ryne.dev",
            "https://stathis-x68s.onrender.com",
            "https://stathis-backend-fresh.onrender.com",
            "http://localhost:3000"),
        SecurityConfig.parseAllowedOrigins(bound));
  }

  @Test
  void wildcardOriginIsRejected() {
    assertThrows(
        IllegalStateException.class,
        () -> new SecurityConfig().corsConfigurationSource("https://stathis.ryne.dev,*"));
    assertThrows(IllegalStateException.class, () -> new SecurityConfig().corsConfigurationSource(" * "));
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

  private MockHttpServletResponse preflight(String origin) throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/auth/login");
    request.addHeader(HttpHeaders.ORIGIN, origin);
    request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
    request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
    MockHttpServletResponse response = new MockHttpServletResponse();
    new CorsFilter(source).doFilter(request, response, (req, res) -> {
      throw new AssertionError("preflight must be completed by the CORS filter");
    });
    return response;
  }

  private CorsConfiguration configurationFor(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.addHeader(HttpHeaders.ORIGIN, "https://stathis.ryne.dev");
    CorsConfiguration cors = source.getCorsConfiguration(request);
    assertNotNull(cors);
    return cors;
  }
}
