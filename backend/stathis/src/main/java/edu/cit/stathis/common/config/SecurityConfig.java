package edu.cit.stathis.common.config;

import edu.cit.stathis.auth.service.CustomUserDetailsService;
import edu.cit.stathis.common.utils.JwtUtil;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter) throws Exception {
        logger.debug("Configuring security filter chain");
        
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/**",
                        "/api/posture/**",
                        "/ws/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                        .requestMatchers("/api/templates/**")
                    .permitAll()
                        .requestMatchers(
                            "/api/classrooms/**",
                            "/api/classrooms/*/students/**",
                            "/api/classrooms/*/students/*/verify")
                        .authenticated()
                        .requestMatchers("/api/users/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        logger.debug("Security filter chain configured successfully");
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    return corsConfigurationSource(allowedOrigins);
  }

  /**
   * Origins come from {@code cors.allowed-origins}. Spring Boot binds
   * {@code CORS_ALLOWED_ORIGINS} to that property. Values are comma-separated
   * and trimmed. {@code *} is rejected. Origin patterns are not used.
   */
  CorsConfigurationSource corsConfigurationSource(String rawOrigins) {
    logger.debug("Configuring CORS with allowed origins: {}", rawOrigins);

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(parseAllowedOrigins(rawOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    logger.debug("CORS configuration completed");
    return source;
  }

  static List<String> parseAllowedOrigins(String rawOrigins) {
    if (rawOrigins == null || rawOrigins.isBlank()) {
      throw new IllegalStateException("cors.allowed-origins must name at least one origin");
    }
    List<String> origins = new ArrayList<>();
    for (String part : rawOrigins.split(",")) {
      String origin = part.trim();
      if (origin.isEmpty()) {
        continue;
      }
      if (origin.indexOf('*') >= 0) {
        throw new IllegalStateException("cors.allowed-origins must not use *");
      }
      origins.add(origin);
    }
    if (origins.isEmpty()) {
      throw new IllegalStateException("cors.allowed-origins must name at least one origin");
    }
    return List.copyOf(origins);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
    return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
  }
}
