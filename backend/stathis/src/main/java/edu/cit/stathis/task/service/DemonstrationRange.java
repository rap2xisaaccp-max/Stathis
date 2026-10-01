package edu.cit.stathis.task.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Inclusive HTTP byte range. Suffix ranges are rejected. */
public record DemonstrationRange(long start, long endInclusive) {

  public static DemonstrationRange parse(String header, long total) {
    if (total <= 0) {
      throw unsatisfiable();
    }
    if (header == null || header.isBlank()) {
      throw unsatisfiable();
    }
    String trimmed = header.trim();
    if (!trimmed.toLowerCase(Locale.ROOT).startsWith("bytes=")) {
      throw unsatisfiable();
    }
    String spec = trimmed.substring("bytes=".length()).trim();
    int comma = spec.indexOf(',');
    if (comma >= 0) {
      spec = spec.substring(0, comma).trim();
    }
    int dash = spec.indexOf('-');
    if (dash <= 0) {
      throw unsatisfiable();
    }
    String left = spec.substring(0, dash).trim();
    String right = spec.substring(dash + 1).trim();
    try {
      long start = Long.parseLong(left);
      long end = right.isEmpty() ? total - 1 : Long.parseLong(right);
      if (start < 0 || start >= total || end < start) {
        throw unsatisfiable();
      }
      if (end >= total) {
        end = total - 1;
      }
      return new DemonstrationRange(start, end);
    } catch (NumberFormatException ex) {
      throw unsatisfiable();
    }
  }

  public String contentRange(long total) {
    return "bytes " + start + "-" + endInclusive + "/" + total;
  }

  public long length() {
    return endInclusive - start + 1;
  }

  private static ResponseStatusException unsatisfiable() {
    return new ResponseStatusException(
        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Demonstration range is not satisfiable");
  }
}
