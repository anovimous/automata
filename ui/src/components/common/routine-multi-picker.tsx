import { useRoutines } from "@/hooks/useResources";
import { Checkbox } from "@/components/ui/primitives";
import { Badge } from "@/components/ui/badge";

/** Choose several routines (checkbox list). */
export function RoutineMultiPicker({
  value,
  onChange,
}: {
  value: number[];
  onChange: (ids: number[]) => void;
}) {
  const routinesQ = useRoutines(undefined, undefined, { page: 0, size: 200 });
  const selected = new Set(value);

  function toggle(id: number) {
    onChange(selected.has(id) ? value.filter((v) => v !== id) : [...value, id]);
  }

  const routines = routinesQ.data?.content ?? [];

  return (
    <div className="max-h-44 overflow-y-auto rounded-md border border-border divide-y divide-border">
      {routines.map((r) => (
        <label
          key={r.id}
          className="flex items-center gap-2 px-2.5 py-1.5 text-[13px] cursor-pointer hover:bg-accent/40"
        >
          <Checkbox checked={selected.has(r.id)} onCheckedChange={() => toggle(r.id)} />
          <span className="font-mono text-xs flex-1 truncate">{r.key}</span>
          {r.wordlistRequired && <Badge variant="outline">wordlist</Badge>}
          {!r.isAvailableAtConsumer && (
            <Badge variant="warning" title="No worker runs it: an agent can't create jobs with it">
              unavailable
            </Badge>
          )}
        </label>
      ))}
      {routinesQ.data && routines.length === 0 && (
        <div className="px-2.5 py-3 text-xs text-muted-foreground">No routines.</div>
      )}
    </div>
  );
}
