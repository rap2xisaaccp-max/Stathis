'use client';

import Link from 'next/link';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Brain, ArrowRight } from 'lucide-react';

export function ClassroomAdaptiveCard({ classroomId }: { classroomId?: string }) {
  if (!classroomId) {
    return (
      <Card className="overflow-hidden rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl shadow-lg">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <Brain className="h-5 w-5" />
            Adaptive coaching
          </CardTitle>
          <CardDescription>
            Select a classroom, then open any student and switch to the Adaptive tab.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl shadow-lg">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-xl">
          <Brain className="h-5 w-5" />
          Adaptive coaching
        </CardTitle>
        <CardDescription>
          Open a student to view Preferred Modality by Exercise, modality effectiveness,
          closed-loop success, recurring form errors, mastery, and recent interventions.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          APSLE now uses one adaptive coaching pipeline across sessions and tasks for each
          exercise.
        </p>
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
