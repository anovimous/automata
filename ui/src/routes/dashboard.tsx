import * as React from "react";
import { Link } from "@tanstack/react-router";
import { Activity, Cpu, FileText, Triangle, Layers, ArrowRight } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { JobStatePill, JobScopePill } from "@/components/job/pills";
import { EmptyState } from "@/components/common/states";
import { useJobs } from "@/hooks/useJobs";
import { usePrograms } from "@/hooks/usePrograms";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import {
  getMostRecentHostInProgram,
  getMostRecentProgram,
  onActivityChange,
} from "@/lib/activity";
import { useSetTopBar } from "@/components/layout/top-bar";
import { relativeTime } from "@/lib/utils";
import { cn } from "@/lib/cn";

export function DashboardRoute() {
  useSetTopBar([{ label: "Dashboard" }]);

  return (
    <div className="p-6 space-y-5 max-w-[1500px] mx-auto">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Across-programs overview of running work and recent activity.
        </p>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="lg:col-span-2 space-y-4">
          <ActiveJobsPanel />
          <RecentDraftsPanel />
        </div>
        <div className="space-y-4">
          <CountsPanel />
          <RecentActivityPanel />
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// Active Jobs
// =============================================================================
function ActiveJobsPanel() {
  const { data, isLoading } = useJobs(
    { currentStates: ["RUNNING", "QUEUED", "TOQUEUE", "PAUSED"] },
    { page: 0, size: 10 },
  );
  const jobs = data?.content ?? [];

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Cpu className="h-3.5 w-3.5 text-muted-foreground" />
          <CardTitle>Active jobs</CardTitle>
          <Badge variant="muted">{data?.totalElements ?? 0}</Badge>
        </div>
        <Link to="/jobs" search={{ tab: "active" }}>
          <Button variant="ghost" size="sm" className="text-xs">
            All jobs <ArrowRight className="h-3 w-3" />
          </Button>
        </Link>
      </CardHeader>
      <CardContent className="p-0">
        {isLoading ? (
          <div className="p-8 text-center text-xs text-muted-foreground">Loading…</div>
        ) : jobs.length === 0 ? (
          <EmptyState
            title="No active jobs"
            description="When you queue a job, it will show up here."
          />
        ) : (
          <ul className="divide-y divide-border">
            {jobs.map((j) => (
              <li key={j.id}>
                <Link
                  to="/jobs/$jobId"
                  params={{ jobId: j.id }}
                  className="flex items-center gap-3 px-4 py-2.5 hover:bg-accent/40 transition-colors"
                >
                  <JobStatePill state={j.currentState} />
                  <JobScopePill scope={j.scope} />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium truncate">
                      #{j.id} · {j.routineKey ?? "—"}
                    </div>
                  </div>
                  <div className="text-xs text-muted-foreground tabular-nums">
                    {relativeTime(j.creationDate)}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

// =============================================================================
// Recent Drafts
// =============================================================================
function RecentDraftsPanel() {
  const { data, isLoading } = useJobs(
    { currentStates: ["DRAFT"] },
    { page: 0, size: 8, sort: ["creationDate,desc"] },
    { poll: false },
  );
  const jobs = data?.content ?? [];

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <FileText className="h-3.5 w-3.5 text-muted-foreground" />
          <CardTitle>Recent drafts</CardTitle>
          <Badge variant="muted">{data?.totalElements ?? 0}</Badge>
        </div>
        <Link to="/jobs" search={{ tab: "drafts" }}>
          <Button variant="ghost" size="sm" className="text-xs">
            All drafts <ArrowRight className="h-3 w-3" />
          </Button>
        </Link>
      </CardHeader>
      <CardContent className="p-0">
        {isLoading ? (
          <div className="p-8 text-center text-xs text-muted-foreground">Loading…</div>
        ) : jobs.length === 0 ? (
          <EmptyState title="No drafts" description="Jobs you start but don't queue appear here." />
        ) : (
          <ul className="divide-y divide-border">
            {jobs.map((j) => (
              <li key={j.id}>
                <Link
                  to="/jobs/$jobId"
                  params={{ jobId: j.id }}
                  className="flex items-center gap-3 px-4 py-2.5 hover:bg-accent/40 transition-colors"
                >
                  <JobScopePill scope={j.scope} />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium truncate">
                      #{j.id} · {j.routineKey ?? "—"}
                    </div>
                  </div>
                  <div className="text-xs text-muted-foreground tabular-nums">
                    {relativeTime(j.creationDate)}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

// =============================================================================
// Counts
// =============================================================================
function CountsPanel() {
  const programs = usePrograms(undefined, { page: 0, size: 1 });
  const allJobs = useJobs(undefined, { page: 0, size: 1 }, { poll: false });
  const runningJobs = useJobs(
    { currentStates: ["RUNNING"] },
    { page: 0, size: 1 },
    { poll: false },
  );
  const queuedJobs = useJobs(
    { currentStates: ["QUEUED", "TOQUEUE"] },
    { page: 0, size: 1 },
    { poll: false },
  );
  const failedJobs = useJobs(
    { currentStates: ["FAILED", "TERMINATED"] },
    { page: 0, size: 1 },
    { poll: false },
  );

  const items = [
    { label: "Programs", value: programs.data?.totalElements, to: "/programs" as const },
    { label: "Jobs", value: allJobs.data?.totalElements, to: "/jobs" as const },
    {
      label: "Running",
      value: runningJobs.data?.totalElements,
      accent: "success" as const,
    },
    {
      label: "Queued",
      value: queuedJobs.data?.totalElements,
      accent: "warning" as const,
    },
    {
      label: "Failed",
      value: failedJobs.data?.totalElements,
      accent: "destructive" as const,
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Counts</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <div className="grid grid-cols-2 divide-x divide-y divide-border border-t border-border">
          {items.map((it, i) => {
            const content = (
              <>
                <div
                  className={cn(
                    "text-2xl font-medium tabular-nums tracking-tight",
                    it.accent === "success" && "text-success",
                    it.accent === "warning" && "text-warning",
                    it.accent === "destructive" && "text-destructive",
                  )}
                >
                  {it.value ?? "—"}
                </div>
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground mt-1">
                  {it.label}
                </div>
              </>
            );
            return it.to ? (
              <Link
                key={i}
                to={it.to}
                className="px-4 py-3 hover:bg-accent/40 transition-colors"
              >
                {content}
              </Link>
            ) : (
              <div key={i} className="px-4 py-3">
                {content}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}

// =============================================================================
// Recent Activity (localStorage)
// =============================================================================
function RecentActivityPanel() {
  const [tick, setTick] = React.useState(0);
  React.useEffect(() => onActivityChange(() => setTick((x) => x + 1)), []);

  const mostRecentProgram = React.useMemo(() => getMostRecentProgram(), [tick]);
  const mostRecentHost = React.useMemo(
    () => (mostRecentProgram ? getMostRecentHostInProgram(mostRecentProgram.programId) : null),
    [mostRecentProgram, tick],
  );

  // Fetch the canonical names — fall back to whatever we stored in activity.
  const programQ = useProgram(mostRecentProgram?.programId ?? null);
  const hostQ = useHost(mostRecentHost?.hostId ?? null);

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Activity className="h-3.5 w-3.5 text-muted-foreground" />
          <CardTitle>Recent activity</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {!mostRecentProgram ? (
          <EmptyState
            title="No activity yet"
            description="Create a job, add a request, or modify a host to populate this."
          />
        ) : (
          <div className="divide-y divide-border">
            <Link
              to="/programs/$programId"
              params={{ programId: mostRecentProgram.programId }}
              className="block px-4 py-3 hover:bg-accent/40 transition-colors"
            >
              <div className="flex items-center gap-2">
                <Layers className="h-3.5 w-3.5 text-muted-foreground" />
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                  Latest program
                </div>
              </div>
              <div className="text-sm font-medium mt-1.5 truncate">
                {programQ.data?.name ??
                  mostRecentProgram.programName ??
                  `Program #${mostRecentProgram.programId}`}
              </div>
              <div className="text-xs text-muted-foreground mt-0.5">
                {relativeTime(mostRecentProgram.ts)}
              </div>
            </Link>

            {mostRecentHost ? (
              <Link
                to="/programs/$programId/hosts/$hostId"
                params={{
                  programId: mostRecentProgram.programId,
                  hostId: mostRecentHost.hostId,
                }}
                className="block px-4 py-3 hover:bg-accent/40 transition-colors"
              >
                <div className="flex items-center gap-2">
                  <Triangle className="h-3.5 w-3.5 text-muted-foreground" />
                  <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                    Latest host in this program
                  </div>
                </div>
                <div className="text-sm font-medium mt-1.5 font-mono truncate">
                  {hostQ.data?.host ??
                    mostRecentHost.hostName ??
                    `Host #${mostRecentHost.hostId}`}
                </div>
                <div className="text-xs text-muted-foreground mt-0.5">
                  {relativeTime(mostRecentHost.ts)}
                </div>
              </Link>
            ) : (
              <div className="px-4 py-3 text-xs text-muted-foreground">
                No host activity in this program yet.
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
