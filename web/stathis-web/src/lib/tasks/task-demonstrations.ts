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

export function demonstrationMessageFromBody(body: string | null | undefined): string | null {
  if (!body) return null;
  const trimmed = body.trim();
  if (!trimmed || trimmed.startsWith('<') || trimmed.length > 300) return null;
  try {
    const parsed = JSON.parse(trimmed) as { error?: unknown; message?: unknown };
    const text =
      typeof parsed.error === 'string'
        ? parsed.error
        : typeof parsed.message === 'string'
          ? parsed.message
          : null;
    if (!text) return null;
    const message = text.trim();
    return message && message.length <= 300 ? message : null;
  } catch {
    return trimmed;
  }
}

export function demonstrationUploadFile(file: File): File {
  const type = (file.type || '').split(';')[0].trim().toLowerCase();
  if (ACCEPTED.has(type)) return file;
  const name = file.name.toLowerCase();
  const inferred = name.endsWith('.webm') ? 'video/webm' : name.endsWith('.mp4') ? 'video/mp4' : '';
  if (!inferred) return file;
  return new File([file], file.name, { type: inferred, lastModified: file.lastModified });
}

export function demonstrationRequestError(
  status: number,
  action: 'upload' | 'remove' | 'load',
  serverMessage?: string | null
): string {
  const detail = serverMessage && serverMessage.trim() ? serverMessage.trim() : null;
  if (status === 0) {
    return 'Network error. Try again.';
  }
  if (status === 401 || (status === 403 && detail === 'Authentication required')) {
    return 'Sign in again. This request was not authenticated.';
  }
  if (status === 403) {
    return detail ?? 'You are not allowed to change this demonstration';
  }
  if (status === 413) {
    return detail ?? 'Demonstration videos must be 50 MB or smaller';
  }
  if (status === 400) {
    return detail ?? 'This video could not be accepted. Use an MP4 or WebM file under 50 MB.';
  }
  if (status === 404) {
    return detail ?? 'This task, exercise, or demonstration was not found.';
  }
  if (status === 502 || status >= 500) {
    return detail ?? 'The video could not be stored. Try again.';
  }
  if (detail) return detail;
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
