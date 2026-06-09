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
import { useCreateProgram } from "@/hooks/usePrograms";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function CreateProgramDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const [name, setName] = React.useState("");
  const [link, setLink] = React.useState("");
  const [platform, setPlatform] = React.useState<string>("");
  const [rate, setRate] = React.useState("");

  const createMut = useCreateProgram();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setName("");
      setLink("");
      setPlatform("");
      setRate("");
    }
  }, [open]);

  async function submit() {
    if (!name.trim()) return;
    try {
      await createMut.mutateAsync({
        name: name.trim(),
        link: link.trim() || undefined,
        platform: platform ? (platform as "BUGCROWD" | "HACKERONE" | "INTIGRITI") : undefined,
        programRateLimit: rate ? Number(rate) : undefined,
      });
      toast({ title: "Program created", variant: "success" });
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
          <DialogTitle>New program</DialogTitle>
          <DialogDescription>A program is the top-level scope above hosts.</DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="name">Name</Label>
            <Input
              id="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="AcmeCorp Public Program"
              autoFocus
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="link">Link</Label>
            <Input
              id="link"
              value={link}
              onChange={(e) => setLink(e.target.value)}
              placeholder="https://hackerone.com/acme"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Platform</Label>
              <Select value={platform} onValueChange={setPlatform}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HACKERONE">HackerOne</SelectItem>
                  <SelectItem value="BUGCROWD">Bugcrowd</SelectItem>
                  <SelectItem value="INTIGRITI">Intigriti</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="rate">Rate limit</Label>
              <Input
                id="rate"
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                placeholder="req/s"
                type="number"
              />
            </div>
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
