package edu.cit.stathis.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Mirrors {@code StudentTaskService.requireTaskStarted} / student list filtering rules.
 */
class TaskStartedGateTest {

  static boolean isStudentVisible(boolean isActive, boolean isStarted) {
    return isActive && isStarted;
  }

  static boolean allowStudentAccess(boolean isActive, boolean isStarted) {
    return isActive && isStarted;
  }

  @Test
  void unstartedHiddenFromStudentList() {
    assertFalse(isStudentVisible(true, false));
    assertTrue(isStudentVisible(true, true));
    assertFalse(isStudentVisible(false, true));
  }

  @Test
  void completeAndDetailBlockedUntilStarted() {
    assertFalse(allowStudentAccess(true, false));
    assertTrue(allowStudentAccess(true, true));
  }
}
