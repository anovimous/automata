import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/cn";
import type {
  ConfigFileStatus,
  FindingStatus,
  HttpJobScope,
  HttpJobState,
  HttpMethod,
  HumanVerdict,
  JobCreator,
} from "@/types/domain";

// =============================================================================
// Job state pill
// =============================================================================
const STATE_STYLES: Record<HttpJobState, { className: string; dot?: string; label: string }> = {
  DRAFT: {
    className: "border-border bg-muted text-muted-foreground",
    dot: "bg-muted-foreground/60",
    label: "Draft",
  },
  TOQUEUE: {
    className: "border-warning/30 bg-warning/10 text-warning",
    dot: "bg-warning",
    label: "To queue",
  },
  SCHEDULED: {
    className: "border-warning/30 bg-warning/10 text-warning",
    dot: "bg-warning",
    label: "Scheduled",
  },
  QUEUED: {
    className: "border-foreground/15 bg-foreground/5 text-foreground",
    dot: "bg-foreground/70",
    label: "Queued",
  },
  RUNNING: {
    className: "border-success/30 bg-success/10 text-success",
    dot: "bg-success",
    label: "Running",
  },
  PAUSED: {
    className: "border-warning/30 bg-warning/10 text-warning",
    dot: "bg-warning",
    label: "Paused",
  },
  CANCELED: {
    className: "border-border bg-muted text-muted-foreground",
    dot: "bg-muted-foreground/60",
    label: "Canceled",
  },
  FINISHED: {
    className: "border-success/30 bg-success/10 text-success",
    dot: "bg-success",
    label: "Finished",
  },
  FAILED: {
    className: "border-destructive/30 bg-destructive/10 text-destructive",
    dot: "bg-destructive",
    label: "Failed",
  },
  SELFTERMINATED: {
    className: "border-warning/30 bg-warning/10 text-warning",
    dot: "bg-warning",
    label: "Self-terminated",
  },
  LOSTCONTACT: {
    className: "border-destructive/30 bg-destructive/10 text-destructive",
    dot: "bg-destructive",
    label: "Lost contact",
  },
};

export function JobStatePill({
  state,
  animate = true,
}: {
  state: HttpJobState;
  animate?: boolean;
}) {
  const s = STATE_STYLES[state];
  const animated = state === "RUNNING" && animate;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide leading-none",
        s.className,
      )}
    >
      <span
        className={cn(
          "inline-block h-1.5 w-1.5 rounded-full",
          s.dot,
          animated && "animate-pulse-dot",
        )}
      />
      {s.label}
    </span>
  );
}

// =============================================================================
// Job scope pill
// =============================================================================
const SCOPE_STYLES: Record<HttpJobScope, string> = {
  NARROW: "border-border bg-muted text-foreground",
  WIDE: "border-foreground/15 bg-foreground/5 text-foreground",
};

export function JobScopePill({ scope }: { scope: HttpJobScope }) {
  return <Badge className={cn(SCOPE_STYLES[scope], "uppercase")}>{scope}</Badge>;
}

// =============================================================================
// HTTP method pill — monochrome with subtle hue tint per method
// =============================================================================
const METHOD_STYLES: Record<HttpMethod, string> = {
  GET: "text-[#3b82f6] border-[#3b82f6]/25 bg-[#3b82f6]/10",
  POST: "text-success border-success/30 bg-success/10",
  PUT: "text-warning border-warning/30 bg-warning/10",
  PATCH: "text-warning border-warning/30 bg-warning/10",
  DELETE: "text-destructive border-destructive/30 bg-destructive/10",
  HEAD: "text-muted-foreground border-border bg-muted",
};

export function MethodPill({ method }: { method: HttpMethod }) {
  return (
    <span
      className={cn(
        "inline-flex items-center justify-center rounded-md border px-1.5 py-0.5 font-mono text-[10px] font-semibold uppercase leading-none tracking-tight",
        METHOD_STYLES[method],
      )}
    >
      {method}
    </span>
  );
}

// =============================================================================
// Status code pill
// =============================================================================
export function StatusCodePill({ code }: { code: number | null | undefined }) {
  if (code === null || code === undefined) {
    return <span className="text-muted-foreground">—</span>;
  }
  const variant =
    code >= 500
      ? "border-destructive/30 bg-destructive/10 text-destructive"
      : code >= 400
        ? "border-warning/30 bg-warning/10 text-warning"
        : code >= 300
          ? "border-foreground/15 bg-foreground/5 text-foreground"
          : code >= 200
            ? "border-success/30 bg-success/10 text-success"
            : "border-border bg-muted text-muted-foreground";
  return (
    <span
      className={cn(
        "inline-flex items-center justify-center rounded-md border px-1.5 py-0.5 font-mono text-[10px] font-semibold leading-none",
        variant,
      )}
    >
      {code}
    </span>
  );
}

// =============================================================================
// Who created a job / finding, and whether an AI job is approved
// =============================================================================
export function CreatorPill({ creator }: { creator: JobCreator }) {
  return (
    <Badge variant={creator === "AI" ? "accent" : "muted"} className="uppercase">
      {creator}
    </Badge>
  );
}

export function ApprovalPill({ approved }: { approved: boolean }) {
  return approved ? (
    <Badge variant="success">Approved</Badge>
  ) : (
    <Badge variant="warning">Needs approval</Badge>
  );
}

const CONFIG_STYLES: Record<ConfigFileStatus, string> = {
  PREPARING: "border-warning/30 bg-warning/10 text-warning",
  READY: "border-success/30 bg-success/10 text-success",
  FAILED: "border-destructive/30 bg-destructive/10 text-destructive",
};

export function ConfigFileStatusPill({ status }: { status: ConfigFileStatus }) {
  return (
    <Badge className={cn(CONFIG_STYLES[status], "uppercase")}>Files {status.toLowerCase()}</Badge>
  );
}

// =============================================================================
// Findings
// =============================================================================
export function FindingStatusPill({ status }: { status: FindingStatus }) {
  return status === "CONFIRMED" ? (
    <Badge variant="destructive">Confirmed</Badge>
  ) : (
    <Badge variant="warning">Suggested</Badge>
  );
}

export function VerdictPill({ verdict }: { verdict: HumanVerdict | null | undefined }) {
  if (!verdict) return <span className="text-muted-foreground">Unreviewed</span>;
  return verdict === "TRUE_POSITIVE" ? (
    <Badge variant="success">True positive</Badge>
  ) : (
    <Badge variant="muted">False positive</Badge>
  );
}

/** CVSS-style severity bands (0-10). */
export function SeverityPill({ severity }: { severity: number }) {
  const band =
    severity >= 9
      ? { label: "Critical", cls: "border-destructive/40 bg-destructive/15 text-destructive" }
      : severity >= 7
        ? { label: "High", cls: "border-destructive/30 bg-destructive/10 text-destructive" }
        : severity >= 4
          ? { label: "Medium", cls: "border-warning/30 bg-warning/10 text-warning" }
          : severity > 0
            ? { label: "Low", cls: "border-foreground/15 bg-foreground/5 text-foreground" }
            : { label: "None", cls: "border-border bg-muted text-muted-foreground" };
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide leading-none",
        band.cls,
      )}
    >
      <span className="font-mono tabular-nums">{severity.toFixed(1)}</span>
      {band.label}
    </span>
  );
}
