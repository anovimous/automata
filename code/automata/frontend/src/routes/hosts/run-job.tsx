import * as React from "react";
import { useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { ArrowLeft, FileText, Play, Save, X } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
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
  HttpJobCreationRequest,
  MatchAndReplace,
  RoutineSummary,
  Verbosity,
  Duration,
} from "@/types/domain";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

type RunJobSearch = {
  kind?: string;
  requestIds?: string;
  equalitySetIds?: string;
  hostIds?: string;
};

export function RunJobRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const search = useSearch({ strict: false }) as RunJobSearch;
  return (
    <RunJobForm
      programId={programId}
      hostId={hostId}
      search={search}
      mode="host"
    />
  );
}

// =============================================================================
// Component
// =============================================================================

interface RunJobFormProps {
  programId?: number;
  hostId?: number;
  search: RunJobSearch;
  mode: "host" | "global";
}

export function RunJobForm({ programId, hostId, search, mode }: RunJobFormProps) {
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId ?? null);
  const hostQ = useHost(hostId ?? null);

  // ---- Selection ----
  const selection: RunJobSelection = React.useMemo(() => {
    if (mode === "global") return { kind: "global" };

    const requestIds = parseIdsCsv(search.requestIds);
    const equalitySetIds = parseIdsCsv(search.equalitySetIds);
    const hostIds = parseIdsCsv(search.hostIds);

    const hostName = hostQ.data?.host;
    const pid = programId ?? 0;
    const hid = hostId ?? 0;

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
    if (search.kind === "single-equality-set" && equalitySetIds.length === 1) {
      return {
        kind: "single-equality-set",
        programId: pid,
        hostId: hid,
        equalitySetId: equalitySetIds[0],
        hostName,
      };
    }
    if (search.kind === "multiple-equality-sets" || equalitySetIds.length > 1) {
      return {
        kind: "multiple-equality-sets",
        programId: pid,
        hostId: hid,
        equalitySetIds,
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
  }, [mode, search, programId, hostId, hostQ.data?.host]);

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
  const [priority, setPriority] = React.useState<string>("");
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
  const tenantsQ = useTenants(mode === "host" && hostId ? hostId : null);
  const [tenantId, setTenantId] = React.useState<string>("");
  const [duration, setDuration] = React.useState<Duration>("SHORT");

  // ---- Top bar ----
  useSetTopBar(
    mode === "host"
      ? [
          { label: "Programs", to: "/programs" },
          {
            label: programQ.data?.name ?? `#${programId}`,
            to: `/programs/${programId}` as string,
          },
          {
            label: hostQ.data?.host ?? `Host #${hostId}`,
            to: `/programs/${programId}/hosts/${hostId}` as string,
          },
          { label: "Run job" },
        ]
      : [{ label: "Jobs", to: "/jobs" }, { label: "Run global job" }],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId, mode],
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
        priority: priority ? Number(priority) : undefined,
        rate: rate ? Number(rate) : undefined,
        routineId: routineId ?? undefined,
        wordlistsIds: wordlistIds.length ? wordlistIds : undefined,
        targetSelector: scope.targetSelector ?? undefined,
        matchAndReplace: mrRules.length ? mrRules : undefined,
        customConfig,
      },
    };

    if (scope.scope === "NARROW") {
      base.narrowJobDetails = {
        duration,
        hostId: hostId ?? undefined,
        tenantId: tenantId ? Number(tenantId) : undefined,
      };
    } else if (scope.scope === "WIDE") {
      base.wideJobDetails = { programId: programId ?? undefined };
    } else {
      base.globalJobDetails = {};
    }

    return base;
  }

  const canSubmit =
    scope.backendReady &&
    routineId !== null &&
    (spec ? validationErrors.length === 0 : true) &&
    (scope.scope !== "NARROW" || tenantId !== "" || !tenantsQ.data?.content?.length);

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

          {/* Routine */}
          <Card>
            <CardHeader>
              <CardTitle>Routine</CardTitle>
              {routineDetailQ.data?.overhead && (
                <Badge variant="muted">{routineDetailQ.data.overhead} overhead</Badge>
              )}
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
                      the custom config as a raw JSON object.
                    </p>
                    <MonacoJsonEditor value={rawConfig} onChange={setRawConfig} />
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
                          <span className="font-mono">{w.name}</span>
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
                <Label>Priority</Label>
                <Input
                  type="number"
                  value={priority}
                  onChange={(e) => setPriority(e.target.value)}
                  placeholder="—"
                />
              </div>
              <div className="space-y-1.5">
                <Label>Rate (req/s)</Label>
                <Input
                  type="number"
                  value={rate}
                  onChange={(e) => setRate(e.target.value)}
                  placeholder="—"
                />
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
                      <SelectValue placeholder={tenantsQ.data?.content?.length ? "—" : "No tenants"} />
                    </SelectTrigger>
                    <SelectContent>
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
