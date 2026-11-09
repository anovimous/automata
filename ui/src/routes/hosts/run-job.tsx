import * as React from "react";
import { useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { ArrowLeft, FileText, Play, Save, X } from "lucide-react";
import { useQueries } from "@tanstack/react-query";
import { useHost, hostsKeys } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useTenants } from "@/hooks/useTenants";
import { useRoutine } from "@/hooks/useResources";
import { useWordlists } from "@/hooks/useResources";
import { useCreateJob, useQueueJob } from "@/hooks/useJobs";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  RadioGroup,
  RadioGroupItem,
} from "@/components/ui/primitives";
import { TargetSummary } from "@/components/run-job/TargetSummary";
import { RoutinePicker } from "@/components/run-job/RoutinePicker";
import { CustomConfigRenderer } from "@/components/run-job/CustomConfigRenderer";
import { MonacoJsonEditor } from "@/components/run-job/MonacoJsonEditor";
import { MRRulesEditor } from "@/components/run-job/MRRulesEditor";
import {
  allowedTargetForSelection,
  inferScope,
  type RunJobSelection,
} from "@/lib/inferScope";
import {
  buildInitialState,
  buildSubmission,
  loadRoutineSpecs,
  validateForm,
  type FormState,
  type RoutineSpec,
  type ValidationError,
} from "@/lib/routineSpec";
import type {
  ConfigFileStatus,
  HttpJobCreationRequest,
  MatchAndReplace,
  RoutineSummary,
  Verbosity,
  Duration,
} from "@/types/domain";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { hostsApi, jobsApi, requestsApi } from "@/api";
import {
  NOT_ELIGIBLE_REASON,
  EAGER_PRIORITY,
  MAX_JOB_PRIORITY,
  MIN_JOB_PRIORITY,
  hostAcceptsJobs,
  isJobEligibleBody,
  narrowRateLimits,
  priorityProblem,
  rateProblem,
  tightestLimit,
  wideRateLimits,
} from "@/lib/jobRules";
import { base64ToBytes, bytesToBase64, concatBytes, formatBytes } from "@/lib/http";

type RunJobSearch = {
  kind?: string;
  requestIds?: string;
  hostIds?: string;
};

const NO_TENANT = "__none__";

export function RunJobRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const search = useSearch({ strict: false }) as RunJobSearch;
  return <RunJobForm programId={programId} hostId={hostId} search={search} />;
}

// =============================================================================
// Component
// =============================================================================

interface RunJobFormProps {
  programId: number;
  hostId: number;
  search: RunJobSearch;
}

export function RunJobForm({ programId, hostId, search }: RunJobFormProps) {
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);

  // ---- Selection ----
  const selection: RunJobSelection = React.useMemo(() => {
    const requestIds = parseIdsCsv(search.requestIds);
    const hostIds = parseIdsCsv(search.hostIds);

    const hostName = hostQ.data?.host;
    const pid = programId;
    const hid = hostId;

    if (search.kind === "single-request" && requestIds.length === 1) {
      return {
        kind: "single-request",
        programId: pid,
        hostId: hid,
        requestId: requestIds[0],
        hostName,
      };
    }
    if (search.kind === "multiple-requests" || requestIds.length > 1) {
      return {
        kind: "multiple-requests",
        programId: pid,
        hostId: hid,
        requestIds,
        hostName,
      };
    }
    if (search.kind === "multiple-hosts" || hostIds.length > 1) {
      return {
        kind: "multiple-hosts",
        programId: pid,
        hostIds,
      };
    }
    // Default to single-host
    return {
      kind: "single-host",
      programId: pid,
      hostId: hid,
      hostName,
    };
  }, [search, programId, hostId, hostQ.data?.host]);

  const scope = React.useMemo(() => inferScope(selection), [selection]);
  const allowedTarget = React.useMemo(() => allowedTargetForSelection(selection), [selection]);

  // ---- Routine ----
  const [routineId, setRoutineId] = React.useState<number | null>(null);
  const [routineSummary, setRoutineSummary] = React.useState<RoutineSummary | null>(null);
  const routineDetailQ = useRoutine(routineId);
  const routineKey = routineDetailQ.data?.key ?? routineSummary?.key ?? null;

  // ---- Custom config ----
  const [spec, setSpec] = React.useState<RoutineSpec | null>(null);
  const [specLoaded, setSpecLoaded] = React.useState(false);
  const [formState, setFormState] = React.useState<FormState>({});
  const [rawConfig, setRawConfig] = React.useState<string>("{}");

  // Load the spec when routineKey changes.
  React.useEffect(() => {
    let active = true;
    setSpec(null);
    setSpecLoaded(false);
    if (!routineKey) {
      setFormState({});
      setRawConfig("{}");
      return;
    }
    (async () => {
      const all = await loadRoutineSpecs();
      if (!active) return;
      const found = all?.routines?.[routineKey] ?? null;
      setSpec(found);
      setSpecLoaded(true);
      if (found) {
        setFormState(buildInitialState(found));
      } else {
        setFormState({});
        setRawConfig("{}");
      }
    })();
    return () => {
      active = false;
    };
  }, [routineKey]);

  const validationErrors: ValidationError[] = React.useMemo(() => {
    if (!spec) return [];
    return validateForm(spec, formState);
  }, [spec, formState]);

  // ---- Generic config ----
  const [verbosity, setVerbosity] = React.useState<Verbosity>("NORMAL");
  const [priority, setPriority] = React.useState<string>("1");
  const [rate, setRate] = React.useState<string>("");
  const [mrRules, setMrRules] = React.useState<MatchAndReplace[]>([]);

  // ---- Wordlists ----
  const wordlistsQ = useWordlists({
    vulnId: routineDetailQ.data?.vulnerabilityId ?? undefined,
    pageable: { page: 0, size: 100 },
  });
  const [showAllWordlists, setShowAllWordlists] = React.useState(false);
  const allWordlistsQ = useWordlists({
    pageable: { page: 0, size: 200 },
  });
  const visibleWordlists =
    showAllWordlists || !routineDetailQ.data?.vulnerabilityId
      ? allWordlistsQ.data?.content ?? []
      : wordlistsQ.data?.content ?? [];

  const [wordlistIds, setWordlistIds] = React.useState<number[]>([]);

  // ---- NARROW-only fields ----
  const tenantsQ = useTenants(hostId, { page: 0, size: 100 });
  const [tenantId, setTenantId] = React.useState<string>(NO_TENANT);
  const [duration, setDuration] = React.useState<Duration>("SHORT");

  // ---- Top bar ----
  useSetTopBar(
    [
      { label: "Programs", to: "/programs" },
      {
        label: programQ.data?.name ?? `#${programId}`,
        to: `/programs/${programId}` as string,
      },
      ...(selection.kind === "multiple-hosts"
        ? []
        : [
            {
              label: hostQ.data?.host ?? `Host #${hostId}`,
              to: `/programs/${programId}/hosts/${hostId}` as string,
            },
          ]),
      { label: "Run job" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId, selection.kind],
  );

  // ---- Mutations ----
  const createMut = useCreateJob();
  const queueMut = useQueueJob();

  function buildRequest(): HttpJobCreationRequest {
    const customConfig = spec
      ? buildSubmission(spec, formState)
      : safeJsonParse(rawConfig);

    const base: HttpJobCreationRequest = {
      httpJobScope: scope.scope,
      genericDetails: {
        verbosity,
        priority: Number(priority),
        rate: Number(rate),
        routineId: routineId as number,
        wordlistsIds: wordlistIds.length ? wordlistIds : undefined,
        targetSelector: scope.targetSelector,
        matchAndReplace: mrRules.length ? mrRules : undefined,
        customConfig,
      },
    };

    if (scope.scope === "NARROW") {
      base.narrowJobDetails = {
        duration,
        hostId,
        tenantId: tenantId !== NO_TENANT ? Number(tenantId) : undefined,
      };
    } else {
      base.wideJobDetails = { programId };
    }

    return base;
  }

  // ---- What the core will check, shown before sending ----
  const rateLimits = React.useMemo(() => {
    if (scope.scope === "WIDE") return wideRateLimits(programQ.data);
    return hostQ.data ? narrowRateLimits(hostQ.data, programQ.data, duration) : [];
  }, [scope.scope, programQ.data, hostQ.data, duration]);
  const tightest = tightestLimit(rateLimits);

  // A wide job's hosts all have to accept jobs; a narrow job's host does.
  const wideHostIds = selection.kind === "multiple-hosts" ? selection.hostIds : [];
  const wideHostQs = useQueries({
    queries: wideHostIds.map((id) => ({
      queryKey: hostsKeys.detail(id),
      queryFn: () => hostsApi.get(id),
    })),
  });
  const closedHosts: string[] =
    scope.scope === "WIDE"
      ? wideHostQs.flatMap((q) => (q.data && !hostAcceptsJobs(q.data) ? [q.data.host] : []))
      : hostQ.data && !hostAcceptsJobs(hostQ.data)
        ? [hostQ.data.host]
        : [];

  // Only a request without a body, or with a JSON or form body, can be a job target.
  const targetRequestIds =
    selection.kind === "single-request"
      ? [selection.requestId]
      : selection.kind === "multiple-requests"
        ? selection.requestIds
        : [];
  const requestMetaQs = useQueries({
    queries: targetRequestIds.map((id) => ({
      queryKey: ["requests", "metadata", id],
      queryFn: () => requestsApi.metadata(id),
    })),
  });
  const ineligibleRequests = requestMetaQs.flatMap((q) =>
    q.data && !isJobEligibleBody(q.data.body) ? [q.data.requestId] : [],
  );

  const problems: string[] = [];
  if (routineId === null) problems.push("Pick a routine.");
  if (spec && validationErrors.length > 0) problems.push("The configuration has errors.");
  const ratePb = rateProblem(rate, rateLimits);
  if (ratePb) problems.push(ratePb);
  const priorityPb = priorityProblem(priority);
  if (priorityPb) problems.push(priorityPb);
  if (closedHosts.length > 0) {
    problems.push(
      closedHosts.length === 1 && scope.scope === "NARROW"
        ? "This host has a rate limit of 0, so it is closed to jobs."
        : `Host${closedHosts.length > 1 ? "s" : ""} ${closedHosts.join(", ")} ha${
            closedHosts.length > 1 ? "ve" : "s"
          } a rate limit of 0, so ${closedHosts.length > 1 ? "they are" : "it is"} closed to jobs.`,
    );
  }
  if (ineligibleRequests.length > 0) {
    problems.push(
      `Request${ineligibleRequests.length > 1 ? "s" : ""} ${ineligibleRequests
        .map((id) => `#${id}`)
        .join(", ")} can't be job targets. ${NOT_ELIGIBLE_REASON}.`,
    );
  }
  const canSubmit = problems.length === 0;

  async function saveDraft() {
    if (!canSubmit) return;
    try {
      const id = await createMut.mutateAsync({
        req: buildRequest(),
        programId,
        hostId,
        hostName: hostQ.data?.host,
      });
      toast({ title: "Draft saved", variant: "success" });
      if (id) navigate({ to: "/jobs/$jobId", params: { jobId: id } });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Save failed";
      toast({ title: "Save failed", description: msg, variant: "destructive" });
    }
  }

  async function saveAndQueue() {
    if (!canSubmit) return;
    try {
      const id = await createMut.mutateAsync({
        req: buildRequest(),
        programId,
        hostId,
        hostName: hostQ.data?.host,
      });
      if (id) {
        // The config files are built after the job is committed; the core only
        // queues a job whose files are READY.
        const ready = await waitForConfigFiles(id);
        if (ready !== "READY") {
          toast({
            title: "Draft saved, not queued",
            description:
              ready === "FAILED"
                ? "Preparing the job files failed. Retry the preparation from the job page."
                : "The job files are still being prepared. Queue it from the job page when ready.",
            variant: "warning",
          });
          navigate({ to: "/jobs/$jobId", params: { jobId: id } });
          return;
        }
        await queueMut.mutateAsync({ id, programId, hostId });
        toast({ title: "Job queued", variant: "success" });
        navigate({ to: "/jobs/$jobId", params: { jobId: id } });
      } else {
        toast({
          title: "Draft saved",
          description: "Could not auto-queue (no job id returned).",
          variant: "warning",
        });
      }
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Save & queue failed";
      toast({ title: "Failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: ".." })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>

      <PageHeader title="Run job" />

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-5">
        {/* Main column */}
        <div className="space-y-4">
          <TargetSummary scope={scope} />

          {problems.length > 0 && (
            <div className="rounded-md border border-warning/30 bg-warning/10 px-3 py-2">
              <div className="text-xs font-medium text-warning mb-1">
                Before the job can be saved
              </div>
              <ul className="text-xs text-muted-foreground list-disc pl-4 space-y-0.5">
                {problems.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Routine */}
          <Card>
            <CardHeader>
              <CardTitle>Routine</CardTitle>
            </CardHeader>
            <CardContent>
              <RoutinePicker
                value={routineId}
                onChange={(id, r) => {
                  setRoutineId(id);
                  setRoutineSummary(r);
                }}
                allowedTarget={allowedTarget}
              />
              {routineDetailQ.data?.description && (
                <div className="text-xs text-muted-foreground mt-2">
                  {routineDetailQ.data.description}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Custom config */}
          {routineKey && (
            <Card>
              <CardHeader>
                <CardTitle>Configuration</CardTitle>
                {specLoaded && !spec && (
                  <Badge variant="warning">No spec — JSON fallback</Badge>
                )}
              </CardHeader>
              <CardContent>
                {!specLoaded ? (
                  <div className="text-xs text-muted-foreground">Loading spec…</div>
                ) : spec ? (
                  <CustomConfigRenderer
                    spec={spec}
                    state={formState}
                    onChange={setFormState}
                    errors={validationErrors}
                  />
                ) : (
                  <div className="space-y-2">
                    <p className="text-xs text-muted-foreground">
                      This routine has no entry in <code>routine-specs.json</code>. Provide
                      the custom config as a raw JSON object
                      {routineDetailQ.data?.configSchema
                        ? "; it is validated against the routine's config schema."
                        : "."}
                    </p>
                    <MonacoJsonEditor value={rawConfig} onChange={setRawConfig} />
                    {selection.kind === "single-request" && (
                      <div className="flex items-center gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={async () => {
                            try {
                              const base64 = await rawRequestBase64(selection.requestId);
                              const current = safeJsonParse(rawConfig) as Record<string, unknown>;
                              setRawConfig(
                                JSON.stringify(
                                  { ...current, rawRequestBase64: base64 },
                                  null,
                                  2,
                                ),
                              );
                            } catch (e) {
                              const msg = e instanceof ApiError ? e.message : "Could not load the request";
                              toast({ title: "Failed", description: msg, variant: "destructive" });
                            }
                          }}
                        >
                          <FileText className="h-3.5 w-3.5" />
                          Insert request #{selection.requestId} as rawRequestBase64
                        </Button>
                        <span className="text-[11px] text-muted-foreground">
                          For routines that take the raw request (fuzz).
                        </span>
                      </div>
                    )}
                    {routineDetailQ.data?.configSchema != null && (
                      <details className="text-xs">
                        <summary className="cursor-pointer text-muted-foreground">
                          Config schema
                        </summary>
                        <pre className="mt-1 text-[11px] font-mono bg-muted/40 border border-border rounded-md p-2 overflow-auto max-h-60">
                          {JSON.stringify(routineDetailQ.data.configSchema, null, 2)}
                        </pre>
                      </details>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Wordlists */}
          {routineId && (
            <Card>
              <CardHeader>
                <CardTitle>Wordlists</CardTitle>
                {routineDetailQ.data?.wordlistRequired && <Badge variant="outline">needed</Badge>}
                <label className="flex items-center gap-2 text-xs text-muted-foreground cursor-pointer">
                  <input
                    type="checkbox"
                    checked={showAllWordlists}
                    onChange={(e) => setShowAllWordlists(e.target.checked)}
                    className="h-3 w-3"
                  />
                  Show all
                </label>
              </CardHeader>
              <CardContent>
                {routineDetailQ.data?.wordlistRequired && wordlistIds.length === 0 && (
                  <div className="text-xs text-warning mb-2">
                    This routine is meant to run with a wordlist; the job will probably fail
                    without one.
                  </div>
                )}
                {routineKey === "fuzz" && wordlistIds.length > 1 && (
                  <div className="text-xs text-warning mb-2">
                    ffuf takes exactly one wordlist: a job with several fails at the worker.
                  </div>
                )}
                {visibleWordlists.length === 0 ? (
                  <div className="text-xs text-muted-foreground">
                    No wordlists{" "}
                    {!showAllWordlists && routineDetailQ.data?.vulnerabilityId
                      ? "for this routine's vulnerability"
                      : "available"}
                    .
                  </div>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {visibleWordlists.map((w) => {
                      const sel = wordlistIds.includes(w.id);
                      return (
                        <button
                          key={w.id}
                          type="button"
                          onClick={() =>
                            setWordlistIds((ids) =>
                              ids.includes(w.id)
                                ? ids.filter((i) => i !== w.id)
                                : [...ids, w.id],
                            )
                          }
                          className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs transition-colors ${
                            sel
                              ? "border-foreground bg-foreground/10 text-foreground"
                              : "border-border hover:bg-accent"
                          }`}
                        >
                          <FileText className="h-3 w-3" />
                          <span className="font-mono">{w.name || `#${w.id}`}</span>
                          <span className="text-muted-foreground">
                            {w.numberOfLines.toLocaleString()} lines · {formatBytes(w.sizeBytes)}
                          </span>
                          {sel && <X className="h-3 w-3" />}
                        </button>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Match & Replace */}
          <Card>
            <CardHeader>
              <CardTitle>Match & Replace</CardTitle>
            </CardHeader>
            <CardContent>
              <MRRulesEditor rules={mrRules} onChange={setMrRules} />
            </CardContent>
          </Card>
        </div>

        {/* Side column */}
        <div className="space-y-4">
          {/* Generic */}
          <Card>
            <CardHeader>
              <CardTitle>Generic</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="space-y-1.5">
                <Label>Verbosity</Label>
                <Select value={verbosity} onValueChange={(v) => setVerbosity(v as Verbosity)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="SUMMARIZED">Summarized</SelectItem>
                    <SelectItem value="NORMAL">Normal</SelectItem>
                    <SelectItem value="VERBOSE">Verbose</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label>
                  Priority ({MIN_JOB_PRIORITY}-{MAX_JOB_PRIORITY})
                </Label>
                <Input
                  type="number"
                  min={MIN_JOB_PRIORITY}
                  max={MAX_JOB_PRIORITY}
                  step={1}
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                />
                <div className="text-[11px] text-muted-foreground">
                  {EAGER_PRIORITY} and above are dispatched before all the others.
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>Rate (req/s)</Label>
                <Input
                  type="number"
                  min={1}
                  step={1}
                  value={rate}
                  onChange={(e) => setRate(e.target.value)}
                  placeholder={tightest ? `1 - ${tightest.value}` : "required"}
                />
                <div className="text-[11px] text-muted-foreground">
                  {tightest
                    ? `At most ${tightest.value} (${tightest.name}). ${rateLimits
                        .map((l) => `${l.name}: ${l.value}`)
                        .join(" · ")}`
                    : "Required, at least 1."}
                </div>
              </div>
            </CardContent>
          </Card>

          {/* NARROW-only */}
          {scope.scope === "NARROW" && (
            <Card>
              <CardHeader>
                <CardTitle>Narrow scope</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-1.5">
                  <Label>Tenant</Label>
                  <Select value={tenantId} onValueChange={setTenantId}>
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_TENANT}>No tenant</SelectItem>
                      {tenantsQ.data?.content.map((t) => (
                        <SelectItem key={t.id} value={String(t.id)}>
                          {t.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label>Duration</Label>
                  <RadioGroup value={duration} onValueChange={(v) => setDuration(v as Duration)}>
                    <label className="flex items-center gap-2 text-xs cursor-pointer">
                      <RadioGroupItem value="SHORT" />
                      Short
                    </label>
                    <label className="flex items-center gap-2 text-xs cursor-pointer">
                      <RadioGroupItem value="LONG" />
                      Long
                    </label>
                  </RadioGroup>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>

      {/* Sticky footer */}
      <div className="sticky bottom-0 mt-6 -mx-6 px-6 py-3 border-t border-border bg-background/95 backdrop-blur flex items-center justify-end gap-2">
        <Button variant="ghost" onClick={() => navigate({ to: ".." })}>
          Cancel
        </Button>
        <Button
          variant="outline"
          onClick={saveDraft}
          disabled={!canSubmit || createMut.isPending}
        >
          <Save className="h-3.5 w-3.5" />
          Save as draft
        </Button>
        <Button
          onClick={saveAndQueue}
          disabled={!canSubmit || createMut.isPending || queueMut.isPending}
        >
          <Play className="h-3.5 w-3.5" />
          Save and queue
        </Button>
      </div>
    </PageContainer>
  );
}

/** The stored request as the raw message bytes (head, then the body), base64. */
async function rawRequestBase64(requestId: number): Promise<string> {
  const raw = await requestsApi.raw(requestId);
  const head = base64ToBytes(raw.rawHeadBase64);
  let body = base64ToBytes(raw.rawBodyBase64);
  if (raw.bodyStoredAsFile) {
    const blob = await requestsApi.bodyBlob(requestId);
    body = new Uint8Array(await blob.arrayBuffer());
  }
  return bytesToBase64(concatBytes(head, body));
}

/** Poll the job until its config files are READY or FAILED (or give up after ~30s). */
async function waitForConfigFiles(jobId: number): Promise<ConfigFileStatus | null> {
  for (let i = 0; i < 30; i++) {
    const job = await jobsApi.get(jobId);
    if (job.configFileStatus === "READY" || job.configFileStatus === "FAILED") {
      return job.configFileStatus;
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  return null;
}

function parseIdsCsv(raw: string | undefined): number[] {
  if (!raw) return [];
  return raw
    .split(",")
    .map((s) => Number(s.trim()))
    .filter(Number.isFinite);
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return {};
  }
}
