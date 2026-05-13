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
import { Input, Label } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useCreateHost } from "@/hooks/useHosts";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function CreateHostDialog({
  open,
  onOpenChange,
  programId,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  programId: number;
}) {
  const [host, setHost] = React.useState("");
  const [scope, setScope] = React.useState<"WILDCARD" | "FQDN">("FQDN");
  const [rateLimit, setRateLimit] = React.useState("");
  const [shortRl, setShortRl] = React.useState("");
  const [longRl, setLongRl] = React.useState("");

  const createMut = useCreateHost();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setHost("");
      setScope("FQDN");
      setRateLimit("");
      setShortRl("");
      setLongRl("");
    }
  }, [open]);

  async function submit() {
    if (!host.trim()) return;
    try {
      await createMut.mutateAsync({
        host: host.trim(),
        scope,
        programId,
        hostRateLimit: rateLimit ? Number(rateLimit) : undefined,
        shortRateLimit: shortRl ? Number(shortRl) : undefined,
        longRateLimit: longRl ? Number(longRl) : undefined,
      });
      toast({ title: "Host created", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Create failed";
      toast({ title: "Create failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New host</DialogTitle>
          <DialogDescription>
            A host is a target domain (FQDN) or wildcard within this program.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="host">Host</Label>
            <Input
              id="host"
              value={host}
              onChange={(e) => setHost(e.target.value)}
              placeholder="api.example.com  or  *.example.com"
              className="font-mono"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label>Scope</Label>
            <Select value={scope} onValueChange={(v) => setScope(v as "WILDCARD" | "FQDN")}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FQDN">FQDN (single host)</SelectItem>
                <SelectItem value="WILDCARD">Wildcard (subdomain)</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="rl">Rate (req/s)</Label>
              <Input
                id="rl"
                type="number"
                value={rateLimit}
                onChange={(e) => setRateLimit(e.target.value)}
                placeholder="—"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="srl">Short burst</Label>
              <Input
                id="srl"
                type="number"
                value={shortRl}
                onChange={(e) => setShortRl(e.target.value)}
                placeholder="—"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="lrl">Long burst</Label>
              <Input
                id="lrl"
                type="number"
                value={longRl}
                onChange={(e) => setLongRl(e.target.value)}
                placeholder="—"
              />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!host.trim() || createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
