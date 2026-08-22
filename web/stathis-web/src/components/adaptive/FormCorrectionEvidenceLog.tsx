'use client';

import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  fetchFormCorrectionEvidence,
  FormCorrectionEvidenceDTO,
} from '@/services/adaptive/api-adaptive-client';
import { API_BASE_URL } from '@/lib/api/server-client';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { AlertCircle, Camera, RefreshCw } from 'lucide-react';
import { formErrorLabel } from '@/components/adaptive/form-error-labels';

function formatWhen(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatExercise(value: string | null | undefined): string {
  if (!value) return 'Exercise';
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

function EvidenceThumbnail({ evidence }: { evidence: FormCorrectionEvidenceDTO }) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;
    async function load() {
      try {
        const token =
          typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null;
        const path =
          evidence.imageUrl ||
          `/adaptive/evidence/${encodeURIComponent(evidence.physicalId)}/image`;
        const url = `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
        const response = await fetch(url, {
          headers: token
            ? { Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}` }
            : {},
        });
        if (!response.ok) {
          throw new Error(`image ${response.status}`);
        }
        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) setSrc(objectUrl);
      } catch {
        if (!cancelled) setFailed(true);
      }
    }
    load();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [evidence.imageUrl, evidence.physicalId]);

  if (failed) {
    return (
      <div className="flex h-36 w-full items-center justify-center rounded-xl bg-muted text-muted-foreground">
        <Camera className="mr-2 h-4 w-4" />
        Snapshot unavailable
      </div>
    );
  }
  if (!src) {
    return <Skeleton className="h-36 w-full rounded-xl" />;
  }
  return (
    <img
      src={src}
      alt={`${formatExercise(evidence.exerciseType)} form correction`}
      className="h-36 w-full rounded-xl object-cover bg-muted"
    />
  );
}

export function FormCorrectionEvidenceLog({
  studentId,
  classroomId,
}: {
  studentId: string;
  classroomId?: string;
}) {
  const query = useQuery({
    queryKey: ['form-correction-evidence', studentId, classroomId],
    queryFn: () => fetchFormCorrectionEvidence(studentId, classroomId),
    enabled: !!studentId,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });

  if (query.isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-40 w-full rounded-2xl" />
        ))}
      </div>
    );
  }

  if (query.isError) {
    return (
      <Card className="border-destructive/30">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <AlertCircle className="h-4 w-4" />
            Could not load form-correction evidence
          </CardTitle>
          <CardDescription>This is an API failure, not an empty log.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" size="sm" variant="outline" onClick={() => query.refetch()}>
            <RefreshCw className="mr-1 h-3.5 w-3.5" />
            Retry
          </Button>
        </CardContent>
      </Card>
    );
  }

  const items = query.data ?? [];
  if (items.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border/70 py-12 text-center text-muted-foreground">
        No form-correction evidence yet
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {items.map((item) => (
        <Card key={item.physicalId} className="overflow-hidden border-border/50">
          <CardContent className="grid gap-4 p-4 md:grid-cols-[220px_1fr]">
            <EvidenceThumbnail evidence={item} />
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base font-semibold">{formatExercise(item.exerciseType)}</h3>
                <Badge variant="secondary">
                  {item.errorLabel || formErrorLabel(item.errorCode)}
                </Badge>
              </div>
              <p className="text-sm text-muted-foreground">
                {item.errorDescription || 'Incorrect form was confirmed during the attempt.'}
              </p>
              <p className="text-sm">
                <span className="font-medium">What to do: </span>
                {item.correctionText || 'Adjust form and continue with control.'}
              </p>
              <p className="text-sm">
                <span className="font-medium">Correction delivered: </span>
                {item.correctionText || '—'}
              </p>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                <span>{formatWhen(item.capturedAt || item.createdAt)}</span>
                {item.taskId ? <span>Task {item.taskId}</span> : <span>Practice session</span>}
                {item.attemptNumber != null ? <span>Attempt {item.attemptNumber}</span> : null}
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
