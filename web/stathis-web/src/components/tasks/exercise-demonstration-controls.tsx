'use client';

import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { API_BASE_URL } from '@/lib/api/server-client';
import {
  acceptDemonstrationFile,
  demonstrationPath,
  type DemonstrationRecord,
} from '@/lib/tasks/task-demonstrations';
import { Loader2 } from 'lucide-react';

type ExerciseDemonstrationControlsProps = {
  taskId?: string | null;
  exerciseTemplateId: string;
};

type RemoteDemonstration = {
  available?: boolean;
  originalFilename?: string | null;
  contentType?: string | null;
};

function authHeaders(): HeadersInit {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('auth_token');
  if (!token) return {};
  return { Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}` };
}

export function ExerciseDemonstrationControls({
  taskId,
  exerciseTemplateId,
}: ExerciseDemonstrationControlsProps) {
  const [record, setRecord] = useState<DemonstrationRecord>({
    exerciseTemplateId,
    originalFilename: null,
    previewUrl: null,
    uploading: false,
    error: null,
  });

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;
    if (!taskId) return;

    async function load() {
      try {
        const metaResponse = await fetch(`${API_BASE_URL}${demonstrationPath(taskId!, exerciseTemplateId)}`, {
          headers: authHeaders(),
        });
        if (!metaResponse.ok) {
          throw new Error('Could not load demonstration');
        }
        const meta = (await metaResponse.json()) as RemoteDemonstration;
        if (!meta.available) {
          if (!cancelled) {
            setRecord({
              exerciseTemplateId,
              originalFilename: null,
              previewUrl: null,
              uploading: false,
              error: null,
            });
          }
          return;
        }
        const content = await fetch(
          `${API_BASE_URL}${demonstrationPath(taskId!, exerciseTemplateId)}/content`,
          { headers: authHeaders() }
        );
        if (!content.ok) {
          throw new Error('Could not load demonstration video');
        }
        const blob = await content.blob();
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) {
          setRecord({
            exerciseTemplateId,
            originalFilename: meta.originalFilename ?? 'Demonstration',
            previewUrl: objectUrl,
            uploading: false,
            error: null,
          });
        }
      } catch (error) {
        if (!cancelled) {
          setRecord((current) => ({
            ...current,
            uploading: false,
            error: error instanceof Error ? error.message : 'Could not load demonstration',
          }));
        }
      }
    }

    load();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [taskId, exerciseTemplateId]);

  if (!taskId) {
    return (
      <p className="mt-3 text-sm text-muted-foreground">
        Save the task before adding a demonstration video.
      </p>
    );
  }

  const upload = async (file: File | undefined) => {
    if (!file) return;
    const rejected = acceptDemonstrationFile(file);
    if (rejected) {
      setRecord((current) => ({ ...current, error: rejected }));
      return;
    }
    setRecord((current) => ({ ...current, uploading: true, error: null }));
    try {
      const body = new FormData();
      body.append('file', file);
      const response = await fetch(`${API_BASE_URL}${demonstrationPath(taskId, exerciseTemplateId)}`, {
        method: 'POST',
        headers: authHeaders(),
        body,
      });
      if (!response.ok) {
        throw new Error('Could not upload demonstration');
      }
      const meta = (await response.json()) as RemoteDemonstration;
      const previewUrl = URL.createObjectURL(file);
      setRecord((current) => {
        if (current.previewUrl) URL.revokeObjectURL(current.previewUrl);
        return {
          exerciseTemplateId,
          originalFilename: meta.originalFilename ?? file.name,
          previewUrl,
          uploading: false,
          error: null,
        };
      });
    } catch (error) {
      setRecord((current) => ({
        ...current,
        uploading: false,
        error: error instanceof Error ? error.message : 'Could not upload demonstration',
      }));
    }
  };

  const remove = async () => {
    setRecord((current) => ({ ...current, uploading: true, error: null }));
    try {
      const response = await fetch(`${API_BASE_URL}${demonstrationPath(taskId, exerciseTemplateId)}`, {
        method: 'DELETE',
        headers: authHeaders(),
      });
      if (!response.ok && response.status !== 204) {
        throw new Error('Could not remove demonstration');
      }
      setRecord((current) => {
        if (current.previewUrl) URL.revokeObjectURL(current.previewUrl);
        return {
          exerciseTemplateId,
          originalFilename: null,
          previewUrl: null,
          uploading: false,
          error: null,
        };
      });
    } catch (error) {
      setRecord((current) => ({
        ...current,
        uploading: false,
        error: error instanceof Error ? error.message : 'Could not remove demonstration',
      }));
    }
  };

  return (
    <div className="mt-3 space-y-2">
      <div className="text-sm font-medium">Demonstration</div>
      {record.originalFilename ? (
        <p className="text-sm text-muted-foreground">{record.originalFilename}</p>
      ) : (
        <p className="text-sm text-muted-foreground">No demonstration video</p>
      )}
      {record.uploading && (
        <p className="flex items-center text-sm text-muted-foreground">
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          Uploading...
        </p>
      )}
      {record.error && <p className="text-sm text-red-600">{record.error}</p>}
      {record.previewUrl && (
        <video className="max-h-48 w-full rounded-xl bg-black" controls src={record.previewUrl} />
      )}
      <div className="flex flex-wrap gap-2">
        <label className="inline-flex">
          <input
            className="sr-only"
            type="file"
            accept="video/mp4,video/webm"
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = '';
              void upload(file);
            }}
          />
          <span className="inline-flex h-9 cursor-pointer items-center rounded-xl border border-border px-3 text-sm">
            {record.originalFilename ? 'Replace' : 'Add demonstration'}
          </span>
        </label>
        {record.originalFilename && (
          <Button type="button" variant="outline" className="h-9 rounded-xl" onClick={() => void remove()}>
            Remove
          </Button>
        )}
      </div>
    </div>
  );
}
