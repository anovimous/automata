import * as React from "react";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { Plus, Filter } from "lucide-react";
import { useJobs } from "@/hooks/useJobs";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { Checkbox } from "@/components/ui/primitives";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/primitives";
import { Badge } from "@/components/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { JobScopePill, JobStatePill } from "@/components/job/pills";
import { relativeTime } from "@/lib/utils";
import type {
  HttpJobFilter,
  HttpJobScope,
  HttpJobState,
} from "@/types/domain";

const ALL_STATES: HttpJobState[] = [
  "DRAFT",
  "TOQUEUE",
  "SCHEDULED",
  "QUEUED",
  "RUNNING",
  "PAUSED",
  "CANCELED",
  "FINISHED",
  "FAILED",
  "TERMINATED",
];

const TAB_STATES: Record<string, HttpJobState[] | undefined> = {
  active: ["RUNNING", "PAUSED", "QUEUED", "TOQUEUE", "SCHEDULED"],
  drafts: ["DRAFT"],
  finished: ["FINISHED"],
  failed: ["FAILED", "CANCELED", "TERMINATED"],
  all: undefined,
};

// =============================================================================
// Top-level jobs page
// =============================================================================
export function JobsListRoute() {
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as Record<string, unknown>;

  useSetTopBar([{ label: "Jobs" }]);

  return (
    <PageContainer>
      <PageHeader
        title="Jobs"
        description="All jobs across programs and hosts."
        actions={
          <Link to="/jobs/run-global">
            <Button>
              <Plus className="h-3.5 w-3.5" />
              New global job
            </Button>
          </Link>
        }
      />

      <JobsListContent
        searchState={search}
        onSearchChange={(s) => navigate({ to: "/jobs", search: s })}
      />
    </PageContainer>
  );
}

// =============================================================================
// Reusable content
// =============================================================================

interface JobsListContentProps {
  /** When set, all entries in here are AND-ed into the filter and not shown as UI. */
  forceFilter?: HttpJobFilter;
  hideFilters?: boolean;
  searchState: Record<string, unknown>;
  onSearchChange: (s: Record<string, unknown>) => void;
}

export function JobsListContent({
  forceFilter,
  hideFilters,
  searchState,
  onSearchChange,
}: JobsListContentProps) {
  const navigate = useNavigate();
  const tab = (searchState.tab as string) || "active";
  const states = parseStatesCsv(searchState.states as string | undefined);
  const scope = (searchState.scope as HttpJobScope | undefined) ?? undefined;
  const page = (searchState.page as number) ?? 0;
  const size = (searchState.size as number) ?? 25;

  // Compose filter: tab states take precedence unless the user has
  // explicit state checkboxes set ("All" tab).
  let currentStates: HttpJobState[] | undefined;
  if (states.length > 0) {
    currentStates = states;
  } else {
    currentStates = TAB_STATES[tab];
  }

  const filter: HttpJobFilter = {
    ...(forceFilter ?? {}),
    currentStates,
    scope,
  };

  const { data, isLoading } = useJobs(filter, { page, size, sort: ["id,desc"] });
  const rows = data?.content ?? [];

  return (
    <>
      {!hideFilters && (
        <div className="flex items-center gap-2 mb-3 flex-wrap">
          <Tabs
            value={tab}
            onValueChange={(v) =>
              onSearchChange({ ...searchState, tab: v, page: 0, states: undefined })
            }
          >
            <TabsList>
              <TabsTrigger value="active">Active</TabsTrigger>
              <TabsTrigger value="drafts">Drafts</TabsTrigger>
              <TabsTrigger value="finished">Finished</TabsTrigger>
              <TabsTrigger value="failed">Failed</TabsTrigger>
              <TabsTrigger value="all">All</TabsTrigger>
            </TabsList>
          </Tabs>

          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" size="sm">
                <Filter className="h-3 w-3" />
                States
                {states.length > 0 && <Badge variant="muted">{states.length}</Badge>}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[220px]">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-2">
                Filter by state
              </div>
              <div className="space-y-1.5 max-h-72 overflow-y-auto">
                {ALL_STATES.map((s) => {
                  const checked = states.includes(s);
                  return (
                    <label
                      key={s}
                      className="flex items-center gap-2 text-xs cursor-pointer"
                    >
                      <Checkbox
                        checked={checked}
                        onCheckedChange={() => {
                          const next = checked
                            ? states.filter((x) => x !== s)
                            : [...states, s];
                          onSearchChange({
                            ...searchState,
                            states: next.length ? next.join(",") : undefined,
                            page: 0,
                          });
                        }}
                      />
                      <span className="font-mono">{s}</span>
                    </label>
                  );
                })}
              </div>
              {states.length > 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="w-full mt-2"
                  onClick={() => onSearchChange({ ...searchState, states: undefined, page: 0 })}
                >
                  Clear
                </Button>
              )}
            </PopoverContent>
          </Popover>
        </div>
      )}

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={rows}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No jobs match these filters."
          onRowClick={(r) => navigate({ to: "/jobs/$jobId", params: { jobId: r.id } })}
          columns={[
            {
              key: "state",
              header: "State",
              width: "w-32",
              cell: (r) => <JobStatePill state={r.currentState} />,
            },
            {
              key: "scope",
              header: "Scope",
              cell: (r) => <JobScopePill scope={r.scope} />,
            },
            {
              key: "id",
              header: "Job",
              cell: (r) => (
                <Link
                  to="/jobs/$jobId"
                  params={{ jobId: r.id }}
                  className="font-mono tabular-nums text-xs hover:underline"
                >
                  #{r.id}
                </Link>
              ),
            },
            {
              key: "routine",
              header: "Routine",
              cell: (r) => (
                <span className="font-mono text-xs">{r.routineKey ?? "—"}</span>
              ),
            },
            {
              key: "rate",
              header: "Rate",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">
                  {r.rate ?? "—"}
                </span>
              ),
            },
            {
              key: "created",
              header: "Created",
              cell: (r) => (
                <span className="text-muted-foreground">
                  {relativeTime(r.creationDate)}
                </span>
              ),
            },
          ]}
        />
        <Pagination
          page={page}
          size={size}
          total={data?.totalElements}
          onPageChange={(p) => onSearchChange({ ...searchState, page: p })}
          onSizeChange={(s) => onSearchChange({ ...searchState, size: s, page: 0 })}
        />
      </div>
    </>
  );
}

function parseStatesCsv(raw: string | undefined): HttpJobState[] {
  if (!raw) return [];
  return raw
    .split(",")
    .map((s) => s.trim() as HttpJobState)
    .filter((s) => ALL_STATES.includes(s));
}
