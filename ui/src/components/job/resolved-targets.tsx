import * as React from "react";
import { Link } from "@tanstack/react-router";
import { useQueries } from "@tanstack/react-query";
import { MoreHorizontal } from "lucide-react";
import { requestsApi, hostsApi } from "@/api";
import { requestsKeys } from "@/hooks/useRequests";
import { hostsKeys } from "@/hooks/useHosts";
import { MethodPill } from "@/components/job/pills";
import { Badge } from "@/components/ui/badge";
import type { TargetSelector } from "@/types/domain";

const MAX_RESOLVED = 10;

interface ResolvedTargetsProps {
  selector: TargetSelector;
  /** Used to build links to host/request pages. */
  fallbackProgramId?: number;
}

/**
 * Resolves a job's `targetSelector` into clickable rows.
 *
 * Selector shapes (from the backend's embedded selector classes):
 * - SINGLE_HOST            → { hostId }
 * - MULTIPLE_HOSTS         → { hostIds: number[] }                  (WIDE)
 * - SINGLE_REQUEST         → { requestId }
 * - MULTIPLE_REQUESTS      → { requestIds: number[] }
 * - SINGLE_EQUALITY_SET    → { equalitySetId }
 * - MULTIPLE_EQUALITY_SETS → { equalitySetIds: number[] }
 *
 * Fetches up to MAX_RESOLVED entities. Anything beyond that is summarised
 * as "and N more". Equality sets cannot be fetched (no backend endpoint),
 * so they only render as clickable links by id.
 */
export function ResolvedTargets({ selector, fallbackProgramId }: ResolvedTargetsProps) {
  const ids = extractIds(selector);
  const visibleIds = ids.slice(0, MAX_RESOLVED);
  const overflow = ids.length - visibleIds.length;

  const kind = selector.selectorType;

  // For request and host kinds we fetch each id. For equality sets we render
  // links only — no fetch.
  const fetchKind: "request" | "host" | null =
    kind === "SINGLE_REQUEST" || kind === "MULTIPLE_REQUESTS"
      ? "request"
      : kind === "SINGLE_HOST" || kind === "MULTIPLE_HOSTS"
        ? "host"
        : null;

  const queries = useQueries({
    queries: fetchKind
      ? visibleIds.map((id) => ({
          queryKey:
            fetchKind === "request"
              ? requestsKeys.detail(id)
              : hostsKeys.detail(id),
          queryFn: () =>
            fetchKind === "request" ? requestsApi.get(id) : hostsApi.get(id),
          staleTime: 60_000,
        }))
      : [],
  });

  if (ids.length === 0) {
    return (
      <div className="text-xs text-muted-foreground italic">
        No targets in selector.
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
        {labelFor(kind, ids.length)}
      </div>

      <ul className="divide-y divide-border rounded-md border border-border overflow-hidden">
        {visibleIds.map((id, i) => {
          const q = queries[i];
          return (
            <li key={`${kind}-${id}`} className="px-2.5 py-1.5">
              {fetchKind === "request" ? (
                <RequestRow
                  id={id}
                  data={q?.data as RequestData | undefined}
                  isLoading={q?.isLoading}
                  isError={q?.isError}
                  fallbackProgramId={fallbackProgramId}
                />
              ) : fetchKind === "host" ? (
                <HostRow
                  id={id}
                  data={q?.data as HostData | undefined}
                  isLoading={q?.isLoading}
                  isError={q?.isError}
                  fallbackProgramId={fallbackProgramId}
                />
              ) : (
                <EqualitySetRow id={id} fallbackProgramId={fallbackProgramId} />
              )}
            </li>
          );
        })}

        {overflow > 0 && (
          <li className="px-2.5 py-1.5 flex items-center gap-2 text-xs text-muted-foreground">
            <MoreHorizontal className="h-3 w-3" />
            and {overflow} more (see raw selector below)
          </li>
        )}
      </ul>
    </div>
  );
}

// =============================================================================
// Row renderers
// =============================================================================

type RequestData = {
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE" | "HEAD";
  computatedPath?: string | null;
  hostId?: number | null;
  programId?: number | null;
};

function RequestRow({
  id,
  data,
  isLoading,
  isError,
  fallbackProgramId,
}: {
  id: number;
  data: RequestData | undefined;
  isLoading?: boolean;
  isError?: boolean;
  fallbackProgramId?: number;
}) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <span className="font-mono tabular-nums">#{id}</span>
        <span>loading…</span>
      </div>
    );
  }
  if (isError || !data) {
    return (
      <div className="flex items-center gap-2 text-xs">
        <span className="font-mono tabular-nums text-muted-foreground">#{id}</span>
        <Badge variant="destructive">failed to load</Badge>
      </div>
    );
  }

  const programId = data.programId ?? fallbackProgramId;
  const hostId = data.hostId;

  const linkable = programId !== null && programId !== undefined && hostId;

  const content = (
    <div className="flex items-center gap-2 min-w-0">
      <MethodPill method={data.method} />
      <span className="font-mono text-xs truncate">
        {data.computatedPath ?? "—"}
      </span>
      <span className="ml-auto font-mono text-[10px] text-muted-foreground tabular-nums shrink-0">
        #{id}
      </span>
    </div>
  );

  return linkable ? (
    <Link
      to="/programs/$programId/hosts/$hostId/requests/$requestId"
      params={{ programId, hostId, requestId: id }}
      className="block hover:bg-accent/40 -mx-2.5 -my-1.5 px-2.5 py-1.5"
    >
      {content}
    </Link>
  ) : (
    <div>{content}</div>
  );
}

type HostData = {
  id: number;
  host: string;
  programId?: number | null;
};

function HostRow({
  id,
  data,
  isLoading,
  isError,
  fallbackProgramId,
}: {
  id: number;
  data: HostData | undefined;
  isLoading?: boolean;
  isError?: boolean;
  fallbackProgramId?: number;
}) {
  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <span className="font-mono tabular-nums">#{id}</span>
        <span>loading…</span>
      </div>
    );
  }
  if (isError || !data) {
    return (
      <div className="flex items-center gap-2 text-xs">
        <span className="font-mono tabular-nums text-muted-foreground">#{id}</span>
        <Badge variant="destructive">failed to load</Badge>
      </div>
    );
  }

  const programId = data.programId ?? fallbackProgramId;
  const linkable = programId !== null && programId !== undefined;

  const content = (
    <div className="flex items-center gap-2 min-w-0">
      <span className="font-mono text-xs truncate">{data.host}</span>
      <span className="ml-auto font-mono text-[10px] text-muted-foreground tabular-nums shrink-0">
        #{id}
      </span>
    </div>
  );

  return linkable ? (
    <Link
      to="/programs/$programId/hosts/$hostId"
      params={{ programId, hostId: id }}
      className="block hover:bg-accent/40 -mx-2.5 -my-1.5 px-2.5 py-1.5"
    >
      {content}
    </Link>
  ) : (
    <div>{content}</div>
  );
}

function EqualitySetRow({
  id,
  fallbackProgramId,
}: {
  id: number;
  fallbackProgramId?: number;
}) {
  // No public endpoint to resolve an equality set to a host id; we can only
  // link by id IF we know the program. Otherwise show the id only.
  const content = (
    <div className="flex items-center gap-2 min-w-0">
      <span className="font-mono text-xs truncate">Equality set #{id}</span>
      <span className="ml-auto text-[10px] text-muted-foreground tabular-nums shrink-0">
        no preview
      </span>
    </div>
  );

  if (fallbackProgramId === undefined) {
    return <div>{content}</div>;
  }

  // We don't know the host id without fetching; render a non-linked row
  // (clicking through requires the host id which only the job's program
  // context provides).
  return <div>{content}</div>;
}

// =============================================================================
// Helpers
// =============================================================================

function extractIds(selector: TargetSelector): number[] {
  const s = selector.selector as Record<string, unknown> | null | undefined;
  if (!s || typeof s !== "object") return [];
  switch (selector.selectorType) {
    case "SINGLE_HOST":
      return typeof s.hostId === "number" ? [s.hostId] : [];
    case "MULTIPLE_HOSTS":
      return Array.isArray(s.hostIds) ? (s.hostIds as number[]) : [];
    case "SINGLE_REQUEST":
      return typeof s.requestId === "number" ? [s.requestId] : [];
    case "MULTIPLE_REQUESTS":
      return Array.isArray(s.requestIds) ? (s.requestIds as number[]) : [];
    case "SINGLE_EQUALITY_SET":
      return typeof s.equalitySetId === "number" ? [s.equalitySetId] : [];
    case "MULTIPLE_EQUALITY_SETS":
      return Array.isArray(s.equalitySetIds) ? (s.equalitySetIds as number[]) : [];
    default:
      return [];
  }
}

function labelFor(kind: TargetSelector["selectorType"], count: number): string {
  switch (kind) {
    case "SINGLE_HOST":
      return "Host";
    case "MULTIPLE_HOSTS":
      return `Hosts (${count})`;
    case "SINGLE_REQUEST":
      return "Request";
    case "MULTIPLE_REQUESTS":
      return `Requests (${count})`;
    case "SINGLE_EQUALITY_SET":
      return "Equality set";
    case "MULTIPLE_EQUALITY_SETS":
      return `Equality sets (${count})`;
    default:
      return "Targets";
  }
}
