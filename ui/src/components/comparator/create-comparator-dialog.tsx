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
import { Checkbox } from "@/components/ui/primitives";
import {
  useCreateComparator,
  useModifierDetails,
  useModifiers,
} from "@/hooks/useResources";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type { ComparatorSchema as ComparatorSchemaType } from "@/types/domain";

export function CreateComparatorDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const [name, setName] = React.useState("");
  const [schema, setSchema] = React.useState<ComparatorSchemaType | "">("");
  const [modifierIds, setModifierIds] = React.useState<number[]>([]);
  const createMut = useCreateComparator();
  const modifiers = useModifiers({ pageable: { page: 0, size: 100 } });
  const details = useModifierDetails((modifiers.data?.content ?? []).map((m) => m.id));
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setName("");
      setSchema("");
      setModifierIds([]);
    }
  }, [open]);

  // The core refuses a modifier which is not available (400), so only those are offered.
  const availableModifiers = (modifiers.data?.content ?? []).filter(
    (m) => details.get(m.id)?.isAvailable !== false,
  );

  async function submit() {
    if (!name.trim() || modifierIds.length === 0) return;
    try {
      await createMut.mutateAsync({
        name: name.trim(),
        schema: schema || undefined,
        modifierIds,
      });
      toast({ title: "Comparator created", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Create failed";
      toast({ title: "Create failed", description: msg, variant: "destructive" });
    }
  }

  function toggle(id: number) {
    setModifierIds((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New comparator</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label>Name</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} autoFocus />
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
          <div className="space-y-1.5">
            <Label>Modifiers (at least one)</Label>
            <div className="space-y-1 max-h-44 overflow-y-auto border border-border rounded-md p-2">
              {availableModifiers.length === 0 && (
                <div className="text-xs text-muted-foreground">No modifiers available.</div>
              )}
              {availableModifiers.map((m) => (
                <label
                  key={m.id}
                  className="flex items-center gap-2 text-xs cursor-pointer"
                >
                  <Checkbox
                    checked={modifierIds.includes(m.id)}
                    onCheckedChange={() => toggle(m.id)}
                  />
                  <span className="font-mono">{m.target}</span>
                  {details.get(m.id)?.description && (
                    <span className="text-muted-foreground truncate">
                      {details.get(m.id)?.description}
                    </span>
                  )}
                </label>
              ))}
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={submit}
            disabled={!name.trim() || modifierIds.length === 0 || createMut.isPending}
          >
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
