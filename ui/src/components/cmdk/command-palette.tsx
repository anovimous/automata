import * as React from "react";
import { Command } from "cmdk";
import { useNavigate, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  Layers,
  Cpu,
  ListTree,
  Bug,
  GitCompare,
  Wrench,
  FileText,
  Settings,
  Plus,
  Play,
  RefreshCw,
  Moon,
  Sun,
  Triangle,
  Shuffle,
  CornerDownLeft,
} from "lucide-react";
import {
  programsApi,
  hostsApi,
  routinesApi,
  comparatorsApi,
  vulnerabilitiesApi,
} from "@/api";
import { useQueryClient } from "@tanstack/react-query";
import { jobsKeys } from "@/hooks/useJobs";
import {
  applyTheme,
  getStoredTheme,
  setStoredTheme,
  type ThemeMode,
} from "@/lib/utils";

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}

interface EntityResult {
  kind: "program" | "host" | "routine" | "comparator" | "vulnerability";
  id: number;
  primary: string;
  secondary?: string;
  /** For hosts only */
  programId?: number;
}

export function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  const [query, setQuery] = React.useState("");
  const [debounced, setDebounced] = React.useState("");
  const [entityResults, setEntityResults] = React.useState<EntityResult[]>([]);
  const [loading, setLoading] = React.useState(false);

  // Reset on open
  React.useEffect(() => {
    if (open) {
      setQuery("");
      setDebounced("");
      setEntityResults([]);
    }
  }, [open]);

  // Debounce
  React.useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 150);
    return () => clearTimeout(t);
  }, [query]);

  // Entity search — runs only when query is non-empty
  React.useEffect(() => {
    let cancelled = false;
    if (!debounced || debounced.length < 2) {
      setEntityResults([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    (async () => {
      try {
        const [programs, routines, comparators, vulns] = await Promise.all([
          programsApi.list(debounced, { page: 0, size: 5 }).catch(() => null),
          routinesApi.list(debounced, undefined, { page: 0, size: 5 }).catch(() => null),
          comparatorsApi
            .list({ query: debounced, pageable: { page: 0, size: 5 } })
            .catch(() => null),
          vulnerabilitiesApi.list({ page: 0, size: 5 }).catch(() => null),
        ]);

        // Hosts only if we have a program context (best-effort) or no
        // good way to do a global search; pull from the first matching program.
        let hosts: EntityResult[] = [];
        if (programs?.content?.length) {
          const firstP = programs.content[0];
          const hh = await hostsApi
            .list(firstP.id, debounced, { page: 0, size: 5 })
            .catch(() => null);
          hosts =
            hh?.content?.map((h) => ({
              kind: "host" as const,
              id: h.id,
              primary: h.host,
              secondary: firstP.name,
              programId: firstP.id,
            })) ?? [];
        }

        const results: EntityResult[] = [
          ...(programs?.content?.map((p) => ({
            kind: "program" as const,
            id: p.id,
            primary: p.name,
            secondary: p.platform ?? undefined,
          })) ?? []),
          ...hosts,
          ...(routines?.content?.map((r) => ({
            kind: "routine" as const,
            id: r.id,
            primary: r.key,
            secondary: r.description ?? undefined,
          })) ?? []),
          ...(comparators?.content?.map((c) => ({
            kind: "comparator" as const,
            id: c.id,
            primary: c.name,
            secondary: c.schema ?? undefined,
          })) ?? []),
          ...((vulns?.content ?? [])
            .filter((v) => v.name.toLowerCase().includes(debounced.toLowerCase()))
            .map((v) => ({
              kind: "vulnerability" as const,
              id: v.id,
              primary: v.name,
            }))),
        ];

        if (!cancelled) setEntityResults(results);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [debounced]);

  function go(to: string, params?: Record<string, number | string>) {
    onOpenChange(false);
    navigate({ to: to as never, params: params as never });
  }

  function toggleTheme() {
    const cur = getStoredTheme();
    const next: ThemeMode = cur === "dark" ? "light" : "dark";
    setStoredTheme(next);
    applyTheme(next);
    onOpenChange(false);
  }

  // Context-aware extras for /programs/$programId/hosts/$hostId
  const hostMatch = pathname.match(/^\/programs\/(\d+)\/hosts\/(\d+)/);
  const inHost = !!hostMatch;
  const ctxProgramId = hostMatch ? Number(hostMatch[1]) : null;
  const ctxHostId = hostMatch ? Number(hostMatch[2]) : null;

  return (
    <Command.Dialog
      open={open}
      onOpenChange={onOpenChange}
      label="Command palette"
      className="fixed top-[18%] left-1/2 -translate-x-1/2 w-[640px] max-w-[92vw] rounded-lg border border-border bg-popover text-popover-foreground shadow-xl z-50 overflow-hidden"
    >
      {/* Backdrop is provided by Radix; cmdk uses its own dialog */}
      <div className="border-b border-border px-3 py-2 flex items-center gap-2">
        <Triangle className="h-3.5 w-3.5 text-muted-foreground" />
        <Command.Input
          autoFocus
          value={query}
          onValueChange={setQuery}
          placeholder="Search or run a command…"
          className="flex-1 bg-transparent outline-none text-sm placeholder:text-muted-foreground py-1"
        />
        {loading && (
          <RefreshCw className="h-3 w-3 text-muted-foreground animate-spin" />
        )}
      </div>

      <Command.List className="max-h-[60vh] overflow-y-auto p-1">
        <Command.Empty className="p-6 text-xs text-muted-foreground text-center">
          No results.
        </Command.Empty>

        {/* Context-aware actions */}
        {inHost && (
          <Command.Group heading="On this host" className="cmdk-group">
            <PaletteItem
              icon={Plus}
              label="Add raw request"
              hint="A"
              onSelect={() => {
                onOpenChange(false);
                // Land on requests page; user can use the Add button there.
                navigate({
                  to: "/programs/$programId/hosts/$hostId/requests",
                  params: { programId: ctxProgramId!, hostId: ctxHostId! },
                });
              }}
            />
            <PaletteItem
              icon={Play}
              label="Run job on host"
              onSelect={() =>
                go("/programs/$programId/hosts/$hostId/run-job", {
                  programId: ctxProgramId!,
                  hostId: ctxHostId!,
                })
              }
            />
            <PaletteItem
              icon={Shuffle}
              label="Equalize requests"
              onSelect={() =>
                go("/programs/$programId/hosts/$hostId/equalize", {
                  programId: ctxProgramId!,
                  hostId: ctxHostId!,
                })
              }
            />
          </Command.Group>
        )}

        {/* Navigation */}
        <Command.Group heading="Navigation" className="cmdk-group">
          <PaletteItem
            icon={LayoutDashboard}
            label="Dashboard"
            onSelect={() => go("/")}
          />
          <PaletteItem icon={Layers} label="Programs" onSelect={() => go("/programs")} />
          <PaletteItem icon={Cpu} label="Jobs" onSelect={() => go("/jobs")} />
          <PaletteItem
            icon={ListTree}
            label="Routines"
            onSelect={() => go("/routines")}
          />
          <PaletteItem
            icon={Bug}
            label="Vulnerabilities"
            onSelect={() => go("/vulnerabilities")}
          />
          <PaletteItem
            icon={GitCompare}
            label="Comparators"
            onSelect={() => go("/comparators")}
          />
          <PaletteItem
            icon={Wrench}
            label="Modifiers"
            onSelect={() => go("/modifiers")}
          />
          <PaletteItem
            icon={FileText}
            label="Wordlists"
            onSelect={() => go("/wordlists")}
          />
          <PaletteItem icon={Settings} label="Settings" onSelect={() => go("/settings")} />
        </Command.Group>

        {/* Entities */}
        {entityResults.length > 0 && (
          <Command.Group heading="Entities" className="cmdk-group">
            {entityResults.map((r) => (
              <Command.Item
                key={`${r.kind}-${r.id}`}
                value={`${r.kind}:${r.id}:${r.primary}`}
                onSelect={() => {
                  switch (r.kind) {
                    case "program":
                      return go("/programs/$programId", { programId: r.id });
                    case "host":
                      return go("/programs/$programId/hosts/$hostId", {
                        programId: r.programId!,
                        hostId: r.id,
                      });
                    case "routine":
                      return go("/routines/$id", { id: r.id });
                    case "comparator":
                      return go("/comparators/$id", { id: r.id });
                    case "vulnerability":
                      return go("/vulnerabilities/$id", { id: r.id });
                  }
                }}
                className="cmdk-item"
              >
                {iconForKind(r.kind)}
                <span className="flex-1 truncate">{r.primary}</span>
                <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
                  {r.kind}
                </span>
                {r.secondary && (
                  <span className="text-xs text-muted-foreground truncate max-w-[180px]">
                    {r.secondary}
                  </span>
                )}
              </Command.Item>
            ))}
          </Command.Group>
        )}

        {/* Global actions */}
        <Command.Group heading="Actions" className="cmdk-group">
          <PaletteItem
            icon={Plus}
            label="New program"
            onSelect={() => go("/programs")}
          />
          <PaletteItem
            icon={Play}
            label="Run global job"
            onSelect={() => go("/jobs/run-global")}
          />
          <PaletteItem
            icon={RefreshCw}
            label="Refresh jobs"
            onSelect={() => {
              qc.invalidateQueries({ queryKey: jobsKeys.all });
              onOpenChange(false);
            }}
          />
          <PaletteItem
            icon={getStoredTheme() === "dark" ? Sun : Moon}
            label={`Switch to ${getStoredTheme() === "dark" ? "light" : "dark"} theme`}
            onSelect={toggleTheme}
          />
        </Command.Group>
      </Command.List>

      <div className="flex items-center justify-end gap-3 px-3 py-1.5 border-t border-border text-[10px] text-muted-foreground">
        <span className="flex items-center gap-1">
          <CornerDownLeft className="h-3 w-3" /> select
        </span>
        <span>esc to close</span>
      </div>

      {/* Inline styles for cmdk classnames */}
      <style>{`
        .cmdk-group [cmdk-group-heading] {
          padding: 6px 8px 4px;
          font-size: 10px;
          letter-spacing: 0.06em;
          text-transform: uppercase;
          color: var(--muted-foreground);
        }
        .cmdk-item, [cmdk-item] {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 6px 8px;
          font-size: 13px;
          border-radius: 4px;
          cursor: pointer;
        }
        [cmdk-item][data-selected="true"] {
          background: var(--accent);
        }
      `}</style>
    </Command.Dialog>
  );
}

function PaletteItem({
  icon: Icon,
  label,
  hint,
  onSelect,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  hint?: string;
  onSelect: () => void;
}) {
  return (
    <Command.Item value={label} onSelect={onSelect} className="cmdk-item">
      <Icon className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
      <span className="flex-1 truncate">{label}</span>
      {hint && <span className="text-[10px] text-muted-foreground">{hint}</span>}
    </Command.Item>
  );
}

function iconForKind(kind: EntityResult["kind"]) {
  const Icon =
    kind === "program"
      ? Layers
      : kind === "host"
        ? Triangle
        : kind === "routine"
          ? ListTree
          : kind === "comparator"
            ? GitCompare
            : Bug;
  return <Icon className="h-3.5 w-3.5 text-muted-foreground shrink-0" />;
}
