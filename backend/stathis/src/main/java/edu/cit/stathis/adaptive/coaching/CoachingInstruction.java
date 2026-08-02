package edu.cit.stathis.adaptive.coaching;

/**
 * Reviewed, exercise-scoped coaching copy. Not medically diagnostic.
 */
public record CoachingInstruction(
    String messageCode,
    String reminder,
    String escalation,
    String reinforcement) {

  public String textFor(InstructionIntensity intensity) {
    return switch (intensity) {
      case ESCALATION -> escalation;
      case REINFORCEMENT -> reinforcement;
      default -> reminder;
    };
  }

  public String codeFor(InstructionIntensity intensity) {
    return messageCode + "." + intensity.name();
  }
}
