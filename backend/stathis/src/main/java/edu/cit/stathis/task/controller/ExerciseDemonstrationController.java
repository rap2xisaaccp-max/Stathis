package edu.cit.stathis.task.controller;

import edu.cit.stathis.task.dto.ExerciseDemonstrationDTO;
import edu.cit.stathis.task.service.DemonstrationRange;
import edu.cit.stathis.task.service.ExerciseDemonstrationService;
import edu.cit.stathis.task.service.ExerciseDemonstrationService.DemonstrationBytes;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks/{taskId}/exercises/{exerciseTemplateId}/demonstration")
@RequiredArgsConstructor
public class ExerciseDemonstrationController {

  private final ExerciseDemonstrationService demonstrationService;

  @GetMapping
  @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
  public ExerciseDemonstrationDTO metadata(
      @PathVariable String taskId, @PathVariable String exerciseTemplateId) {
    return demonstrationService.metadata(taskId, exerciseTemplateId);
  }

  @GetMapping("/content")
  @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
  public ResponseEntity<InputStreamResource> content(
      @PathVariable String taskId,
      @PathVariable String exerciseTemplateId,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
    ExerciseDemonstrationDTO meta = demonstrationService.metadata(taskId, exerciseTemplateId);
    if (!meta.isAvailable()) {
      return ResponseEntity.notFound().build();
    }
    MediaType type = MediaType.parseMediaType(meta.getContentType());
    String disposition = "inline; filename=\"" + safeFilename(meta.getOriginalFilename()) + "\"";
    if (range == null || range.isBlank()) {
      InputStream body = demonstrationService.content(taskId, exerciseTemplateId);
      return ResponseEntity.ok()
          .contentType(type)
          .contentLength(meta.getByteSize() == null ? 0 : meta.getByteSize())
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
          .body(new InputStreamResource(body));
    }
    long total = meta.getByteSize() == null ? 0 : meta.getByteSize();
    DemonstrationRange parsed = DemonstrationRange.parse(range, total);
    DemonstrationBytes slice =
        demonstrationService.contentSlice(taskId, exerciseTemplateId, parsed.start(), parsed.endInclusive());
    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(type)
        .contentLength(parsed.length())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(HttpHeaders.CONTENT_RANGE, parsed.contentRange(slice.total()))
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .body(new InputStreamResource(slice.body()));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('TEACHER')")
  public ExerciseDemonstrationDTO upload(
      @PathVariable String taskId,
      @PathVariable String exerciseTemplateId,
      @RequestParam("file") MultipartFile file) {
    try {
      return demonstrationService.upload(
          taskId,
          exerciseTemplateId,
          file.getOriginalFilename(),
          file.getContentType(),
          file.getInputStream());
    } catch (java.io.IOException ex) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Could not read demonstration video");
    }
  }

  @DeleteMapping
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<Void> delete(
      @PathVariable String taskId, @PathVariable String exerciseTemplateId) {
    demonstrationService.delete(taskId, exerciseTemplateId);
    return ResponseEntity.noContent().build();
  }

  private static String safeFilename(String name) {
    if (name == null || name.isBlank()) {
      return "demonstration.mp4";
    }
    return name.replace("\"", "").replace("\r", "").replace("\n", "");
  }
}
