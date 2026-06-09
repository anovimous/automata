import * as React from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch, Checkbox } from "@/components/ui/primitives";
import { useCreateRoutine, useVulnerabilities } from "@/hooks/useResources";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type {
  AllowedTarget,
  Overhead,
  Protocol,
} from "@/types/domain";

const TARGETS: AllowedTarget[] = [
  "SINGLE_HOST",
  "MULTILPLE_HOSTS",
  "SINGLE_REQUEST",
  "MULTIPLE_REQUESTS",
];

export function CreateRoutineDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const [key, setKey] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [overhead, setOverhead] = React.useState<Overhead | "">("");
  const [protocol, setProtocol] = React.useState<Protocol | "">("");
  const [vulnId, setVulnId] = React.useState<string>("");
  const [available, setAvailable] = React.useState(true);
  const [allowedTargets, setAllowedTargets] = React.useState<AllowedTarget[]>([]);

  const vulns = useVulnerabilities({ page: 0, size: 100 });
  const createMut = useCreateRoutine();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setKey("");
      setDescription("");
      setOverhead("");
      setProtocol("");
      setVulnId("");
      setAvailable(true);
      setAllowedTargets([]);
    }
  }, [open]);

  async function submit() {
    if (!key.trim()) return;
    try {
      await createMut.mutateAsync({
        key: key.trim(),
        description: description.trim() || undefined,
        overhead: overhead || undefined,
        protocol: protocol || undefined,
        vulnerabilityId: vulnId ? Number(vulnId) : undefined,
        isAvailableAtConsumer: available,
        allowedTargets: allowedTargets.length ? allowedTargets : undefined,
      });
      toast({ title: "Routine created", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Create failed";
      toast({ title: "Create failed", description: msg, variant: "destructive" });
    }
  }

  function toggle(t: AllowedTarget) {
    setAllowedTargets((cur) =>
      cur.includes(t) ? cur.filter((x) => x !== t) : [...cur, t],
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New routine</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="r-key">Key</Label>
            <Input
              id="r-key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="sql-injection-time-based"
              className="font-mono"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="r-desc">Description</Label>
            <Textarea
              id="r-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder=""
              className="min-h-[80px]"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Overhead</Label>
              <Select value={overhead} onValueChange={(v) => setOverhead(v as Overhead)}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="LOW">LOW</SelectItem>
                  <SelectItem value="MEDIUM">MEDIUM</SelectItem>
                  <SelectItem value="HIGH">HIGH</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Protocol</Label>
              <Select value={protocol} onValueChange={(v) => setProtocol(v as Protocol)}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HTTP">HTTP</SelectItem>
                  <SelectItem value="NETWORK">NETWORK</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Vulnerability</Label>
            <Select value={vulnId} onValueChange={setVulnId}>
              <SelectTrigger>
                <SelectValue placeholder="—" />
              </SelectTrigger>
              <SelectContent>
                {vulns.data?.content.map((v) => (
                  <SelectItem key={v.id} value={String(v.id)}>
                    {v.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>Allowed targets</Label>
            <div className="grid grid-cols-2 gap-1.5">
              {TARGETS.map((t) => (
                <label
                  key={t}
                  className="flex items-center gap-2 text-xs cursor-pointer font-mono"
                >
                  <Checkbox
                    checked={allowedTargets.includes(t)}
                    onCheckedChange={() => toggle(t)}
                  />
                  {t}
                </label>
              ))}
            </div>
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="r-avail">Available at consumer</Label>
            <Switch id="r-avail" checked={available} onCheckedChange={setAvailable} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!key.trim() || createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
