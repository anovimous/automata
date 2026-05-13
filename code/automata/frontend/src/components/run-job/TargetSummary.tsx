import { AlertTriangle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { JobScopePill } from "@/components/job/pills";
import { cn } from "@/lib/cn";
import type { InferredScope } from "@/lib/inferScope";

export function TargetSummary({ scope }: { scope: InferredScope }) {
  return (
    <Card>
      <CardContent className="p-4 space-y-3">
        <div className="flex items-center gap-2 flex-wrap">
          <JobScopePill scope={scope.scope} />
          <Badge variant="outline">{scope.selectionLabel}</Badge>
        </div>
        <div className="text-sm">{scope.summary}</div>

        {!scope.backendReady && (
          <div
            className={cn(
              "flex items-start gap-2 rounded-md border border-warning/30 bg-warning/10 px-3 py-2",
            )}
          >
            <AlertTriangle className="h-3.5 w-3.5 text-warning shrink-0 mt-0.5" />
            <div className="text-xs space-y-1">
              <div className="font-medium text-warning">Backend not yet ready</div>
              <div className="text-muted-foreground">
                This selector type is currently stubbed in the backend. You can fill out
                the form but submission is disabled.
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
