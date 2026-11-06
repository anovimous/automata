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
import { useHost, usePatchHost } from "@/hooks/useHosts";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { hostLimitsProblem } from "@/lib/hostRules";

export function EditHostDialog({
  open,
  onOpenChange,
  hostId,
  programId,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  hostId: number;
  programId: number;
}) {
  const { data } = useHost(open ? hostId : null);
  const patchMut = usePatchHost();
  const { toast } = useToast();

  const [scope, setScope] = React.useState<"WILDCARD" | "FQDN">("FQDN");
  const [rl, setRl] = React.useState("");
  const [srl, setSrl] = React.useState("");
  const [lrl, setLrl] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [technologies, setTechnologies] = React.useState("");
  const [notes, setNotes] = React.useState("");

  React.useEffect(() => {
    if (open && data) {
      setScope(data.scope);
      setRl(data.hostRateLimit?.toString() ?? "");
      setSrl(data.shortRateLimit?.toString() ?? "");
      setLrl(data.longRateLimit?.toString() ?? "");
      setDescription(data.description ?? "");
      setTechnologies((data.technologies ?? []).join(", "));
      setNotes(data.notes ?? "");
    }
  }, [open, data]);

  const limitsProblem = hostLimitsProblem(rl, srl, lrl);

  async function submit() {
    if (limitsProblem) return;
    try {
      await patchMut.mutateAsync({
        id: hostId,
        programId,
        hostName: data?.host,
        req: {
          scope,
          hostRateLimit: Number(rl),
          shortRateLimit: Number(srl),
          longRateLimit: Number(lrl),
          description,
          technologies: technologies
            .split(",")
            .map((t) => t.trim())
            .filter(Boolean),
          notes,
        },
      });
      toast({ title: "Host updated", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Update failed";
      toast({ title: "Update failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit host</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3 max-h-[70vh] overflow-y-auto">
          <div className="space-y-1.5">
            <Label htmlFor="host">Host</Label>
            <Input id="host" value={data?.host ?? ""} readOnly className="font-mono" />
            <div className="text-[11px] text-muted-foreground">
              The name of a host (and so its level) can't be changed.
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Scope</Label>
            <Select value={scope} onValueChange={(v) => setScope(v as "WILDCARD" | "FQDN")}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FQDN">FQDN</SelectItem>
                <SelectItem value="WILDCARD">Wildcard</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label>Host (req/s)</Label>
              <Input
                type="number"
                value={rl}
                onChange={(e) => setRl(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Short jobs (req/s)</Label>
              <Input
                type="number"
                value={srl}
                onChange={(e) => setSrl(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Long jobs (req/s)</Label>
              <Input
                type="number"
                value={lrl}
                onChange={(e) => setLrl(e.target.value)}
              />
            </div>
          </div>
          <div className="text-[11px] text-muted-foreground -mt-1">
            {limitsProblem ??
              "The limits can only change while no job acts on the host (its own jobs, or wide jobs of its program) other than drafts and ended ones: otherwise the core answers 409."}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="h-desc">Description</Label>
            <Textarea
              id="h-desc"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What this host is, shown to AI agents working on it."
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="h-tech">Technologies</Label>
            <Input
              id="h-tech"
              value={technologies}
              onChange={(e) => setTechnologies(e.target.value)}
              placeholder="nginx, Spring Boot, PostgreSQL"
            />
            <div className="text-[11px] text-muted-foreground">Comma separated.</div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="h-notes">Notes</Label>
            <Textarea
              id="h-notes"
              rows={3}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={patchMut.isPending || !!limitsProblem}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
