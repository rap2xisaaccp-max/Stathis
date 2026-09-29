'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { ExerciseTemplateResponseDTO } from '@/services/templates/api-template';
import {
  AssignedExercise,
  assignmentError,
  formatGoalTime,
  moveExercise,
} from '@/lib/tasks/task-exercises';
import { ArrowDown, ArrowUp, Loader2, Plus, Trash } from 'lucide-react';
import { toast } from 'sonner';
import { ExerciseDemonstrationControls } from './exercise-demonstration-controls';

type ExerciseAssignmentEditorProps = {
  templates: ExerciseTemplateResponseDTO[];
  loading?: boolean;
  value: AssignedExercise[];
  locked?: boolean;
  taskId?: string | null;
  onChange: (next: AssignedExercise[]) => void;
};

export function ExerciseAssignmentEditor({
  templates,
  loading,
  value,
  locked,
  taskId,
  onChange,
}: ExerciseAssignmentEditorProps) {
  const [pendingId, setPendingId] = useState<string>('');

  const addPending = () => {
    const template = templates.find((item) => item.physicalId === pendingId);
    if (!template) {
      toast.error('Choose an exercise template');
      return;
    }
    const candidate: AssignedExercise = {
      physicalId: template.physicalId,
      title: template.title,
      exerciseType: template.exerciseType,
      goalReps: template.goalReps,
      goalAccuracy: template.goalAccuracy,
      goalTime: template.goalTime,
    };
    const error = assignmentError(value, candidate);
    if (error) {
      toast.error(error);
      return;
    }
    onChange([...value, candidate]);
    setPendingId('');
  };

  return (
    <div className="space-y-3">
      <div className="text-lg font-semibold">Exercises</div>
      <p className="text-sm text-muted-foreground">
        Add one or more exercises. Each keeps its own reps, accuracy, and time goals.
      </p>
      {locked ? (
        <p className="text-sm text-amber-700">
          This task has started. The exercise list can no longer be changed.
        </p>
      ) : (
        <div className="flex flex-col gap-2 sm:flex-row">
          <Select value={pendingId} onValueChange={setPendingId} disabled={loading}>
            <SelectTrigger className="h-14 flex-1 rounded-2xl border-border/30 bg-background/60 text-base">
              {loading ? (
                <div className="flex items-center">
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Loading templates...
                </div>
              ) : (
                <SelectValue placeholder="Choose an exercise template" />
              )}
            </SelectTrigger>
            <SelectContent>
              {templates.length === 0 ? (
                <div className="p-4 text-center text-sm text-muted-foreground">No exercise templates yet</div>
              ) : (
                templates.map((template) => (
                  <SelectItem key={template.physicalId} value={template.physicalId}>
                    {template.title}
                  </SelectItem>
                ))
              )}
            </SelectContent>
          </Select>
          <Button type="button" variant="outline" className="h-14 rounded-2xl" onClick={addPending}>
            <Plus className="mr-2 h-4 w-4" />
            Add exercise
          </Button>
        </div>
      )}
      <div className="space-y-2">
        {value.length === 0 ? (
          <p className="text-sm text-muted-foreground">No exercises added yet.</p>
        ) : (
          value.map((item, index) => (
            <div
              key={`${item.physicalId}-${index}`}
              className="rounded-2xl border border-border/40 bg-background/60 p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-medium">
                    {index + 1}. {item.title}
                  </div>
                  <div className="mt-1 text-sm text-muted-foreground">
                    {item.goalReps} valid reps · {item.goalAccuracy}% accuracy · {formatGoalTime(item.goalTime)}
                  </div>
                </div>
                {!locked && (
                  <div className="flex gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={index === 0}
                      onClick={() => onChange(moveExercise(value, index, -1))}
                      title="Move up"
                    >
                      <ArrowUp className="h-4 w-4" />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={index === value.length - 1}
                      onClick={() => onChange(moveExercise(value, index, 1))}
                      title="Move down"
                    >
                      <ArrowDown className="h-4 w-4" />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => onChange(value.filter((_, itemIndex) => itemIndex !== index))}
                      title="Remove exercise"
                    >
                      <Trash className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>
              <ExerciseDemonstrationControls taskId={taskId} exerciseTemplateId={item.physicalId} />
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export function assignedFromTemplate(template: ExerciseTemplateResponseDTO): AssignedExercise {
  return {
    physicalId: template.physicalId,
    title: template.title,
    exerciseType: template.exerciseType,
    goalReps: template.goalReps,
    goalAccuracy: template.goalAccuracy,
    goalTime: template.goalTime,
  };
}
