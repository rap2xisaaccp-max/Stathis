package edu.cit.stathis.common.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Writes API errors on the original response. These statuses must stay 400, 403, 413, or 502
 * so a later error dispatch cannot replace them with an empty 403.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
    int code = ex.getStatusCode().value();
    String error = ex.getReason();
    if (error == null || error.isBlank()) {
      error = "Request failed";
    }
    return ResponseEntity.status(ex.getStatusCode())
        .body(Map.of("status", code, "error", error, "message", error));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException ex) {
    return payloadTooLarge();
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<Map<String, Object>> multipart(MultipartException ex) {
    if (causedBySize(ex)) {
      return payloadTooLarge();
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "status",
                400,
                "error",
                "Could not read the uploaded file",
                "message",
                "Could not read the uploaded file"));
  }

  private static ResponseEntity<Map<String, Object>> payloadTooLarge() {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(
            Map.of(
                "status",
                413,
                "error",
                "Demonstration videos must be 50 MB or smaller",
                "message",
                "Demonstration videos must be 50 MB or smaller"));
  }

  private static boolean causedBySize(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      String name = current.getClass().getName();
      if (name.endsWith("SizeLimitExceededException") || name.endsWith("FileSizeLimitExceededException")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
