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
import { useCreateWordlist, useVulnerabilities } from "@/hooks/useResources";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function CreateWordlistDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const [name, setName] = React.useState("");
  const [path, setPath] = React.useState("");
  const [vulnId, setVulnId] = React.useState("");
  const createMut = useCreateWordlist();
  const vulns = useVulnerabilities({ page: 0, size: 200 });
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setName("");
      setPath("");
      setVulnId("");
    }
  }, [open]);

  async function submit() {
    if (!name.trim() || !path.trim()) return;
    try {
      await createMut.mutateAsync({
        name: name.trim(),
        path: path.trim(),
        vulnerabilityId: vulnId ? Number(vulnId) : undefined,
      });
      toast({ title: "Wordlist created", variant: "success" });
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
          <DialogTitle>New wordlist</DialogTitle>
          <DialogDescription>
            Wordlists are pointers to files already present in the consumer environment.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label>Name</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="common-paths"
              className="font-mono"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label>Path</Label>
            <Input
              value={path}
              onChange={(e) => setPath(e.target.value)}
              placeholder="/wordlists/common.txt"
              className="font-mono"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Vulnerability (optional)</Label>
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
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={submit}
            disabled={!name.trim() || !path.trim() || createMut.isPending}
          >
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
