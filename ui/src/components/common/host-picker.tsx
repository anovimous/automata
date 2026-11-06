import * as React from "react";
import { useHosts } from "@/hooks/useHosts";
import { usePrograms } from "@/hooks/usePrograms";
import { Label } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

/** Pick a host: first its program, then the host. Reports the host id (or null). */
export function HostPicker({
  value,
  onChange,
  disabled,
}: {
  value: number | null;
  onChange: (hostId: number | null) => void;
  disabled?: boolean;
}) {
  const [programId, setProgramId] = React.useState<number | null>(null);
  const programsQ = usePrograms(undefined, { page: 0, size: 200 });
  const hostsQ = useHosts(programId, undefined, { page: 0, size: 200 });

  return (
    <div className="grid grid-cols-2 gap-3">
      <div className="space-y-1.5">
        <Label>Program</Label>
        <Select
          value={programId !== null ? String(programId) : ""}
          onValueChange={(v) => {
            setProgramId(Number(v));
            onChange(null);
          }}
          disabled={disabled}
        >
          <SelectTrigger>
            <SelectValue placeholder="Pick a program" />
          </SelectTrigger>
          <SelectContent>
            {programsQ.data?.content.map((p) => (
              <SelectItem key={p.id} value={String(p.id)}>
                {p.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label>Host</Label>
        <Select
          value={value !== null ? String(value) : ""}
          onValueChange={(v) => onChange(Number(v))}
          disabled={disabled || programId === null}
        >
          <SelectTrigger>
            <SelectValue placeholder={programId === null ? "Pick a program first" : "Pick a host"} />
          </SelectTrigger>
          <SelectContent>
            {hostsQ.data?.content.map((h) => (
              <SelectItem key={h.id} value={String(h.id)}>
                {h.host}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
