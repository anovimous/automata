import * as React from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
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

  const [host, setHost] = React.useState("");
  const [scope, setScope] = React.useState<"WILDCARD" | "FQDN">("FQDN");
  const [rl, setRl] = React.useState("");
  const [srl, setSrl] = React.useState("");
  const [lrl, setLrl] = React.useState("");

  React.useEffect(() => {
    if (open && data) {
      setHost(data.host);
      setScope(data.scope);
      setRl(data.hostRateLimit?.toString() ?? "");
      setSrl(data.shortRateLimit?.toString() ?? "");
      setLrl(data.longRateLimit?.toString() ?? "");
    }
  }, [open, data]);

  async function submit() {
    try {
      await patchMut.mutateAsync({
        id: hostId,
        programId,
        req: {
          host: host.trim() || undefined,
          scope,
          hostRateLimit: rl ? Number(rl) : undefined,
          shortRateLimit: srl ? Number(srl) : undefined,
          longRateLimit: lrl ? Number(lrl) : undefined,
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
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="host">Host</Label>
            <Input
              id="host"
              value={host}
              onChange={(e) => setHost(e.target.value)}
              className="font-mono"
            />
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
              <Label>Rate (req/s)</Label>
              <Input type="number" value={rl} onChange={(e) => setRl(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>Short burst</Label>
              <Input type="number" value={srl} onChange={(e) => setSrl(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>Long burst</Label>
              <Input type="number" value={lrl} onChange={(e) => setLrl(e.target.value)} />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={patchMut.isPending}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
