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
import { useCreateTenant } from "@/hooks/useTenants";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function CreateTenantDialog({
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
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const createMut = useCreateTenant();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setName("");
      setEmail("");
    }
  }, [open]);

  async function submit() {
    if (!name.trim()) return;
    try {
      await createMut.mutateAsync({
        name: name.trim(),
        email: email.trim() || undefined,
        hostId,
        programId,
        hostName,
      });
      toast({ title: "Tenant created", variant: "success" });
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
          <DialogTitle>New tenant</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="t-name">Name</Label>
            <Input
              id="t-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="alice-admin"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="t-email">Email</Label>
            <Input
              id="t-email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="alice@example.com"
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!name.trim() || createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
