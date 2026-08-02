export type FormErrorLabel = {
  label: string;
  explanation: string;
};

const FORM_ERROR_LABELS: Record<string, FormErrorLabel> = {
  SAG: {
    label: 'Hips sagging',
    explanation: 'Hips or torso dropping below a straight body line.',
  },
  LOW_ROM: {
    label: 'Incomplete movement',
    explanation: 'Movement did not travel through the full useful range.',
  },
  DEPTH_LOW: {
    label: 'Not deep enough',
    explanation: 'Squat or lunge did not reach enough depth.',
  },
  KNEES_IN: {
    label: 'Knees moving inward',
    explanation: 'Knees collapsing inward instead of tracking over the toes.',
  },
  CHEST_UP: {
    label: 'Chest / torso dropping',
    explanation: 'Torso collapsing or not staying upright.',
  },
  PIKE: {
    label: 'Hips too high',
    explanation: 'Hips rising above a straight push-up line.',
  },
  LEGS_BENT: {
    label: 'Legs not straight',
    explanation: 'Knees bent during a straight-leg movement.',
  },
  LOW_CONFIDENCE: {
    label: 'Pose uncertainty (technical)',
    explanation: 'Camera/model confidence was too low to coach form.',
  },
  LOW_VISIBILITY: {
    label: 'Low visibility (technical)',
    explanation: 'Body landmarks were not clearly visible to the camera.',
  },
  BODY_NOT_VISIBLE: {
    label: 'Body not visible (technical)',
    explanation: 'Camera framing did not capture enough of the body.',
  },
  UNKNOWN: {
    label: 'Unclassified form cue',
    explanation: 'A coaching cue without a specific catalogued error code.',
  },
};

export const MORE_COACHING_DATA_NEEDED =
  'APSLE needs more successful coaching responses before recommending an exercise difficulty. Completing sessions alone is not enough.';

export function formErrorLabel(code: string | null | undefined): string {
  if (!code) return 'Unknown form error';
  const key = code.trim().toUpperCase();
  return FORM_ERROR_LABELS[key]?.label || key.replaceAll('_', ' ');
}

export function formErrorExplanation(code: string | null | undefined): string {
  if (!code) return '';
  const key = code.trim().toUpperCase();
  return FORM_ERROR_LABELS[key]?.explanation || '';
}

/** Teacher label only — no raw APSLE codes in the UI. */
export function formErrorDisplay(code: string | null | undefined): string {
  return formErrorLabel(code);
}

/** Teacher-facing coaching method names (internal enum values unchanged). */
export function formatModalityLabel(modality: string | null | undefined): string {
  if (!modality) return '—';
  const key = modality.trim().toUpperCase().replace(/[\s-]+/g, '_');
  switch (key) {
    case 'VERBAL_TTS':
      return 'Voice Coaching';
    case 'VERBAL_TEXT':
      return 'Text Coaching';
    case 'VISUAL_HIGHLIGHT':
      return 'Visual Guidance';
    default:
      return modality.replaceAll('_', ' ');
  }
}

export function preferredModalityCopy(opts: {
  modality?: string | null;
  source?: string | null;
  n?: number | null;
}): { title: string; detail: string } {
  const n = opts.n ?? 0;
  const source = (opts.source || 'DEFAULT').toUpperCase();
  const modality = formatModalityLabel(opts.modality);
  const detail =
    n === 1
      ? 'Based on 1 successful coaching response.'
      : `Based on ${n} successful coaching responses.`;

  if (source === 'LEARNED') {
    return { title: `Preferred: ${modality}`, detail };
  }
  if (source === 'EXPLORING') {
    return { title: `Still learning — trying ${modality}`, detail };
  }
  return {
    title: 'Still learning preferred coaching style',
    detail: n > 0 ? detail : 'Not enough successful coaching responses yet.',
  };
}

/** Plain-language Learning Progress label (numeric APSLE value stays in tooltip). */
export function formatLearningProgressLabel(
  value: number | null | undefined
): string {
  if (value == null || Number.isNaN(value)) return '—';
  if (value > 0) return 'Improving';
  if (value < 0) return 'Needs attention';
  return 'Stable';
}

export function formatLearningProgressTooltip(
  value: number | null | undefined
): string {
  if (value == null || Number.isNaN(value)) {
    return 'No recent learning progress estimate yet.';
  }
  const sign = value > 0 ? '+' : '';
  return `APSLE learning progress estimate: ${sign}${value.toFixed(2)}`;
}

export function learningProgressDescription(
  value: number | null | undefined
): string {
  if (value == null || Number.isNaN(value)) {
    return 'Learning progress will appear after successful coaching responses.';
  }
  if (value > 0) {
    return "Recent coaching sessions are helping reduce the student's form errors.";
  }
  if (value < 0) {
    return "Recent coaching sessions have not yet reduced the student's form errors.";
  }
  return 'Little recent change in the student’s form errors.';
}

/** @deprecated Use formatLearningProgressLabel — kept for any residual imports. */
export function formatLearningTrend(value: number | null | undefined): string {
  return formatLearningProgressLabel(value);
}

export function closedLoopSuccessCopy(
  successful: number | null | undefined,
  total: number | null | undefined
): string {
  const s = successful ?? 0;
  const t = total ?? 0;
  if (t <= 0) return 'No coaching sessions with measured form improvement yet.';
  return `${s} of ${t} coaching sessions resulted in measurable improvements to the student's form.`;
}

export function isInsufficientFormCorrectionData(
  sessionsCount: number | null | undefined,
  masteryLevel: number | null | undefined
): boolean {
  return (sessionsCount ?? 0) > 0 && (masteryLevel ?? 0) < 0.05;
}

export function formatImprovementDeltaTooltip(
  value: number | null | undefined
): string {
  if (value == null || Number.isNaN(value)) return '';
  const sign = value > 0 ? '+' : '';
  return `APSLE form improvement: ${sign}${value.toFixed(2)}`;
}
