import { contextDefaultsProblem } from "@/lib/jobRules";
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
import { Input, Label, Textarea } from "@/components/ui/input";
import { Switch, Checkbox } from "@/components/ui/primitives";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { HostPicker } from "@/components/common/host-picker";
import { RoutineMultiPicker } from "@/components/common/routine-multi-picker";
import { useCreateAgentContext } from "@/hooks/useAgentContexts";
import { useTenants } from "@/hooks/useTenants";
import { useHost } from "@/hooks/useHosts";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type { Duration, RepresentativeStrategy, Verbosity } from "@/types/domain";

const NONE = "__none__";

export function CreateAgentContextDialog({
  open,
  onOpenChange,
  initialHostId,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  initialHostId?: number;
  onCreated?: (id: number | null) => void;
}) {
  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [hostId, setHostId] = React.useState<number | null>(null);
  const [autoApproval, setAutoApproval] = React.useState(false);
  const [strategy, setStrategy] = React.useState<RepresentativeStrategy>("LATEST");
  const [routineIds, setRoutineIds] = React.useState<number[]>([]);
  const [tenantIds, setTenantIds] = React.useState<number[]>([]);
  const [duration, setDuration] = React.useState<string>(NONE);
  const [verbosity, setVerbosity] = React.useState<string>(NONE);
  const [priority, setPriority] = React.useState("");
  const [maxRate, setMaxRate] = React.useState("");
  const [defaultJobRate, setDefaultJobRate] = React.useState("");

  const createMut = useCreateAgentContext();
  const { toast } = useToast();
  const initialHostQ = useHost(open ? (initialHostId ?? null) : null);
  const tenantsQ = useTenants(hostId, { page: 0, size: 100 });

  React.useEffect(() => {
    if (open) {
      setName("");
      setDescription("");
      setHostId(initialHostId ?? null);
      setAutoApproval(false);
      setStrategy("LATEST");
      setRoutineIds([]);
      setTenantIds([]);
      setDuration(NONE);
      setVerbosity(NONE);
      setPriority("");
      setMaxRate("");
      setDefaultJobRate("");
    }
  }, [open, initialHostId]);

  // tenants belong to the host: forget the picked ones when the host changes
  React.useEffect(() => setTenantIds([]), [hostId]);

  const defaultsProblem = contextDefaultsProblem(maxRate, defaultJobRate, priority);

  async function submit() {
    if (!name.trim() || hostId === null || defaultsProblem) return;
    try {
      const id = await createMut.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        hostId,
        defaultAutoApproval: autoApproval,
        representativeStrategy: strategy,
        allowedRoutineIds: routineIds,
        tenantIds,
        defaultDuration: duration === NONE ? undefined : (duration as Duration),
        defaultVerbosity: verbosity === NONE ? undefined : (verbosity as Verbosity),
        defaultPriority: priority ? Number(priority) : undefined,
        maxRate: Number(maxRate),
        defaultJobRate: Number(defaultJobRate),
      });
      toast({ title: "Agent context created", variant: "success" });
      onOpenChange(false);
      onCreated?.(id);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Create failed";
      toast({ title: "Create failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>New agent context</DialogTitle>
          <DialogDescription>
            A context scopes an AI agent to one host: the requests and equality sets it may work
            on, the routines it may run and the tenants it may use. Give its UUID to the agent.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3 max-h-[70vh] overflow-y-auto">
          <div className="space-y-1.5">
            <Label htmlFor="ctx-name">Name</Label>
            <Input
              id="ctx-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ctx-desc">Description</Label>
            <Textarea
              id="ctx-desc"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What the agent should focus on."
            />
          </div>
          {initialHostId !== undefined ? (
            <div className="space-y-1.5">
              <Label>Host</Label>
              <div className="font-mono text-sm">
                {initialHostQ.data?.host ?? `#${initialHostId}`}
              </div>
            </div>
          ) : (
            <HostPicker value={hostId} onChange={setHostId} />
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Representative of an equality set</Label>
              <Select
                value={strategy}
                onValueChange={(v) => setStrategy(v as RepresentativeStrategy)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="LATEST">Latest request</SelectItem>
                  <SelectItem value="AI_JUDGEMENT">Agent chooses</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-end justify-between gap-2 pb-1.5">
              <Label htmlFor="ctx-auto">Auto-approve jobs</Label>
              <Switch id="ctx-auto" checked={autoApproval} onCheckedChange={setAutoApproval} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Allowed routines</Label>
            <RoutineMultiPicker value={routineIds} onChange={setRoutineIds} />
          </div>
          <div className="space-y-1.5">
            <Label>Tenants</Label>
            {hostId === null ? (
              <div className="text-xs text-muted-foreground">Pick a host first.</div>
            ) : (
              <div className="max-h-32 overflow-y-auto rounded-md border border-border divide-y divide-border">
                {tenantsQ.data?.content.map((t) => (
                  <label
                    key={t.id}
                    className="flex items-center gap-2 px-2.5 py-1.5 text-[13px] cursor-pointer hover:bg-accent/40"
                  >
                    <Checkbox
                      checked={tenantIds.includes(t.id)}
                      onCheckedChange={() =>
                        setTenantIds((cur) =>
                          cur.includes(t.id) ? cur.filter((x) => x !== t.id) : [...cur, t.id],
                        )
                      }
                    />
                    {t.name}
                  </label>
                ))}
                {tenantsQ.data && tenantsQ.data.content.length === 0 && (
                  <div className="px-2.5 py-3 text-xs text-muted-foreground">
                    This host has no tenants.
                  </div>
                )}
              </div>
            )}
          </div>
          <div className="text-[10px] uppercase tracking-wider text-muted-foreground pt-1">
            Defaults for the jobs the agent creates
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Duration</Label>
              <Select value={duration} onValueChange={setDuration}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>—</SelectItem>
                  <SelectItem value="SHORT">Short</SelectItem>
                  <SelectItem value="LONG">Long</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Verbosity</Label>
              <Select value={verbosity} onValueChange={setVerbosity}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>— (summarized)</SelectItem>
                  <SelectItem value="SUMMARIZED">Summarized</SelectItem>
                  <SelectItem value="NORMAL">Normal</SelectItem>
                  <SelectItem value="VERBOSE">Verbose</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Priority (1-5)</Label>
              <Input
                type="number"
                min={1}
                max={5}
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                placeholder="1"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ctx-max-rate">Max rate (req/s)</Label>
              <Input
                id="ctx-max-rate"
                type="number"
                min={1}
                value={maxRate}
                onChange={(e) => setMaxRate(e.target.value)}
                placeholder="required"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ctx-default-rate">Default job rate (req/s)</Label>
              <Input
                id="ctx-default-rate"
                type="number"
                min={1}
                value={defaultJobRate}
                onChange={(e) => setDefaultJobRate(e.target.value)}
                placeholder="required, at most the max rate"
              />
            </div>
          </div>
          {defaultsProblem && maxRate !== "" && defaultJobRate !== "" && (
            <div className="text-[11px] text-destructive">{defaultsProblem}</div>
          )}
          <div className="text-[11px] text-muted-foreground">
            A job needs a duration: set a default here, or the agent must give one each time. A
            rate the agent gives is refused above the max rate and above the limits of the host,
            the program and the duration; without one the job gets the default job rate, lowered
            to those limits.
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={submit}
            disabled={
              !name.trim() || hostId === null || !!defaultsProblem || createMut.isPending
            }
          >
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
