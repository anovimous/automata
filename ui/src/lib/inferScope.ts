import type { AllowedTarget, HttpJobScope, SelectorType, TargetSelector } from "@/types/domain";
import { MULTI_REQUESTS_BACKEND_READY, WIDE_MULTI_HOSTS_BACKEND_READY } from "@/config/features";

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
   * Whether this selection is currently supported by the backend.
   * Some selector classes are TODO on the backend; we still let the user fill
   * the form but block submission with an explanatory banner.
   */
  backendReady: boolean;
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
        backendReady: true,
        summary: `1 request${sel.hostName ? ` on ${sel.hostName}` : ""}`,
        scopeLabel: "NARROW",
        selectionLabel: "Single request",
      };
    }

    case "multiple-requests": {
      // MultipleRequestsSelector { ids, highLevelMatcher, lowLevelMatcher }
      // For v1 we only fill the ids set. The matchers are advanced filtering
      // the backend currently expects to be either-or; we use ids.
      return {
        scope: "NARROW",
        selectorType: "MULTIPLE_REQUESTS",
        selector: {
          ids: sel.requestIds,
          highLevelMatcher: { hostId: sel.hostId },
        },
        targetSelector: {
          selectorType: "MULTIPLE_REQUESTS",
          selector: {
            ids: sel.requestIds,
            highLevelMatcher: { hostId: sel.hostId },
          },
        },
        backendReady: MULTI_REQUESTS_BACKEND_READY,
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
        backendReady: true,
        summary: `Host${sel.hostName ? ` ${sel.hostName}` : ` #${sel.hostId}`}`,
        scopeLabel: "NARROW",
        selectionLabel: "Single host",
      };
    }

    case "multiple-hosts": {
      // MultipleHostsSelector — backend answers 501 (TargetSelectionService).
      return {
        scope: "WIDE",
        selectorType: "MULTIPLE_HOSTS",
        selector: { ids: sel.hostIds },
        targetSelector: {
          selectorType: "MULTIPLE_HOSTS",
          selector: { ids: sel.hostIds },
        },
        backendReady: WIDE_MULTI_HOSTS_BACKEND_READY,
        summary: `${sel.hostIds.length} hosts in 1 program`,
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
