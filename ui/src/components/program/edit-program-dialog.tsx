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
import { VulnerabilityMultiPicker } from "@/components/common/vulnerability-picker";
import { usePatchProgram, useProgram } from "@/hooks/usePrograms";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type { Platform } from "@/types/domain";

const NO_PLATFORM = "__none__";

export function EditProgramDialog({
  open,
  onOpenChange,
  programId,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  programId: number;
}) {
  const { data } = useProgram(open ? programId : null);
  const patchMut = usePatchProgram();
  const { toast } = useToast();

  const [name, setName] = React.useState("");
  const [link, setLink] = React.useState("");
  const [platform, setPlatform] = React.useState<string>(NO_PLATFORM);
  const [rate, setRate] = React.useState("");
  const [outOfScope, setOutOfScope] = React.useState<number[]>([]);

  React.useEffect(() => {
    if (open && data) {
      setName(data.name);
      setLink(data.link ?? "");
      setPlatform(data.platform ?? NO_PLATFORM);
      setRate(data.programRateLimit?.toString() ?? "");
      setOutOfScope(data.outOfScopeVulnIds ?? []);
    }
  }, [open, data]);

  const rateInvalid =
    rate.trim() !== "" && (!Number.isInteger(Number(rate)) || Number(rate) < 0);

  async function submit() {
    if (!data || rateInvalid) return;
    const trimmedLink = link.trim();
    try {
      await patchMut.mutateAsync({
        id: programId,
        req: {
          name: name.trim() || undefined,
          link: trimmedLink || undefined,
          setLinkNull: !trimmedLink && !!data.link,
          platform: platform === NO_PLATFORM ? undefined : (platform as Platform),
          setPlatformNull: platform === NO_PLATFORM && !!data.platform,
          programRateLimit: rate.trim() ? Number(rate) : undefined,
          outOfScopeVulnIds: outOfScope,
        },
      });
      toast({ title: "Program updated", variant: "success" });
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
          <DialogTitle>Edit program</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3 max-h-[70vh] overflow-y-auto">
          <div className="space-y-1.5">
            <Label htmlFor="p-name">Name</Label>
            <Input id="p-name" value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="p-link">Link</Label>
            <Input id="p-link" value={link} onChange={(e) => setLink(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Platform</Label>
              <Select value={platform} onValueChange={setPlatform}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_PLATFORM}>—</SelectItem>
                  <SelectItem value="HACKERONE">HackerOne</SelectItem>
                  <SelectItem value="BUGCROWD">Bugcrowd</SelectItem>
                  <SelectItem value="INTIGRITI">Intigriti</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="p-rate">Rate limit (req/s)</Label>
              <Input
                id="p-rate"
                type="number"
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                min={0}
                step={1}
              />
              <div className={`text-[11px] ${rateInvalid ? "text-destructive" : "text-muted-foreground"}`}>
                {rateInvalid
                  ? "A whole number of 0 or more."
                  : "Can only change while no job of the program is in progress (anything but a draft or an ended job): otherwise the core answers 409."}
              </div>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>Out-of-scope vulnerabilities</Label>
            <VulnerabilityMultiPicker value={outOfScope} onChange={setOutOfScope} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!name.trim() || rateInvalid || patchMut.isPending}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
