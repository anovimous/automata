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
import { hostLimitsProblem } from "@/lib/hostRules";
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

  const limitsProblem = hostLimitsProblem(rateLimit, shortRl, longRl);

  async function submit() {
    if (!host.trim() || limitsProblem) return;
    try {
      await createMut.mutateAsync({
        host: host.trim(),
        scope,
        programId,
        hostRateLimit: Number(rateLimit),
        shortRateLimit: Number(shortRl),
        longRateLimit: Number(longRl),
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
              placeholder="api.example.com"
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
          <div className="text-[11px] text-muted-foreground -mt-1">
            A bare domain name (it needs a dot, no <code>*.</code> prefix): for a wildcard enter{" "}
            <code>example.com</code> and pick the Wildcard scope. The name can't be changed later.
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="rl">Host (req/s)</Label>
              <Input
                id="rl"
                type="number"
                value={rateLimit}
                onChange={(e) => setRateLimit(e.target.value)}
                placeholder="0"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="srl">Short jobs (req/s)</Label>
              <Input
                id="srl"
                type="number"
                value={shortRl}
                onChange={(e) => setShortRl(e.target.value)}
                placeholder="0"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="lrl">Long jobs (req/s)</Label>
              <Input
                id="lrl"
                type="number"
                value={longRl}
                onChange={(e) => setLongRl(e.target.value)}
                placeholder="0"
              />
            </div>
          </div>
          <div className="text-[11px] text-muted-foreground">
            {limitsProblem ??
              "The host rate must cover the short plus the long job rate. A host with a rate of 0 can't run jobs."}
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={submit}
            disabled={!host.trim() || !!limitsProblem || createMut.isPending}
          >
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
