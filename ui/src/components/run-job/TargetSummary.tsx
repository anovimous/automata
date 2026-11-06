import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { JobScopePill } from "@/components/job/pills";
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
      </CardContent>
    </Card>
  );
}
