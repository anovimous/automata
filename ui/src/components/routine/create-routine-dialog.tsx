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
import { MonacoJsonEditor } from "@/components/run-job/MonacoJsonEditor";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type {
  AllowedTarget,
  Overhead,
  Protocol,
  ResultContentType,
} from "@/types/domain";

const NO_VULN = "__none__";

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
  const [vulnId, setVulnId] = React.useState<string>(NO_VULN);
  const [resultContentType, setResultContentType] = React.useState<ResultContentType>("JSON");
  const [configSchema, setConfigSchema] = React.useState("");
  const [available, setAvailable] = React.useState(true);
  const [allowedTargets, setAllowedTargets] = React.useState<AllowedTarget[]>([]);

  const vulns = useVulnerabilities({ pageable: { page: 0, size: 200 } });
  const createMut = useCreateRoutine();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setKey("");
      setDescription("");
      setOverhead("");
      setProtocol("");
      setVulnId(NO_VULN);
      setResultContentType("JSON");
      setConfigSchema("");
      setAvailable(true);
      setAllowedTargets([]);
    }
  }, [open]);

  let schemaError: string | null = null;
  if (configSchema.trim()) {
    try {
      JSON.parse(configSchema);
    } catch (e) {
      schemaError = e instanceof Error ? e.message : "Invalid JSON";
    }
  }

  async function submit() {
    if (!key.trim() || schemaError) return;
    try {
      await createMut.mutateAsync({
        key: key.trim(),
        description: description.trim() || undefined,
        overhead: overhead || undefined,
        protocol: protocol || undefined,
        vulnerabilityId: vulnId !== NO_VULN ? Number(vulnId) : undefined,
        resultContentType,
        configSchema: configSchema.trim() ? JSON.parse(configSchema) : undefined,
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
        <div className="px-5 py-4 space-y-3 max-h-[70vh] overflow-y-auto">
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
                <SelectItem value={NO_VULN}>—</SelectItem>
                {vulns.data?.content.map((v) => (
                  <SelectItem key={v.id} value={String(v.id)}>
                    {v.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>Result content type</Label>
            <Select
              value={resultContentType}
              onValueChange={(v) => setResultContentType(v as ResultContentType)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="JSON">JSON</SelectItem>
                <SelectItem value="TEXT">TEXT</SelectItem>
              </SelectContent>
            </Select>
            <div className="text-[11px] text-muted-foreground">
              How the job result is served when viewed inline.
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Config schema (JSON Schema, optional)</Label>
            <MonacoJsonEditor value={configSchema} onChange={setConfigSchema} height="160px" />
            {schemaError ? (
              <div className="text-[11px] text-destructive">{schemaError}</div>
            ) : (
              <div className="text-[11px] text-muted-foreground">
                The custom config of every job of this routine is validated against it.
              </div>
            )}
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
          <Button
            onClick={submit}
            disabled={!key.trim() || !!schemaError || createMut.isPending}
          >
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
