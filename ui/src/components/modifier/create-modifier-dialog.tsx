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
import { Switch } from "@/components/ui/primitives";
import { useCreateModifier } from "@/hooks/useResources";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type {
  ComparatorSchema as ComparatorSchemaType,
  ModifierTarget,
} from "@/types/domain";

export function CreateModifierDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const [target, setTarget] = React.useState<ModifierTarget>("PATH");
  const [description, setDescription] = React.useState("");
  const [priority, setPriority] = React.useState<string>("");
  const [schema, setSchema] = React.useState<ComparatorSchemaType | "">("");
  const [available, setAvailable] = React.useState(true);

  const createMut = useCreateModifier();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setTarget("PATH");
      setDescription("");
      setPriority("");
      setSchema("");
      setAvailable(true);
    }
  }, [open]);

  async function submit() {
    try {
      await createMut.mutateAsync({
        target,
        description: description.trim() || undefined,
        priority: priority ? Number(priority) : undefined,
        schema: schema || undefined,
        available,
      });
      toast({ title: "Modifier created", variant: "success" });
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
          <DialogTitle>New modifier</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label>Target</Label>
            <Select value={target} onValueChange={(v) => setTarget(v as ModifierTarget)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="METHOD">METHOD</SelectItem>
                <SelectItem value="PATH">PATH</SelectItem>
                <SelectItem value="QUERYSTRING">QUERYSTRING</SelectItem>
                <SelectItem value="BODY">BODY</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>Description</Label>
            <Textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="min-h-[80px]"
              placeholder="e.g. ignores trailing slashes"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Priority</Label>
              <Input
                type="number"
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                placeholder="—"
              />
            </div>
            <div className="space-y-1.5">
              <Label>Schema</Label>
              <Select value={schema} onValueChange={(v) => setSchema(v as ComparatorSchemaType)}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="REST">REST</SelectItem>
                  <SelectItem value="GRAPHQL">GRAPHQL</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="flex items-center justify-between pt-2">
            <Label>Available at consumer</Label>
            <Switch checked={available} onCheckedChange={setAvailable} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
