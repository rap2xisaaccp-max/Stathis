package edu.cit.stathis.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new BoomController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void storageFailureStays502WithTheServerMessage() throws Exception {
    mockMvc
        .perform(get("/boom"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.error").value("Demonstration storage upload failed: 400"))
        .andExpect(jsonPath("$.message").value("Demonstration storage upload failed: 400"));
  }

  @Test
  void ownerDenialStays403WithTheClassroomMessage() throws Exception {
    mockMvc
        .perform(get("/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Not authorized for this classroom"));
  }

  @Test
  void uploadLargerThanTheMultipartLimitIs413() throws Exception {
    mockMvc
        .perform(post("/big"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.status").value(413));
  }

  @Test
  void unreadableMultipartIs400() throws Exception {
    mockMvc.perform(post("/parts")).andExpect(status().isBadRequest());
  }

  @RestController
  static class BoomController {
    @GetMapping("/boom")
    void boom() {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Demonstration storage upload failed: 400");
    }

    @GetMapping("/denied")
    void denied() {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this classroom");
    }

    @PostMapping("/big")
    void big() {
      throw new MaxUploadSizeExceededException(50L * 1024L * 1024L);
    }

    @PostMapping("/parts")
    void parts() {
      throw new MultipartException("Could not parse");
    }
  }
}
