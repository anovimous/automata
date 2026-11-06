import type { AllowedTarget, HttpJobScope, SelectorType, TargetSelector } from "@/types/domain";

/**
 * The "selection" the user makes before opening the run-job page.
 *
 * Selections are mutually exclusive — exactly one of these branches is filled.
 * Equality sets are not job targets: the AI agent works on them through an agent
 * context, and a user job targets a host or request(s).
 */
export type RunJobSelection =
  | { kind: "single-host"; programId: number; hostId: number; hostName?: string }
  | { kind: "single-request"; programId: number; hostId: number; requestId: number; hostName?: string }
  | {
      kind: "multiple-requests";
      programId: number;
      hostId: number;
      requestIds: number[];
      hostName?: string;
    }
  | { kind: "multiple-hosts"; programId: number; hostIds: number[] }; // WIDE

export interface InferredScope {
  scope: HttpJobScope;
  selectorType: SelectorType;
  selector: unknown; // shape determined by selectorType (see backend embedded package)
  targetSelector: TargetSelector;
  /**
   * Human-readable one-line summary of what's selected.
   */
  summary: string;
  /**
   * One-word badge label for the scope pill.
   */
  scopeLabel: HttpJobScope;
  /**
   * Granular sub-kind shown next to the scope pill.
   */
  selectionLabel: string;
}

export function inferScope(sel: RunJobSelection): InferredScope {
  switch (sel.kind) {
    case "single-request": {
      // SingleRequestSelector { requestId: Long }
      return {
        scope: "NARROW",
        selectorType: "SINGLE_REQUEST",
        selector: { requestId: sel.requestId },
        targetSelector: {
          selectorType: "SINGLE_REQUEST",
          selector: { requestId: sel.requestId },
        },
        summary: `1 request${sel.hostName ? ` on ${sel.hostName}` : ""}`,
        scopeLabel: "NARROW",
        selectionLabel: "Single request",
      };
    }

    case "multiple-requests": {
      // MultipleRequestsSelector { requestIds }: every request must exist, be job eligible
      // and belong to the host of the job.
      return {
        scope: "NARROW",
        selectorType: "MULTIPLE_REQUESTS",
        selector: { requestIds: sel.requestIds },
        targetSelector: {
          selectorType: "MULTIPLE_REQUESTS",
          selector: { requestIds: sel.requestIds },
        },
        summary: `${sel.requestIds.length} requests${sel.hostName ? ` on ${sel.hostName}` : ""}`,
        scopeLabel: "NARROW",
        selectionLabel: `${sel.requestIds.length} requests`,
      };
    }

    case "single-host": {
      // SingleHostSelector { hostId: Long }
      return {
        scope: "NARROW",
        selectorType: "SINGLE_HOST",
        selector: { hostId: sel.hostId },
        targetSelector: {
          selectorType: "SINGLE_HOST",
          selector: { hostId: sel.hostId },
        },
        summary: `Host${sel.hostName ? ` ${sel.hostName}` : ` #${sel.hostId}`}`,
        scopeLabel: "NARROW",
        selectionLabel: "Single host",
      };
    }

    case "multiple-hosts": {
      // MultipleHostsSelector { hostIds }: a WIDE job; every host must belong to the program
      // of the job and accept jobs (a host rate limit of at least 1).
      return {
        scope: "WIDE",
        selectorType: "MULTIPLE_HOSTS",
        selector: { hostIds: sel.hostIds },
        targetSelector: {
          selectorType: "MULTIPLE_HOSTS",
          selector: { hostIds: sel.hostIds },
        },
        summary: `${sel.hostIds.length} hosts of one program`,
        scopeLabel: "WIDE",
        selectionLabel: `${sel.hostIds.length} hosts`,
      };
    }

  }
}

/**
 * Map a selection kind to the AllowedTarget enum used by Routine.allowedTargets.
 * Used to filter the routine dropdown in the run-job form.
 *
 * Note: backend enum value is "MULTILPLE_HOSTS" (sic).
 */
export function allowedTargetForSelection(sel: RunJobSelection): AllowedTarget {
  switch (sel.kind) {
    case "single-host":
      return "SINGLE_HOST";
    case "multiple-hosts":
      return "MULTILPLE_HOSTS";
    case "single-request":
      return "SINGLE_REQUEST";
    case "multiple-requests":
      return "MULTIPLE_REQUESTS";
  }
}
