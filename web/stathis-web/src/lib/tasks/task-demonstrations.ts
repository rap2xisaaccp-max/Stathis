export type DemonstrationRecord = {
  exerciseTemplateId: string;
  originalFilename: string | null;
  previewUrl: string | null;
  uploading: boolean;
  error: string | null;
};

const ACCEPTED = new Set(['video/mp4', 'video/webm']);

/** Intended demonstration cap. The servlet limit is configured separately. */
export const DEMONSTRATION_MAX_BYTES = 50 * 1024 * 1024;

export function canUploadDemonstration(taskId: string | null | undefined): boolean {
  return Boolean(taskId && taskId.trim());
}

export function demonstrationFor(
  records: DemonstrationRecord[],
  exerciseTemplateId: string
): DemonstrationRecord | null {
  return records.find((row) => row.exerciseTemplateId === exerciseTemplateId) ?? null;
}

export function upsertDemonstration(
  records: DemonstrationRecord[],
  next: DemonstrationRecord
): DemonstrationRecord[] {
  const without = records.filter((row) => row.exerciseTemplateId !== next.exerciseTemplateId);
  return [...without, next];
}

export function removeDemonstration(
  records: DemonstrationRecord[],
  exerciseTemplateId: string
): DemonstrationRecord[] {
  return records.filter((row) => row.exerciseTemplateId !== exerciseTemplateId);
}

export function acceptDemonstrationFile(file: {
  type: string;
  size: number;
  name: string;
}): string | null {
  if (!file || file.size <= 0) {
    return 'Choose a video file';
  }
  const type = (file.type || '').split(';')[0].trim().toLowerCase();
  const name = file.name.toLowerCase();
  const extensionOk = name.endsWith('.mp4') || name.endsWith('.webm');
  if (!ACCEPTED.has(type) && !extensionOk) {
    return 'Use an MP4 or WebM video';
  }
  if (file.size > DEMONSTRATION_MAX_BYTES) {
    return 'Demonstration videos must be 50 MB or smaller';
  }
  return null;
}

export function demonstrationRequestError(status: number, action: 'upload' | 'remove' | 'load'): string {
  if (status === 0) {
    return 'Network error. Try again.';
  }
  if (status === 401 || status === 403) {
    return 'You are not allowed to change this demonstration';
  }
  if (status === 413) {
    return 'The server rejected this video because it is larger than the current upload limit';
  }
  if (action === 'remove') {
    return 'Could not remove demonstration';
  }
  if (action === 'load') {
    return 'Could not load demonstration';
  }
  return 'Could not upload demonstration';
}

export function demonstrationPath(taskId: string, exerciseTemplateId: string): string {
  return `/tasks/${encodeURIComponent(taskId)}/exercises/${encodeURIComponent(exerciseTemplateId)}/demonstration`;
}
