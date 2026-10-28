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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useCreateFinding } from "@/hooks/useFindings";
import { useVulnerabilities } from "@/hooks/useResources";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type { FindingStatus } from "@/types/domain";

/** Report a finding by hand (the AI agent reports its own through the MCP server). */
export function CreateFindingDialog({
  open,
  onOpenChange,
  initialRequestId,
  initialJobId,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  initialRequestId?: number;
  initialJobId?: number;
  onCreated?: (id: number | null) => void;
}) {
  const [requestId, setRequestId] = React.useState("");
  const [jobId, setJobId] = React.useState("");
  const [vulnId, setVulnId] = React.useState("");
  const [severity, setSeverity] = React.useState("5");
  const [confidence, setConfidence] = React.useState("5");
  const [status, setStatus] = React.useState<FindingStatus>("SUGGESTED");
  const [evidence, setEvidence] = React.useState("");
  const [poc, setPoc] = React.useState("");

  const vulns = useVulnerabilities({ pageable: { page: 0, size: 200 } });
  const createMut = useCreateFinding();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setRequestId(initialRequestId ? String(initialRequestId) : "");
      setJobId(initialJobId ? String(initialJobId) : "");
      setVulnId("");
      setSeverity("5");
      setConfidence("5");
      setStatus("SUGGESTED");
      setEvidence("");
      setPoc("");
    }
  }, [open, initialRequestId, initialJobId]);

  const sev = Number(severity);
  const conf = Number(confidence);
  const problems: string[] = [];
  if (!requestId || !Number.isInteger(Number(requestId))) problems.push("A request id is required.");
  if (!vulnId) problems.push("Pick the vulnerability.");
  if (!(sev >= 0 && sev <= 10)) problems.push("Severity is between 0 and 10.");
  if (!(conf >= 0 && conf <= 10)) problems.push("Confidence is between 0 and 10.");
  if (status === "CONFIRMED" && !poc.trim())
    problems.push("A confirmed finding needs reproduction steps.");

  async function submit() {
    if (problems.length > 0) return;
    try {
      const id = await createMut.mutateAsync({
        requestId: Number(requestId),
        jobId: jobId ? Number(jobId) : undefined,
        vulnerabilityId: Number(vulnId),
        severity: sev,
        confidence: conf,
        status,
        evidence: evidence.trim() || undefined,
        pocReproductionSteps: poc.trim() || undefined,
        createdBy: "USER",
      });
      toast({ title: "Finding created", variant: "success" });
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
          <DialogTitle>Report a finding</DialogTitle>
          <DialogDescription>
            The host is taken from the request. Severity is CVSS (0–10).
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3 max-h-[70vh] overflow-y-auto">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Request id</Label>
              <Input
                value={requestId}
                onChange={(e) => setRequestId(e.target.value)}
                inputMode="numeric"
                className="font-mono"
              />
            </div>
            <div className="space-y-1.5">
              <Label>Job id (optional)</Label>
              <Input
                value={jobId}
                onChange={(e) => setJobId(e.target.value)}
                inputMode="numeric"
                className="font-mono"
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Vulnerability</Label>
            <Select value={vulnId} onValueChange={setVulnId}>
              <SelectTrigger>
                <SelectValue placeholder="Pick one" />
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
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label>Severity</Label>
              <Input
                type="number"
                min={0}
                max={10}
                step={0.1}
                value={severity}
                onChange={(e) => setSeverity(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Confidence</Label>
              <Input
                type="number"
                min={0}
                max={10}
                step={0.1}
                value={confidence}
                onChange={(e) => setConfidence(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Status</Label>
              <Select value={status} onValueChange={(v) => setStatus(v as FindingStatus)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="SUGGESTED">Suggested</SelectItem>
                  <SelectItem value="CONFIRMED">Confirmed</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Evidence</Label>
            <Textarea value={evidence} onChange={(e) => setEvidence(e.target.value)} rows={3} />
          </div>
          <div className="space-y-1.5">
            <Label>Reproduction steps{status === "CONFIRMED" ? " (required)" : ""}</Label>
            <Textarea value={poc} onChange={(e) => setPoc(e.target.value)} rows={4} />
          </div>
          {problems.length > 0 && (
            <ul className="text-[11px] text-muted-foreground list-disc pl-4 space-y-0.5">
              {problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={problems.length > 0 || createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
