import { Label, Textarea } from "@/components/ui/input";
import { cn } from "@/lib/cn";
import { MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH } from "@/lib/routineRules";

/** The required "usage description for agent" of a routine, with a counter against the core's limit. */
export function UsageDescriptionField({
  id,
  value,
  onChange,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const over = value.length > MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH;
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>Usage description for agent (required)</Label>
      <Textarea
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="When the agent should pick this routine, how to configure it and what to expect from its result."
        className="min-h-[140px]"
      />
      <div className="flex justify-between text-[11px] text-muted-foreground">
        <span>Only returned when the routine itself is read, not in the routine list.</span>
        <span className={cn("tabular-nums", over && "text-destructive")}>
          {value.length} / {MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH}
        </span>
      </div>
    </div>
  );
}
