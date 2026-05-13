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
import { Label, Textarea } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAddRawRequest } from "@/hooks/useRequests";
import { useTenants } from "@/hooks/useTenants";
import { useToast } from "@/components/ui/toast";
import { toBase64 } from "@/lib/utils";
import { ApiError } from "@/lib/fetcher";

export function AddRawRequestDialog({
  open,
  onOpenChange,
  hostId,
  programId,
  hostName,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  hostId: number;
  programId?: number;
  hostName?: string;
}) {
  const [rawReq, setRawReq] = React.useState("");
  const [rawRes, setRawRes] = React.useState("");
  const [source, setSource] = React.useState<"MANUAL" | "WAYBACK">("MANUAL");
  const [tenantId, setTenantId] = React.useState<string>("");

  const tenants = useTenants(open ? hostId : null);
  const addMut = useAddRawRequest();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setRawReq("");
      setRawRes("");
      setSource("MANUAL");
      setTenantId("");
    }
  }, [open]);

  async function submit() {
    if (!rawReq.trim()) return;
    try {
      await addMut.mutateAsync({
        hostId,
        source,
        tenantId: tenantId ? Number(tenantId) : undefined,
        requestBase64: toBase64(rawReq),
        responseBase64: rawRes.trim() ? toBase64(rawRes) : undefined,
        programId,
        hostName,
      });
      toast({ title: "Request added", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Add failed";
      toast({ title: "Add failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Add raw request</DialogTitle>
          <DialogDescription>
            Paste the full HTTP request. Response is optional.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Source</Label>
              <Select value={source} onValueChange={(v) => setSource(v as "MANUAL" | "WAYBACK")}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="MANUAL">Manual</SelectItem>
                  <SelectItem value="WAYBACK">Wayback</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Tenant (optional)</Label>
              <Select value={tenantId} onValueChange={setTenantId}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  {tenants.data?.content.map((t) => (
                    <SelectItem key={t.id} value={String(t.id)}>
                      {t.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Request</Label>
            <Textarea
              value={rawReq}
              onChange={(e) => setRawReq(e.target.value)}
              placeholder={"GET /api/v1/users HTTP/1.1\nHost: api.example.com\n…"}
              className="min-h-[160px] text-[12px]"
            />
          </div>

          <div className="space-y-1.5">
            <Label>Response (optional)</Label>
            <Textarea
              value={rawRes}
              onChange={(e) => setRawRes(e.target.value)}
              placeholder={"HTTP/1.1 200 OK\nContent-Type: application/json\n…"}
              className="min-h-[120px] text-[12px]"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!rawReq.trim() || addMut.isPending}>
            Add request
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
