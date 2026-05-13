import * as React from "react";
import { Check, ChevronsUpDown, Search } from "lucide-react";
import { useRoutines } from "@/hooks/useResources";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/primitives";
import { cn } from "@/lib/cn";
import type { AllowedTarget, RoutineSummary } from "@/types/domain";

interface RoutinePickerProps {
  value: number | null;
  onChange: (id: number | null, routine: RoutineSummary | null) => void;
  /**
   * Filter to routines whose allowedTargets include this value.
   * Null = show all (used for GLOBAL jobs).
   */
  allowedTarget: AllowedTarget | null;
}

export function RoutinePicker({ value, onChange, allowedTarget }: RoutinePickerProps) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const [debouncedQuery, setDebouncedQuery] = React.useState("");

  React.useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(t);
  }, [query]);

  const { data } = useRoutines(debouncedQuery || undefined, undefined, {
    page: 0,
    size: 50,
  });

  // Client-side filter by allowedTarget
  const items = React.useMemo(() => {
    const all = data?.content ?? [];
    if (!allowedTarget) return all;
    return all.filter((r) => r.allowedTargets?.includes(allowedTarget));
  }, [data, allowedTarget]);

  const selected = items.find((r) => r.id === value) ?? null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between h-9"
        >
          {selected ? (
            <span className="flex items-center gap-2 min-w-0">
              <span className="font-mono text-xs font-medium truncate">{selected.key}</span>
              {selected.overhead && (
                <Badge variant="muted" className="shrink-0">
                  {selected.overhead}
                </Badge>
              )}
            </span>
          ) : (
            <span className="text-muted-foreground">Pick a routine…</span>
          )}
          <ChevronsUpDown className="h-3.5 w-3.5 opacity-50 shrink-0" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[420px] p-0" align="start">
        <div className="p-2 border-b border-border">
          <div className="relative">
            <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search routines…"
              className="pl-8"
            />
          </div>
        </div>
        <div className="max-h-72 overflow-y-auto p-1">
          {items.length === 0 && (
            <div className="p-3 text-xs text-muted-foreground text-center">
              {allowedTarget
                ? `No routines support ${allowedTarget}`
                : "No routines"}
            </div>
          )}
          {items.map((r) => {
            const isSelected = r.id === value;
            return (
              <button
                key={r.id}
                type="button"
                onClick={() => {
                  onChange(r.id, r);
                  setOpen(false);
                }}
                className={cn(
                  "w-full text-left flex items-start gap-2 rounded-sm px-2 py-1.5 hover:bg-accent transition-colors",
                  isSelected && "bg-accent",
                )}
              >
                <Check
                  className={cn("h-3.5 w-3.5 mt-0.5 shrink-0", !isSelected && "opacity-0")}
                />
                <div className="flex-1 min-w-0">
                  <div className="font-mono text-xs font-medium truncate">{r.key}</div>
                  {r.description && (
                    <div className="text-[11px] text-muted-foreground truncate mt-0.5">
                      {r.description}
                    </div>
                  )}
                  <div className="flex items-center gap-1 mt-1 flex-wrap">
                    {r.overhead && <Badge variant="muted">{r.overhead}</Badge>}
                    {r.protocol && <Badge variant="outline">{r.protocol}</Badge>}
                    {r.allowedTargets?.map((at) => (
                      <Badge key={at} variant="outline">
                        {at}
                      </Badge>
                    ))}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}
