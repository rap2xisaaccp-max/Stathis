export type FormErrorLabel = {
  label: string;
  explanation: string;
};

const FORM_ERROR_LABELS: Record<string, FormErrorLabel> = {
  SAG: {
    label: 'Body alignment sag',
    explanation: 'Hips or torso dropping below a straight body line.',
  },
  LOW_ROM: {
    label: 'Incomplete range of motion',
    explanation: 'Movement did not travel through a full useful range.',
  },
  DEPTH_LOW: {
    label: 'Insufficient depth',
    explanation: 'Squat or lunge did not reach enough depth.',
  },
  KNEES_IN: {
    label: 'Knees caving inward',
    explanation: 'Knees collapsing inward instead of tracking over the toes.',
  },
  CHEST_UP: {
    label: 'Chest / torso position',
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

/** Teacher-facing primary label with raw code secondary, e.g. "Body alignment sag (SAG)". */
export function formErrorDisplay(code: string | null | undefined): string {
  if (!code) return 'Unknown form error';
  const key = code.trim().toUpperCase();
  const label = formErrorLabel(key);
  if (label.toUpperCase() === key.replaceAll('_', ' ')) {
    return label;
  }
  return `${label} (${key})`;
}

export function preferredModalityCopy(opts: {
  modality?: string | null;
  source?: string | null;
  n?: number | null;
}): { title: string; detail: string } {
  const n = opts.n ?? 0;
  const source = (opts.source || 'DEFAULT').toUpperCase();
  const modality = opts.modality?.replaceAll('_', ' ') || '—';
  const detail =
    n === 1
      ? 'Based on 1 measured coaching response'
      : `Based on ${n} measured coaching responses`;

  if (source === 'LEARNED') {
    return { title: `Preferred: ${modality}`, detail };
  }
  if (source === 'EXPLORING') {
    return { title: `Still learning — trying ${modality}`, detail };
  }
  return {
    title: 'Still learning preferred coaching style',
    detail: n > 0 ? detail : 'Not enough measured coaching responses yet',
  };
}

export function formatLearningTrend(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)} severity`;
}

export function closedLoopSuccessCopy(
  successful: number | null | undefined,
  total: number | null | undefined
): string {
  const s = successful ?? 0;
  const t = total ?? 0;
  if (t <= 0) return 'No measured coaching interventions yet.';
  return `${s} of ${t} measured coaching interventions improved the student’s form.`;
}

export function isInsufficientFormCorrectionData(
  sessionsCount: number | null | undefined,
  masteryLevel: number | null | undefined
): boolean {
  return (sessionsCount ?? 0) > 0 && (masteryLevel ?? 0) < 0.05;
}
