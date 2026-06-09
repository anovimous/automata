import type { HttpJobScope, SelectorType, TargetSelector } from "@/types/domain";

/**
 * The "selection" the user makes before opening the run-job page.
 *
 * Selections are mutually exclusive — exactly one of these branches is filled.
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
  | {
      kind: "single-equality-set";
      programId: number;
      hostId: number;
      equalitySetId: number;
      hostName?: string;
    }
  | {
      kind: "multiple-equality-sets";
      programId: number;
      hostId: number;
      equalitySetIds: number[];
      hostName?: string;
    }
  | { kind: "multiple-hosts"; programId: number; hostIds: number[] } // WIDE
  | { kind: "global" }; // GLOBAL — no preselected hosts

export interface InferredScope {
  scope: HttpJobScope;
  selectorType: SelectorType | null; // null only for GLOBAL with empty selector
  selector: unknown; // shape determined by selectorType (see backend embedded package)
  targetSelector: TargetSelector | null;
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
  scopeLabel: "NARROW" | "WIDE" | "GLOBAL";
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
        backendReady: true,
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

    case "single-equality-set": {
      // SingleEqualitySetSelector { equalitySetId: Long }
      return {
        scope: "NARROW",
        selectorType: "SINGLE_EQUALITY_SET",
        selector: { equalitySetId: sel.equalitySetId },
        targetSelector: {
          selectorType: "SINGLE_EQUALITY_SET",
          selector: { equalitySetId: sel.equalitySetId },
        },
        backendReady: true,
        summary: `Equality set #${sel.equalitySetId}${sel.hostName ? ` on ${sel.hostName}` : ""}`,
        scopeLabel: "NARROW",
        selectionLabel: "Single equality set",
      };
    }

    case "multiple-equality-sets": {
      // MultipleEqualitySetsSelector — backend has it stubbed out (// TODO).
      return {
        scope: "NARROW",
        selectorType: "MULTIPLE_EQUALITY_SETS",
        selector: { ids: sel.equalitySetIds },
        targetSelector: {
          selectorType: "MULTIPLE_EQUALITY_SETS",
          selector: { ids: sel.equalitySetIds },
        },
        backendReady: false,
        summary: `${sel.equalitySetIds.length} equality sets${sel.hostName ? ` on ${sel.hostName}` : ""}`,
        scopeLabel: "NARROW",
        selectionLabel: `${sel.equalitySetIds.length} equality sets`,
      };
    }

    case "multiple-hosts": {
      // MultipleHostsSelector — backend has it stubbed out (// TODO).
      return {
        scope: "WIDE",
        selectorType: "MULTIPLE_HOSTS",
        selector: { ids: sel.hostIds },
        targetSelector: {
          selectorType: "MULTIPLE_HOSTS",
          selector: { ids: sel.hostIds },
        },
        backendReady: false,
        summary: `${sel.hostIds.length} hosts in 1 program`,
        scopeLabel: "WIDE",
        selectionLabel: `${sel.hostIds.length} hosts`,
      };
    }

    case "global": {
      // GLOBAL — no targetSelector required on the wire; backend has
      // GlobalHttpJobDetailsDto as an empty object.
      return {
        scope: "GLOBAL",
        selectorType: null,
        selector: null,
        targetSelector: null,
        backendReady: true,
        summary: "All in-scope targets, across programs",
        scopeLabel: "GLOBAL",
        selectionLabel: "Global",
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
export function allowedTargetForSelection(
  sel: RunJobSelection,
): "SINGLE_HOST" | "MULTILPLE_HOSTS" | "SINGLE_REQUEST" | "MULTIPLE_REQUESTS" | null {
  switch (sel.kind) {
    case "single-host":
      return "SINGLE_HOST";
    case "multiple-hosts":
      return "MULTILPLE_HOSTS";
    case "single-request":
    case "single-equality-set":
      return "SINGLE_REQUEST";
    case "multiple-requests":
    case "multiple-equality-sets":
      return "MULTIPLE_REQUESTS";
    case "global":
      // Global jobs don't have a single allowed-target match.
      // Show all routines; user picks.
      return null;
  }
}
