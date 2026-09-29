export type DemonstrationRecord = {
  exerciseTemplateId: string;
  originalFilename: string | null;
  previewUrl: string | null;
  uploading: boolean;
  error: string | null;
};

const ACCEPTED = new Set(['video/mp4', 'video/webm']);

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
  if (file.size > 1024 * 1024) {
    return 'This environment currently accepts videos up to 1 MB';
  }
  return null;
}

export function demonstrationPath(taskId: string, exerciseTemplateId: string): string {
  return `/tasks/${encodeURIComponent(taskId)}/exercises/${encodeURIComponent(exerciseTemplateId)}/demonstration`;
}
