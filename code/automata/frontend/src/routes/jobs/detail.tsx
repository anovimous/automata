import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  ArrowLeft,
  Download,
  Pause,
  Play,
  Square,
  Trash2,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import {
  useCancelJob,
  useDeleteJob,
  useJob,
  usePauseJob,
  useQueueJob,
  useResumeJob,
} from "@/hooks/useJobs";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { JobScopePill, JobStatePill } from "@/components/job/pills";
import { ResolvedTargets } from "@/components/job/resolved-targets";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { ErrorState } from "@/components/common/states";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { jobsApi } from "@/api/jobs";
import { isActiveState, isTerminalState } from "@/types/enums";

export function JobDetailRoute() {
  const { jobId } = useParams({ strict: false }) as { jobId: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const jobQ = useJob(jobId);
  const queueMut = useQueueJob();
  const pauseMut = usePauseJob();
  const resumeMut = useResumeJob();
  const cancelMut = useCancelJob();
  const deleteMut = useDeleteJob();

  const [deleteOpen, setDeleteOpen] = React.useState(false);
  const [rawOpen, setRawOpen] = React.useState(false);

  useSetTopBar(
    [
      { label: "Jobs", to: "/jobs" },
      { label: `#${jobId}` },
    ],
    null,
    [jobId],
  );

  if (jobQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load job"
          description={jobQ.error instanceof Error ? jobQ.error.message : ""}
        />
      </PageContainer>
    );
  }

  const job = jobQ.data;
  const state = job?.currentState;

  async function safeRun<T>(label: string, fn: () => Promise<T>) {
    try {
      await fn();
      toast({ title: `${label} OK`, variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : `${label} failed`;
      toast({ title: `${label} failed`, description: msg, variant: "destructive" });
    }
  }

  const programId = job?.program?.id;

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/jobs" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to jobs
      </Button>

      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <span>Job #{jobId}</span>
            {state && <JobStatePill state={state} />}
            {job?.requestedState && job.requestedState !== state && (
              <span className="text-xs text-muted-foreground flex items-center gap-1">
                → <JobStatePill state={job.requestedState} animate={false} />
              </span>
            )}
            {job && <JobScopePill scope={job.httpJobScope} />}
          </span>
        }
        actions={
          <>
            {state === "DRAFT" && (
              <Button
                onClick={() =>
                  safeRun("Queue", () => queueMut.mutateAsync({ id: jobId, programId }))
                }
              >
                <Play className="h-3.5 w-3.5" />
                Queue
              </Button>
            )}
            {state === "RUNNING" && (
              <Button
                variant="outline"
                onClick={() =>
                  safeRun("Pause", () => pauseMut.mutateAsync({ id: jobId, programId }))
                }
              >
                <Pause className="h-3.5 w-3.5" />
                Pause
              </Button>
            )}
            {state === "PAUSED" && (
              <Button
                onClick={() =>
                  safeRun("Resume", () => resumeMut.mutateAsync({ id: jobId, programId }))
                }
              >
                <Play className="h-3.5 w-3.5" />
                Resume
              </Button>
            )}
            {state && isActiveState(state) && state !== "TOQUEUE" && (
              <Button
                variant="outline"
                onClick={() =>
                  safeRun("Cancel", () => cancelMut.mutateAsync({ id: jobId, programId }))
                }
              >
                <Square className="h-3.5 w-3.5" />
                Cancel
              </Button>
            )}
            {state === "FINISHED" && (
              <a href={jobsApi.resultDownloadUrl(jobId)} download={`job-${jobId}-result`}>
                <Button>
                  <Download className="h-3.5 w-3.5" />
                  Download result
                </Button>
              </a>
            )}
            {state && isTerminalState(state) && (
              <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            )}
          </>
        }
      />

      {!job ? (
        <div className="text-xs text-muted-foreground">Loading…</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4">
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Target</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                {job.program && (
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
                      Program
                    </span>
                    <Link
                      to="/programs/$programId"
                      params={{ programId: job.program.id }}
                      className="hover:underline"
                    >
                      {job.program.name ?? `#${job.program.id}`}
                    </Link>
                  </div>
                )}
                {job.targetSelector && (
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase tracking-wider text-muted-foreground">
                      Selector type
                    </span>
                    <Badge variant="outline">{job.targetSelector.selectorType}</Badge>
                  </div>
                )}

                {job.targetSelector && (
                  <ResolvedTargets
                    selector={job.targetSelector}
                    fallbackProgramId={job.program?.id}
                  />
                )}

                <button
                  onClick={() => setRawOpen((o) => !o)}
                  className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
                >
                  {rawOpen ? (
                    <ChevronDown className="h-3 w-3" />
                  ) : (
                    <ChevronRight className="h-3 w-3" />
                  )}
                  Raw target selector
                </button>
                {rawOpen && (
                  <pre className="text-[11px] font-mono bg-muted/40 border border-border rounded-md p-2 overflow-auto">
                    {JSON.stringify(job.targetSelector, null, 2)}
                  </pre>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Routine</CardTitle>
              </CardHeader>
              <CardContent className="text-sm">
                {job.routine ? (
                  <Link to="/routines/$id" params={{ id: job.routine.id }} className="font-mono hover:underline">
                    {job.routine.key ?? `#${job.routine.id}`}
                  </Link>
                ) : (
                  <span className="text-muted-foreground">—</span>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Custom config</CardTitle>
              </CardHeader>
              <CardContent>
                <pre className="text-[11px] font-mono bg-muted/40 border border-border rounded-md p-3 overflow-auto max-h-[420px]">
                  {JSON.stringify(job.customConfig ?? {}, null, 2)}
                </pre>
              </CardContent>
            </Card>

            {job.genericConfig?.matchAndReplace && job.genericConfig.matchAndReplace.length > 0 && (
              <Card>
                <CardHeader>
                  <CardTitle>Match & Replace rules</CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="space-y-1.5">
                    {job.genericConfig.matchAndReplace.map((r, i) => (
                      <li key={i} className="text-xs font-mono">
                        <Badge variant="outline">{r.rule}</Badge>{" "}
                        <Badge variant="muted">{r.targetType}</Badge>{" "}
                        <span>{r.targetKey ?? ""}</span>
                        {r.value && <span className="text-muted-foreground"> = {r.value}</span>}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            )}
          </div>

          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Generic config</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <KV label="Verbosity" value={job.verbosity ?? "—"} />
                <KV label="Priority" value={job.priority ?? "—"} />
                <KV label="Rate" value={job.rate ?? "—"} />
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete job?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync({ id: jobId, programId });
            toast({ title: "Job deleted", variant: "success" });
            navigate({ to: "/jobs" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}

function KV({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</div>
      <div className="text-sm font-mono tabular-nums mt-1">{value}</div>
    </div>
  );
}
