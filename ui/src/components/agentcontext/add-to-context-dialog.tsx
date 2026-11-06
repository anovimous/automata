import * as React from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAddContextItem, useAgentContexts } from "@/hooks/useAgentContexts";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

/** Add requests (REQUEST items) to one of the agent contexts of a host. */
export function AddToContextDialog({
  open,
  onOpenChange,
  hostId,
  requestIds,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  hostId: number;
  requestIds: number[];
}) {
  const contextsQ = useAgentContexts(open ? hostId : undefined, { page: 0, size: 100 });
  const addMut = useAddContextItem();
  const { toast } = useToast();
  const [contextId, setContextId] = React.useState("");
  const [busy, setBusy] = React.useState(false);

  React.useEffect(() => {
    if (open) setContextId("");
  }, [open]);

  async function submit() {
    if (!contextId) return;
    setBusy(true);
    let added = 0;
    const failures: string[] = [];
    for (const requestId of requestIds) {
      try {
        await addMut.mutateAsync({
          id: Number(contextId),
          req: { itemType: "REQUEST", requestId },
        });
        added++;
      } catch (e) {
        failures.push(`#${requestId}: ${e instanceof ApiError ? e.message : "failed"}`);
      }
    }
    setBusy(false);
    toast({
      title: `Added ${added} of ${requestIds.length}`,
      description: failures.length > 0 ? failures.slice(0, 3).join("\n") : undefined,
      variant: failures.length > 0 ? "warning" : "success",
    });
    if (failures.length === 0) onOpenChange(false);
  }

  const contexts = contextsQ.data?.content ?? [];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add to agent context</DialogTitle>
          <DialogDescription>
            The AI agent working in that context can read these requests and create jobs on them.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-1.5">
          <Label>Context</Label>
          <Select value={contextId} onValueChange={setContextId}>
            <SelectTrigger>
              <SelectValue placeholder="Pick a context" />
            </SelectTrigger>
            <SelectContent>
              {contexts.map((c) => (
                <SelectItem key={c.id} value={String(c.id)}>
                  {c.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {contextsQ.data && contexts.length === 0 && (
            <div className="text-xs text-muted-foreground">
              This host has no agent context yet. Create one under Agent contexts.
            </div>
          )}
          <div className="text-xs text-muted-foreground pt-1">
            {requestIds.length} request{requestIds.length === 1 ? "" : "s"} selected.
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!contextId || busy || requestIds.length === 0}>
            Add
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
