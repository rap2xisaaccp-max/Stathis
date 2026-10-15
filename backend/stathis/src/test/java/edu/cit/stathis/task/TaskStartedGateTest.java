package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Observable student-access rules for teacher Start gating
 * (StudentTaskService / TaskCompletionService / TaskService student paths).
 */
class TaskStartedGateTest {

  static void requireTaskStarted(boolean isStarted, boolean isActive) {
    if (!isStarted) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Task has not been started by the teacher yet");
    }
    if (!isActive) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task is no longer active");
    }
  }

  static boolean isStudentVisible(boolean isActive, boolean isStarted) {
    return isActive && isStarted;
  }

  @Test
  void unstartedHiddenFromStudentList() {
    assertFalse(isStudentVisible(true, false));
    assertTrue(isStudentVisible(true, true));
    assertFalse(isStudentVisible(false, true));
  }

  @Test
  void detailProgressCompleteBlockedWhenNotStarted() {
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> requireTaskStarted(false, true));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }

  @Test
  void detailProgressCompleteAllowedWhenStartedAndActive() {
    requireTaskStarted(true, true);
  }

  @Test
  void inactiveStartedStillBlocked() {
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> requireTaskStarted(true, false));
    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
  }
}
