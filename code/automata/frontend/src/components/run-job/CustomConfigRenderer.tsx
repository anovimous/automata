import * as React from "react";
import { Plus, Trash2, X } from "lucide-react";
import { Input, Label, Textarea } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Switch, Checkbox } from "@/components/ui/primitives";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  FieldSpec,
  FormState,
  RoutineSpec,
  ValidationError,
} from "@/lib/routineSpec";
import { isFieldVisible } from "@/lib/routineSpec";
import { cn } from "@/lib/cn";

interface CustomConfigRendererProps {
  spec: RoutineSpec;
  state: FormState;
  onChange: (next: FormState) => void;
  errors?: ValidationError[];
}

export function CustomConfigRenderer({
  spec,
  state,
  onChange,
  errors,
}: CustomConfigRendererProps) {
  return (
    <div className="space-y-3">
      {spec.description && (
        <p className="text-xs text-muted-foreground -mb-1">{spec.description}</p>
      )}
      {spec.fields.map((f) => (
        <FieldRenderer
          key={f.key}
          field={f}
          state={state}
          path={[f.key]}
          onChange={(v) => onChange({ ...state, [f.key]: v })}
          errors={errors}
          siblings={state}
        />
      ))}
    </div>
  );
}

function FieldRenderer({
  field,
  state,
  path,
  siblings,
  onChange,
  errors,
}: {
  field: FieldSpec;
  state: unknown;
  path: string[];
  siblings: FormState;
  onChange: (v: unknown) => void;
  errors?: ValidationError[];
}) {
  if (!isFieldVisible(field, siblings)) return null;

  const fieldError = errors?.find((e) => e.path.join(".") === path.join("."));
  const id = `f-${path.join("-")}`;

  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-2">
        <Label htmlFor={id}>
          {field.label}
          {field.required && <span className="text-destructive ml-0.5">*</span>}
        </Label>
        {field.locked && <Badge variant="muted">locked</Badge>}
      </div>
      {renderInput(field, state, onChange, id)}
      {field.help && !fieldError && (
        <div className="text-[11px] text-muted-foreground">{field.help}</div>
      )}
      {fieldError && (
        <div className="text-[11px] text-destructive">{fieldError.message}</div>
      )}
    </div>
  );
}

function renderInput(
  field: FieldSpec,
  state: unknown,
  onChange: (v: unknown) => void,
  id: string,
): React.ReactNode {
  const disabled = field.locked;

  switch (field.type) {
    case "string":
      return (
        <Input
          id={id}
          value={(state as string | undefined) ?? ""}
          onChange={(e) => onChange(e.target.value)}
          placeholder={field.placeholder}
          disabled={disabled}
        />
      );

    case "integer":
    case "number":
      return (
        <Input
          id={id}
          type="number"
          value={state === undefined || state === null ? "" : String(state)}
          onChange={(e) => {
            const v = e.target.value;
            if (v === "") onChange(undefined);
            else onChange(field.type === "integer" ? Math.trunc(Number(v)) : Number(v));
          }}
          placeholder={field.placeholder}
          min={field.min}
          max={field.max}
          disabled={disabled}
        />
      );

    case "boolean":
      return (
        <div>
          <Switch
            id={id}
            checked={!!state}
            onCheckedChange={(v) => onChange(!!v)}
            disabled={disabled}
          />
        </div>
      );

    case "enum":
      return (
        <Select
          value={state === undefined || state === null ? "" : String(state)}
          onValueChange={(v) => {
            const found = field.options?.find((o) => String(o.value) === v);
            onChange(found ? found.value : v);
          }}
          disabled={disabled}
        >
          <SelectTrigger>
            <SelectValue placeholder="—" />
          </SelectTrigger>
          <SelectContent>
            {field.options?.map((o) => (
              <SelectItem key={String(o.value)} value={String(o.value)}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );

    case "enum-multi": {
      const arr = (state as (string | number)[] | undefined) ?? [];
      return (
        <div className="space-y-1.5">
          {field.options?.map((o) => {
            const sel = arr.some((x) => String(x) === String(o.value));
            return (
              <label
                key={String(o.value)}
                className={cn(
                  "flex items-center gap-2 text-xs cursor-pointer",
                  disabled && "opacity-50 cursor-not-allowed",
                )}
              >
                <Checkbox
                  checked={sel}
                  onCheckedChange={() => {
                    if (disabled) return;
                    const next = sel
                      ? arr.filter((x) => String(x) !== String(o.value))
                      : [...arr, o.value];
                    onChange(next);
                  }}
                />
                <span>{o.label}</span>
              </label>
            );
          })}
        </div>
      );
    }

    case "string-list": {
      const arr = (state as string[] | undefined) ?? [];
      return (
        <div className="space-y-1.5">
          {arr.length === 0 && (
            <div className="text-xs text-muted-foreground italic">None</div>
          )}
          {arr.map((v, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input
                value={v}
                onChange={(e) => {
                  const next = [...arr];
                  next[i] = e.target.value;
                  onChange(next);
                }}
                placeholder={field.placeholder}
                disabled={disabled}
                className="font-mono"
              />
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => onChange(arr.filter((_, ix) => ix !== i))}
                aria-label="Remove"
                disabled={disabled}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>
          ))}
          <Button
            variant="outline"
            size="sm"
            onClick={() => onChange([...arr, ""])}
            disabled={disabled}
          >
            <Plus className="h-3 w-3" />
            Add
          </Button>
        </div>
      );
    }

    case "object": {
      const obj = (state as FormState) ?? {};
      return (
        <div className="rounded-md border border-border p-3 space-y-3">
          {field.fields?.map((sub) => (
            <FieldRenderer
              key={sub.key}
              field={sub}
              state={obj[sub.key]}
              path={[id, sub.key]}
              siblings={obj}
              onChange={(v) => onChange({ ...obj, [sub.key]: v })}
            />
          ))}
        </div>
      );
    }

    case "array-of-objects": {
      const arr = (state as FormState[] | undefined) ?? [];
      return (
        <div className="space-y-2">
          {arr.length === 0 && (
            <div className="text-xs text-muted-foreground italic">No items</div>
          )}
          {arr.map((item, i) => (
            <div key={i} className="rounded-md border border-border p-3 relative">
              <Button
                variant="ghost"
                size="icon-sm"
                className="absolute top-2 right-2"
                onClick={() => onChange(arr.filter((_, ix) => ix !== i))}
                aria-label="Remove item"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
              <div className="space-y-3">
                {field.itemFields?.map((sub) => (
                  <FieldRenderer
                    key={sub.key}
                    field={sub}
                    state={item[sub.key]}
                    path={[id, String(i), sub.key]}
                    siblings={item}
                    onChange={(v) => {
                      const next = [...arr];
                      next[i] = { ...item, [sub.key]: v };
                      onChange(next);
                    }}
                  />
                ))}
              </div>
            </div>
          ))}
          <Button variant="outline" size="sm" onClick={() => onChange([...arr, {}])}>
            <Plus className="h-3 w-3" />
            Add item
          </Button>
        </div>
      );
    }
  }
}

// Avoid unused-import warning if Textarea isn't reached
const _kept = Textarea;
