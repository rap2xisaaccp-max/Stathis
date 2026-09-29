package edu.cit.stathis.task.service;

import java.util.Set;

/**
 * Keeps demonstration videos aligned with the exercise templates still assigned
 * to a task. Reorder keeps the same template ids. Removing a template drops only
 * that video. This does not change completion or sort rules.
 */
public interface ExerciseDemonstrationRetention {

  void retainOnly(String taskId, Set<String> exerciseTemplateIds);

  Set<String> templateIds(String taskId);
}
