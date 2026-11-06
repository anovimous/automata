/**
 * Engine for the routine custom-config mappings file.
 *
 * - Loads /routine-specs.json at startup, caches it for the lifetime of the app.
 * - Provides helpers to build initial form state, validate, and emit the
 *   JSON object that goes into HttpJobCreationRequest.genericDetails.customConfig.
 */

// =============================================================================
// Spec types
// =============================================================================

export type FieldType =
  | "string"
  | "integer"
  | "number"
  | "boolean"
  | "enum"
  | "enum-multi"
  | "string-list"
  | "object"
  | "array-of-objects";

export interface VisibleWhen {
  field: string;
  equals?: unknown;
  in?: unknown[];
}

export interface EnumOption {
  value: string | number;
  label: string;
}

export interface FieldSpec {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  default?: unknown;
  locked?: boolean;
  min?: number;
  max?: number;
  pattern?: string;
  help?: string;
  placeholder?: string;
  options?: EnumOption[];
  visibleWhen?: VisibleWhen;
  fields?: FieldSpec[]; // for "object"
  itemFields?: FieldSpec[]; // for "array-of-objects"
}

export interface RoutineSpec {
  title?: string;
  description?: string;
  fields: FieldSpec[];
}

export interface SpecsFile {
  version: number;
  routines: Record<string, RoutineSpec>;
}

// =============================================================================
// Loader
// =============================================================================

let cachedPromise: Promise<SpecsFile | null> | null = null;

export function loadRoutineSpecs(): Promise<SpecsFile | null> {
  if (cachedPromise) return cachedPromise;
  cachedPromise = (async () => {
    try {
      const res = await fetch("/routine-specs.json", { cache: "no-cache" });
      if (!res.ok) return null;
      const data = (await res.json()) as SpecsFile;
      if (!data || typeof data !== "object" || !data.routines) return null;
      return data;
    } catch {
      return null;
    }
  })();
  return cachedPromise;
}

export async function getRoutineSpec(routineKey: string): Promise<RoutineSpec | null> {
  const all = await loadRoutineSpecs();
  if (!all) return null;
  return all.routines[routineKey] ?? null;
}

// =============================================================================
// Form state
// =============================================================================

export type FormValue = unknown;
export type FormState = Record<string, FormValue>;

/**
 * Walk a spec and produce an initial state with `default`s applied.
 */
export function buildInitialState(spec: RoutineSpec): FormState {
  const state: FormState = {};
  walk(spec.fields, state);
  return state;
}

function walk(fields: FieldSpec[], state: FormState) {
  for (const f of fields) {
    if (f.default !== undefined) {
      state[f.key] = clone(f.default);
    } else {
      switch (f.type) {
        case "boolean":
          state[f.key] = false;
          break;
        case "enum-multi":
        case "string-list":
        case "array-of-objects":
          state[f.key] = [];
          break;
        case "object": {
          const child: FormState = {};
          if (f.fields) walk(f.fields, child);
          state[f.key] = child;
          break;
        }
        default:
          state[f.key] = undefined;
      }
    }
  }
}

function clone<T>(v: T): T {
  if (v === null || typeof v !== "object") return v;
  return JSON.parse(JSON.stringify(v));
}

/**
 * Evaluate the visibleWhen predicate on a sibling state.
 */
export function isFieldVisible(f: FieldSpec, siblings: FormState): boolean {
  if (!f.visibleWhen) return true;
  const v = siblings[f.visibleWhen.field];
  if (f.visibleWhen.equals !== undefined) return v === f.visibleWhen.equals;
  if (f.visibleWhen.in) return f.visibleWhen.in.includes(v as never);
  return true;
}

// =============================================================================
// Validation
// =============================================================================

export interface ValidationError {
  path: string[];
  message: string;
}

export function validateForm(spec: RoutineSpec, state: FormState): ValidationError[] {
  const errors: ValidationError[] = [];
  validateFields(spec.fields, state, [], errors);
  return errors;
}

function validateFields(
  fields: FieldSpec[],
  state: FormState,
  path: string[],
  errors: ValidationError[],
) {
  for (const f of fields) {
    if (!isFieldVisible(f, state)) continue;
    const v = state[f.key];
    const here = [...path, f.key];

    if (f.required) {
      const empty =
        v === undefined ||
        v === null ||
        v === "" ||
        (Array.isArray(v) && v.length === 0);
      if (empty) {
        errors.push({ path: here, message: `${f.label} is required` });
        continue;
      }
    }

    if (v === undefined || v === null || v === "") continue;

    switch (f.type) {
      case "integer":
      case "number": {
        const n = typeof v === "number" ? v : Number(v);
        if (Number.isNaN(n)) {
          errors.push({ path: here, message: `${f.label} must be a number` });
        } else {
          if (f.type === "integer" && !Number.isInteger(n)) {
            errors.push({ path: here, message: `${f.label} must be an integer` });
          }
          if (f.min !== undefined && n < f.min) {
            errors.push({ path: here, message: `${f.label} must be ≥ ${f.min}` });
          }
          if (f.max !== undefined && n > f.max) {
            errors.push({ path: here, message: `${f.label} must be ≤ ${f.max}` });
          }
        }
        break;
      }
      case "string": {
        if (f.pattern) {
          try {
            const re = new RegExp(f.pattern);
            if (!re.test(String(v))) {
              errors.push({ path: here, message: `${f.label} does not match pattern` });
            }
          } catch {
            /* invalid regex in spec — silently ignore */
          }
        }
        break;
      }
      case "object":
        if (f.fields) {
          validateFields(f.fields, (v as FormState) || {}, here, errors);
        }
        break;
      case "array-of-objects":
        if (f.itemFields && Array.isArray(v)) {
          (v as FormState[]).forEach((item, i) => {
            validateFields(f.itemFields!, item, [...here, String(i)], errors);
          });
        }
        break;
      default:
        break;
    }
  }
}

/**
 * Produce a final clean JSON object for submission, stripping fields whose
 * visibleWhen evaluates false and dropping `undefined`s.
 */
export function buildSubmission(spec: RoutineSpec, state: FormState): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const f of spec.fields) {
    if (!isFieldVisible(f, state)) continue;
    const v = state[f.key];
    if (v === undefined) continue;
    if (f.type === "object" && f.fields) {
      out[f.key] = buildSubmission({ fields: f.fields }, (v as FormState) || {});
    } else if (f.type === "array-of-objects" && f.itemFields) {
      out[f.key] = (v as FormState[] | undefined)?.map((item) =>
        buildSubmission({ fields: f.itemFields! }, item || {}),
      );
    } else {
      out[f.key] = v;
    }
  }
  return out;
}
