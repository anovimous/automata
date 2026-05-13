import * as React from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { MatchAndReplace } from "@/types/domain";

interface MRRulesEditorProps {
  rules: MatchAndReplace[];
  onChange: (next: MatchAndReplace[]) => void;
}

const RULE_OPTIONS: MatchAndReplace["rule"][] = ["ADD", "REPLACE", "DELETE"];

export function MRRulesEditor({ rules, onChange }: MRRulesEditorProps) {
  function update(i: number, patch: Partial<MatchAndReplace>) {
    const next = [...rules];
    next[i] = { ...next[i], ...patch };
    onChange(next);
  }

  function add() {
    onChange([
      ...rules,
      { targetType: "HEADER", rule: "ADD", targetKey: "", value: "" },
    ]);
  }

  function remove(i: number) {
    onChange(rules.filter((_, ix) => ix !== i));
  }

  return (
    <div className="space-y-2">
      {rules.length === 0 ? (
        <div className="text-xs text-muted-foreground italic">No rules</div>
      ) : (
        rules.map((rule, i) => (
          <div
            key={i}
            className="grid grid-cols-[100px_120px_1fr_1fr_auto] gap-2 items-end"
          >
            <div className="space-y-1">
              {i === 0 && <Label>Target</Label>}
              <Select value={rule.targetType} disabled>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HEADER">HEADER</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              {i === 0 && <Label>Rule</Label>}
              <Select
                value={rule.rule}
                onValueChange={(v) => update(i, { rule: v as MatchAndReplace["rule"] })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RULE_OPTIONS.map((r) => (
                    <SelectItem key={r} value={r}>
                      {r}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              {i === 0 && <Label>Key</Label>}
              <Input
                value={rule.targetKey ?? ""}
                onChange={(e) => update(i, { targetKey: e.target.value })}
                placeholder="X-Header-Name"
                className="font-mono"
              />
            </div>
            <div className="space-y-1">
              {i === 0 && <Label>Value</Label>}
              <Input
                value={rule.value ?? ""}
                onChange={(e) => update(i, { value: e.target.value })}
                placeholder={rule.rule === "DELETE" ? "—" : "value"}
                disabled={rule.rule === "DELETE"}
                className="font-mono"
              />
            </div>
            <div className="space-y-1">
              {i === 0 && <Label className="opacity-0">_</Label>}
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => remove(i)}
                aria-label="Remove rule"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        ))
      )}
      <Button variant="outline" size="sm" onClick={add}>
        <Plus className="h-3 w-3" />
        Add rule
      </Button>
    </div>
  );
}
