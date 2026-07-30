'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { fetchClassroomEvaluation } from '@/services/adaptive/api-adaptive-client';
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
import { Brain, ArrowRight } from 'lucide-react';

function formatLift(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(3)}`;
}

function formatSuccessLift(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—';
  const sign = value > 0 ? '+' : '';
  return `${sign}${(value * 100).toFixed(1)} pp`;
}

export function ClassroomAdaptiveCard({ classroomId }: { classroomId?: string }) {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['adaptive-classroom-evaluation', classroomId],
    queryFn: () => fetchClassroomEvaluation(classroomId!),
    enabled: !!classroomId,
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    retry: 1,
  });

  if (!classroomId) {
    return (
      <Card className="overflow-hidden rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl shadow-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <Brain className="h-5 w-5" />
            Adaptive coaching
          </CardTitle>
          <CardDescription>
            Select a classroom to preview Adaptive vs Static coaching comparison, then open any
            student and switch to the Adaptive tab.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  if (isLoading) {
    return <Skeleton className="h-40 w-full rounded-2xl" />;
  }

  if (isError) {
    return (
      <Card className="overflow-hidden rounded-2xl border-destructive/30 bg-card/80 backdrop-blur-xl shadow-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <Brain className="h-5 w-5" />
            Adaptive coaching
          </CardTitle>
          <CardDescription>
            Could not load classroom adaptive metrics. This is an API failure, not empty data.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" size="sm" variant="outline" onClick={() => refetch()}>
            Retry
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl shadow-lg">
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle className="flex items-center gap-2 text-xl">
              <Brain className="h-5 w-5" />
              Adaptive coaching
            </CardTitle>
            <CardDescription>
              Classroom Adaptive vs Static comparison. Open a student → Adaptive tab for full
              insights.
            </CardDescription>
          </div>
          {data?.adaptiveMeanDelta != null &&
            data?.staticMeanDelta != null &&
            data?.adaptiveOutperformsOnDelta != null && (
            <Badge variant={data.adaptiveOutperformsOnDelta ? 'default' : 'secondary'}>
              {data.adaptiveOutperformsOnDelta
                ? 'Adaptive leading on Δ'
                : 'No Adaptive Δ lead yet'}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {!data ? (
          <p className="text-sm text-muted-foreground">
            No classroom coaching comparison yet. Metrics appear after both study arms log
            interventions.
          </p>
        ) : data.adaptiveMeanDelta == null || data.staticMeanDelta == null ? (
          <p className="text-sm text-muted-foreground">
            Insufficient data for Adaptive vs Static comparison. Both study arms require
            completed sessions before lift metrics are shown.
          </p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-3 text-sm">
            <div>
              <p className="text-muted-foreground">Students tracked</p>
              <p className="font-medium text-lg">{data.studentCount}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Improvement lift (Δ)</p>
              <p className="font-medium text-lg">{formatLift(data.meanDeltaLift)}</p>
            </div>
            <div>
              <p className="text-muted-foreground">Success-rate lift</p>
              <p className="font-medium text-lg">{formatSuccessLift(data.successRateLift)}</p>
            </div>
          </div>
        )}
        <Button type="button" size="sm" variant="outline" asChild>
          <Link href={`/student-progress?classroomId=${encodeURIComponent(classroomId)}&focus=adaptive`}>
            Open student Adaptive insights
            <ArrowRight className="ml-1 h-3.5 w-3.5" />
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}
