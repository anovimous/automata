import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  ArrowLeft,
  Bot,
  Check,
  Download,
  Pause,
  Play,
  RefreshCw,
  ShieldAlert,
  Square,
  Trash2,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import {
  useApproveJob,
  useCancelJob,
  useDeleteJob,
  useJob,
  usePauseJob,
  useQueueJob,
  useResumeJob,
  useRetryJobPreparation,
} from "@/hooks/useJobs";
import { useQuery } from "@tanstack/react-query";
import { useFindings } from "@/hooks/useFindings";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  ApprovalPill,
  ConfigFileStatusPill,
  CreatorPill,
  FindingStatusPill,
  JobScopePill,
  JobStatePill,
  SeverityPill,
} from "@/components/job/pills";
import { ResolvedTargets } from "@/components/job/resolved-targets";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { ErrorState } from "@/components/common/states";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { jobsApi } from "@/api/jobs";
import { isTerminalState } from "@/types/enums";
import { DELETE_JOB_BACKEND_READY } from "@/config/features";
import { relativeTime } from "@/lib/utils";

export function JobDetailRoute() {
  const { jobId } = useParams({ strict: false }) as { jobId: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const jobQ = useJob(jobId);
  const queueMut = useQueueJob();
  const approveMut = useApproveJob();
  const retryMut = useRetryJobPreparation();
  const pauseMut = usePauseJob();
  const resumeMut = useResumeJob();
  const cancelMut = useCancelJob();
  const deleteMut = useDeleteJob();

  const [deleteOpen, setDeleteOpen] = React.useState(false);
  const [rawOpen, setRawOpen] = React.useState(false);
  const findingsQ = useFindings({ jobId }, { page: 0, size: 20 });

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

  const isAi = job?.createdBy === "AI";
  const filesReady = job?.configFileStatus === "READY";
  const awaitingApproval = isAi && !job?.approved && state === "DRAFT";
  const canQueue = state === "DRAFT" && filesReady && !awaitingApproval;
  const canCancel =
    state === "TOQUEUE" || state === "QUEUED" || state === "RUNNING" || state === "PAUSED";
  // pause/resume/cancel of a job a worker holds are only requested (the worker
  // carries them out when it next polls)
  const pendingRequest = job?.requestedState && job.requestedState !== state;

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
            {job && <CreatorPill creator={job.createdBy} />}
          </span>
        }
        actions={
          <>
            {awaitingApproval && (
              <Button
                onClick={() =>
                  safeRun("Approve", () => approveMut.mutateAsync({ id: jobId, programId }))
                }
                disabled={approveMut.isPending}
              >
                <Check className="h-3.5 w-3.5" />
                Approve
              </Button>
            )}
            {state === "DRAFT" && (
              <Button
                variant={awaitingApproval ? "outline" : "default"}
                disabled={!canQueue || queueMut.isPending}
                title={
                  awaitingApproval
                    ? "An AI job needs a human approval before it can be queued"
                    : !filesReady
                      ? "The job files are not ready yet"
                      : undefined
                }
                onClick={() =>
                  safeRun("Queue", () => queueMut.mutateAsync({ id: jobId, programId }))
                }
              >
                <Play className="h-3.5 w-3.5" />
                Queue
              </Button>
            )}
            {state === "DRAFT" &&
              (job?.configFileStatus === "FAILED" || job?.configFileStatus === "PREPARING") && (
                <Button
                  variant="outline"
                  disabled={retryMut.isPending}
                  onClick={() =>
                    safeRun("Retry preparation", () =>
                      retryMut.mutateAsync({ id: jobId, programId }),
                    )
                  }
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                  Retry preparation
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
            {canCancel && (
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
            {DELETE_JOB_BACKEND_READY && state && isTerminalState(state) && (
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
            {pendingRequest && (
              <div className="rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-xs">
                <span className="font-medium text-warning">
                  {job.requestedState} requested.
                </span>{" "}
                <span className="text-muted-foreground">
                  The worker carries it out the next time it polls.
                </span>
              </div>
            )}
            {job.failureMessage && (
              <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs">
                <div className="font-medium text-destructive mb-0.5">Failure message</div>
                <div className="font-mono whitespace-pre-wrap break-words">
                  {job.failureMessage}
                </div>
              </div>
            )}
            {job.configFileFailure && (
              <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs">
                <div className="font-medium text-destructive mb-0.5">
                  Preparing the job files failed
                </div>
                <div className="font-mono whitespace-pre-wrap break-words">
                  {job.configFileFailure}
                </div>
              </div>
            )}

            {isAi && (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Bot className="h-3.5 w-3.5 text-muted-foreground" />
                    AI agent
                  </CardTitle>
                  <ApprovalPill approved={job.approved} />
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div>
                    <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1">
                      Goal of the job
                    </div>
                    <div className="whitespace-pre-wrap">{job.aiDescription ?? "—"}</div>
                  </div>
                  <div>
                    <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1">
                      Agent's summary of the result
                    </div>
                    {job.aiResult ? (
                      <div className="whitespace-pre-wrap">{job.aiResult}</div>
                    ) : (
                      <div className="text-xs text-muted-foreground">
                        {state && isTerminalState(state)
                          ? "The agent has not reviewed this result yet."
                          : "Set by the agent once the job has ended."}
                      </div>
                    )}
                  </div>
                  {job.agentContextUuid && (
                    <div className="text-[11px] text-muted-foreground font-mono">
                      Context {job.agentContextUuid}
                    </div>
                  )}
                </CardContent>
              </Card>
            )}

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

            {state && isTerminalState(state) && <ResultCard jobId={jobId} state={state} />}

            <Card>
              <CardHeader>
                <CardTitle>Findings</CardTitle>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    navigate({ to: "/findings", search: { jobId, create: true } })
                  }
                >
                  <ShieldAlert className="h-3.5 w-3.5" />
                  Report finding
                </Button>
              </CardHeader>
              <CardContent className="p-0">
                {findingsQ.data && findingsQ.data.content.length > 0 ? (
                  <table className="w-full text-[12px]">
                    <tbody>
                      {findingsQ.data.content.map((f) => (
                        <tr key={f.id} className="border-t border-border first:border-t-0">
                          <td className="px-4 py-2">
                            <Link
                              to="/findings/$findingId"
                              params={{ findingId: f.id }}
                              className="font-mono hover:underline"
                            >
                              #{f.id}
                            </Link>
                          </td>
                          <td className="px-2 py-2">
                            <SeverityPill severity={f.severity} />
                          </td>
                          <td className="px-2 py-2">
                            <FindingStatusPill status={f.status} />
                          </td>
                          <td className="px-4 py-2 text-right text-muted-foreground">
                            {relativeTime(f.creationDate)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="p-4 text-xs text-muted-foreground">
                    No finding references this job.
                  </div>
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

            <Card>
              <CardHeader>
                <CardTitle>Job files</CardTitle>
                {job.configFileStatus && <ConfigFileStatusPill status={job.configFileStatus} />}
              </CardHeader>
              <CardContent className="text-xs text-muted-foreground">
                The configuration and target files the worker reads. A draft job can only be
                queued once they are ready.
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

// =============================================================================
// Result of an ended job (served with the content type of its routine)
// =============================================================================
function ResultCard({ jobId, state }: { jobId: number; state: string }) {
  const [shown, setShown] = React.useState(false);
  const resultQ = useQuery({
    enabled: shown,
    queryKey: ["jobs", "result", jobId],
    queryFn: () => jobsApi.resultText(jobId),
    retry: false,
  });

  const text = React.useMemo(() => {
    if (typeof resultQ.data !== "string") return "";
    try {
      return JSON.stringify(JSON.parse(resultQ.data), null, 2);
    } catch {
      return resultQ.data;
    }
  }, [resultQ.data]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Result</CardTitle>
        <div className="flex items-center gap-2">
          {!shown && (
            <Button variant="outline" size="sm" onClick={() => setShown(true)}>
              View
            </Button>
          )}
          <a href={jobsApi.resultDownloadUrl(jobId)} download={`job-${jobId}.result`}>
            <Button variant="outline" size="sm">
              <Download className="h-3.5 w-3.5" />
              Download
            </Button>
          </a>
        </div>
      </CardHeader>
      <CardContent>
        {!shown ? (
          <div className="text-xs text-muted-foreground">
            The job is {state.toLowerCase()}. A job that ended without producing output has no
            result file.
          </div>
        ) : resultQ.isLoading ? (
          <div className="text-xs text-muted-foreground">Loading…</div>
        ) : resultQ.error ? (
          <div className="text-xs text-muted-foreground">
            {resultQ.error instanceof ApiError && resultQ.error.status === 404
              ? "This job has no result file."
              : resultQ.error instanceof Error
                ? resultQ.error.message
                : "Could not load the result."}
          </div>
        ) : (
          <pre className="text-[11px] font-mono bg-muted/40 border border-border rounded-md p-3 overflow-auto max-h-[480px] whitespace-pre-wrap break-words">
            {text || "(empty)"}
          </pre>
        )}
      </CardContent>
    </Card>
  );
}
