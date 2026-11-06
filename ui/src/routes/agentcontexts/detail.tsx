import { contextDefaultsProblem } from "@/lib/jobRules";
import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Copy, Plus, Save, Trash2, X } from "lucide-react";
import {
  useAddContextItem,
  useAddContextTenant,
  useAgentContext,
  useDeleteAgentContext,
  usePatchAgentContext,
  useRemoveContextItem,
  useRemoveContextTenant,
  useResolvedTargets,
} from "@/hooks/useAgentContexts";
import { useHost } from "@/hooks/useHosts";
import { useJobs } from "@/hooks/useJobs";
import { useTenants } from "@/hooks/useTenants";
import { useRoutines } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input, Label, Textarea } from "@/components/ui/input";
import { Switch } from "@/components/ui/primitives";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RoutineMultiPicker } from "@/components/common/routine-multi-picker";
import { ApprovalPill, JobStatePill } from "@/components/job/pills";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { ErrorState } from "@/components/common/states";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { relativeTime } from "@/lib/utils";
import type {
  AgentContextItemType,
  Duration,
  RepresentativeStrategy,
  Verbosity,
} from "@/types/domain";

const NONE = "__none__";

export function AgentContextDetailRoute() {
  const { contextId } = useParams({ strict: false }) as { contextId: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const ctxQ = useAgentContext(contextId);
  const ctx = ctxQ.data;
  const hostQ = useHost(ctx?.hostId ?? null);
  const programId = hostQ.data?.programId;
  const resolvedQ = useResolvedTargets(contextId);
  const jobsQ = useJobs(
    { agentContextId: contextId },
    { page: 0, size: 8, sort: ["creationDate,desc"] },
    { poll: false },
  );
  const patchMut = usePatchAgentContext();
  const deleteMut = useDeleteAgentContext();
  const addItemMut = useAddContextItem();
  const removeItemMut = useRemoveContextItem();
  const addTenantMut = useAddContextTenant();
  const removeTenantMut = useRemoveContextTenant();
  const tenantsQ = useTenants(ctx?.hostId ?? null, { page: 0, size: 200 });
  const routinesQ = useRoutines(undefined, undefined, { page: 0, size: 200 });

  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [autoApproval, setAutoApproval] = React.useState(false);
  const [strategy, setStrategy] = React.useState<RepresentativeStrategy>("LATEST");
  const [routineIds, setRoutineIds] = React.useState<number[]>([]);
  const [duration, setDuration] = React.useState(NONE);
  const [verbosity, setVerbosity] = React.useState(NONE);
  const [priority, setPriority] = React.useState("");
  const [maxRate, setMaxRate] = React.useState("");
  const [defaultJobRate, setDefaultJobRate] = React.useState("");
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  const [itemType, setItemType] = React.useState<AgentContextItemType>("REQUEST");
  const [itemId, setItemId] = React.useState("");
  const [tenantToAdd, setTenantToAdd] = React.useState("");

  React.useEffect(() => {
    if (ctx) {
      setName(ctx.name);
      setDescription(ctx.description ?? "");
      setAutoApproval(ctx.defaultAutoApproval);
      setStrategy(ctx.representativeStrategy);
      setRoutineIds(ctx.allowedRoutineIds);
      setDuration(ctx.defaultDuration ?? NONE);
      setVerbosity(ctx.defaultVerbosity ?? NONE);
      setPriority(ctx.defaultPriority?.toString() ?? "");
      setMaxRate(ctx.maxRate.toString());
      setDefaultJobRate(ctx.defaultJobRate.toString());
    }
  }, [ctx]);

  useSetTopBar(
    [{ label: "Agent contexts", to: "/agent-contexts" }, { label: ctx?.name ?? `#${contextId}` }],
    null,
    [ctx?.name, contextId],
  );

  if (ctxQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load the agent context"
          description={ctxQ.error instanceof Error ? ctxQ.error.message : ""}
        />
      </PageContainer>
    );
  }

  async function run(label: string, fn: () => Promise<unknown>) {
    try {
      await fn();
      toast({ title: label, variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : `${label} failed`;
      toast({ title: `${label} failed`, description: msg, variant: "destructive" });
    }
  }

  const defaultsProblem = contextDefaultsProblem(maxRate, defaultJobRate, priority);

  function save() {
    if (!ctx || defaultsProblem) return;
    return run("Context saved", () =>
      patchMut.mutateAsync({
        id: contextId,
        req: {
          name: name.trim() || undefined,
          description: description.trim() || undefined,
          setDescriptionNull: !description.trim() && !!ctx.description,
          defaultAutoApproval: autoApproval,
          representativeStrategy: strategy,
          allowedRoutineIds: routineIds,
          defaultDuration: duration === NONE ? undefined : (duration as Duration),
          setDefaultDurationNull: duration === NONE && !!ctx.defaultDuration,
          defaultVerbosity: verbosity === NONE ? undefined : (verbosity as Verbosity),
          setDefaultVerbosityNull: verbosity === NONE && !!ctx.defaultVerbosity,
          defaultPriority: priority ? Number(priority) : undefined,
          setDefaultPriorityNull: !priority && ctx.defaultPriority != null,
          maxRate: maxRate ? Number(maxRate) : undefined,
          defaultJobRate: defaultJobRate ? Number(defaultJobRate) : undefined,
        },
      }),
    );
  }

  const idValue = Number(itemId);
  const contextTenantIds = new Set(ctx?.tenantIds ?? []);
  const tenantName = (id: number) =>
    tenantsQ.data?.content.find((t) => t.id === id)?.name ?? `Tenant #${id}`;
  const routineKey = (id: number) =>
    routinesQ.data?.content.find((r) => r.id === id)?.key ?? `#${id}`;

  function requestLink(requestId: number) {
    return programId && ctx ? (
      <Link
        to="/programs/$programId/hosts/$hostId/requests/$requestId"
        params={{ programId, hostId: ctx.hostId, requestId }}
        className="font-mono hover:underline"
      >
        Request #{requestId}
      </Link>
    ) : (
      <span className="font-mono">Request #{requestId}</span>
    );
  }

  function setLink(equalitySetId: number) {
    return programId && ctx ? (
      <Link
        to="/programs/$programId/hosts/$hostId/equality-sets/$equalitySetId"
        params={{ programId, hostId: ctx.hostId, equalitySetId }}
        className="font-mono hover:underline"
      >
        Equality set #{equalitySetId}
      </Link>
    ) : (
      <span className="font-mono">Equality set #{equalitySetId}</span>
    );
  }

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/agent-contexts" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to contexts
      </Button>

      <PageHeader
        title={ctx?.name ?? `Context #${contextId}`}
        description={
          ctx && (
            <span className="flex items-center gap-2 flex-wrap">
              <span className="font-mono text-xs">{ctx.uuid}</span>
              <Button
                variant="ghost"
                size="sm"
                className="h-6 px-1.5 text-xs"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(ctx.uuid);
                    toast({ title: "UUID copied", variant: "success" });
                  } catch {
                    toast({ title: "Could not copy", variant: "destructive" });
                  }
                }}
              >
                <Copy className="h-3 w-3" />
                Copy
              </Button>
              {hostQ.data && programId && (
                <Link
                  to="/programs/$programId/hosts/$hostId"
                  params={{ programId, hostId: ctx.hostId }}
                  className="font-mono text-xs hover:underline"
                >
                  {hostQ.data.host}
                </Link>
              )}
            </span>
          )
        }
        actions={
          <>
            <Button onClick={save} disabled={patchMut.isPending || !ctx || !!defaultsProblem}>
              <Save className="h-3.5 w-3.5" />
              Save
            </Button>
            <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </>
        }
      />

      {!ctx ? (
        <div className="text-xs text-muted-foreground">Loading…</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Settings</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-1.5">
                  <Label>Name</Label>
                  <Input value={name} onChange={(e) => setName(e.target.value)} />
                </div>
                <div className="space-y-1.5">
                  <Label>Description</Label>
                  <Textarea
                    rows={2}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label>Representative of a set</Label>
                    <Select
                      value={strategy}
                      onValueChange={(v) => setStrategy(v as RepresentativeStrategy)}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="LATEST">Latest request</SelectItem>
                        <SelectItem value="AI_JUDGEMENT">Agent chooses</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="flex items-end justify-between gap-2 pb-1.5">
                    <Label>Auto-approve jobs</Label>
                    <Switch checked={autoApproval} onCheckedChange={setAutoApproval} />
                  </div>
                </div>
                <div className="text-[11px] text-muted-foreground">
                  Auto-approval of a request that is in the context through an equality set is
                  decided by that set; this is the default for the others.
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label>Default duration</Label>
                    <Select value={duration} onValueChange={setDuration}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>—</SelectItem>
                        <SelectItem value="SHORT">Short</SelectItem>
                        <SelectItem value="LONG">Long</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1.5">
                    <Label>Default verbosity</Label>
                    <Select value={verbosity} onValueChange={setVerbosity}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>— (summarized)</SelectItem>
                        <SelectItem value="SUMMARIZED">Summarized</SelectItem>
                        <SelectItem value="NORMAL">Normal</SelectItem>
                        <SelectItem value="VERBOSE">Verbose</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1.5">
                    <Label>Default priority (1-5)</Label>
                    <Input
                      type="number"
                      min={1}
                      max={5}
                      value={priority}
                      onChange={(e) => setPriority(e.target.value)}
                      placeholder="1"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>Max rate (req/s)</Label>
                    <Input
                      type="number"
                      min={1}
                      value={maxRate}
                      onChange={(e) => setMaxRate(e.target.value)}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>Default job rate (req/s)</Label>
                    <Input
                      type="number"
                      min={1}
                      value={defaultJobRate}
                      onChange={(e) => setDefaultJobRate(e.target.value)}
                    />
                  </div>
                  {defaultsProblem && (
                    <div className="col-span-2 text-[11px] text-destructive">
                      {defaultsProblem}
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Allowed routines</CardTitle>
                <Badge variant="muted">{routineIds.length}</Badge>
              </CardHeader>
              <CardContent className="space-y-2">
                <RoutineMultiPicker value={routineIds} onChange={setRoutineIds} />
                <div className="text-[11px] text-muted-foreground">
                  Applied with Save. The agent can only create jobs with these routines.
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Tenants</CardTitle>
                <Badge variant="muted">{ctx.tenantIds.length}</Badge>
              </CardHeader>
              <CardContent className="space-y-3">
                {ctx.tenantIds.length === 0 ? (
                  <div className="text-xs text-muted-foreground">
                    No tenant: the agent has no authentication data to use.
                  </div>
                ) : (
                  <ul className="space-y-1">
                    {ctx.tenantIds.map((id) => (
                      <li key={id} className="flex items-center gap-2 text-sm">
                        <span className="flex-1">{tenantName(id)}</span>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label="Remove tenant"
                          onClick={() =>
                            run("Tenant removed", () =>
                              removeTenantMut.mutateAsync({ id: contextId, tenantId: id }),
                            )
                          }
                        >
                          <X className="h-3.5 w-3.5" />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="flex items-center gap-2">
                  <Select value={tenantToAdd} onValueChange={setTenantToAdd}>
                    <SelectTrigger className="flex-1">
                      <SelectValue placeholder="Add a tenant of the host" />
                    </SelectTrigger>
                    <SelectContent>
                      {tenantsQ.data?.content
                        .filter((t) => !contextTenantIds.has(t.id))
                        .map((t) => (
                          <SelectItem key={t.id} value={String(t.id)}>
                            {t.name}
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                  <Button
                    disabled={!tenantToAdd || addTenantMut.isPending}
                    onClick={async () => {
                      await run("Tenant added", () =>
                        addTenantMut.mutateAsync({
                          id: contextId,
                          tenantId: Number(tenantToAdd),
                        }),
                      );
                      setTenantToAdd("");
                    }}
                  >
                    <Plus className="h-3.5 w-3.5" />
                    Add
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Items</CardTitle>
                <Badge variant="muted">{ctx.items.length}</Badge>
              </CardHeader>
              <CardContent className="space-y-3">
                {ctx.items.length === 0 ? (
                  <div className="text-xs text-muted-foreground">
                    Empty: the agent can't see any request yet. Add requests or equality sets of
                    this host.
                  </div>
                ) : (
                  <ul className="divide-y divide-border rounded-md border border-border">
                    {ctx.items.map((item) => (
                      <li key={item.id} className="flex items-center gap-2 px-2.5 py-1.5 text-sm">
                        <Badge variant="outline">
                          {item.itemType === "REQUEST" ? "Request" : "Equality set"}
                        </Badge>
                        <span className="flex-1">
                          {item.itemType === "REQUEST" && item.requestId != null
                            ? requestLink(item.requestId)
                            : item.equalitySetId != null
                              ? setLink(item.equalitySetId)
                              : "—"}
                        </span>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label="Remove item"
                          onClick={() =>
                            run("Item removed", () =>
                              removeItemMut.mutateAsync({ id: contextId, itemId: item.id }),
                            )
                          }
                        >
                          <X className="h-3.5 w-3.5" />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
                <div className="flex items-center gap-2">
                  <Select
                    value={itemType}
                    onValueChange={(v) => setItemType(v as AgentContextItemType)}
                  >
                    <SelectTrigger className="w-[150px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="REQUEST">Request</SelectItem>
                      <SelectItem value="EQUALITY_SET">Equality set</SelectItem>
                    </SelectContent>
                  </Select>
                  <Input
                    value={itemId}
                    onChange={(e) => setItemId(e.target.value)}
                    inputMode="numeric"
                    placeholder="id"
                    className="font-mono"
                  />
                  <Button
                    disabled={!Number.isInteger(idValue) || idValue <= 0 || addItemMut.isPending}
                    onClick={async () => {
                      await run("Item added", () =>
                        addItemMut.mutateAsync({
                          id: contextId,
                          req:
                            itemType === "REQUEST"
                              ? { itemType, requestId: idValue }
                              : { itemType, equalitySetId: idValue },
                        }),
                      );
                      setItemId("");
                    }}
                  >
                    <Plus className="h-3.5 w-3.5" />
                    Add
                  </Button>
                </div>
                <div className="text-[11px] text-muted-foreground">
                  Requests can also be added from the request list of the host.
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Resolved requests</CardTitle>
                <Badge variant="muted">{resolvedQ.data?.length ?? 0}</Badge>
              </CardHeader>
              <CardContent className="p-0">
                {resolvedQ.data && resolvedQ.data.length > 0 ? (
                  <table className="w-full text-[12px]">
                    <thead>
                      <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground">
                        <th className="px-4 py-2 font-medium">Request</th>
                        <th className="px-2 py-2 font-medium">Source</th>
                        <th className="px-2 py-2 font-medium">Job-eligible</th>
                        <th className="px-4 py-2 font-medium">Approval</th>
                      </tr>
                    </thead>
                    <tbody>
                      {resolvedQ.data.map((t) => (
                        <tr key={t.requestId} className="border-t border-border">
                          <td className="px-4 py-1.5">{requestLink(t.requestId)}</td>
                          <td className="px-2 py-1.5 text-muted-foreground">
                            {t.sourceItemType === "EQUALITY_SET" && t.sourceEqualitySetId != null
                              ? `Set #${t.sourceEqualitySetId}`
                              : "Request"}
                          </td>
                          <td className="px-2 py-1.5">
                            {t.automataJobEligible ? (
                              <Badge variant="success">Yes</Badge>
                            ) : (
                              <Badge variant="muted">No</Badge>
                            )}
                          </td>
                          <td className="px-4 py-1.5">
                            {t.autoApproval ? (
                              <Badge variant="success">Auto</Badge>
                            ) : (
                              <Badge variant="warning">Manual</Badge>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="p-4 text-xs text-muted-foreground">
                    The requests the agent can see and create jobs on, after resolving the items.
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Jobs of this context</CardTitle>
                <Link to="/jobs" search={{ agentContextId: contextId, tab: "all" }}>
                  <Button variant="ghost" size="sm" className="text-xs">
                    All ({jobsQ.data?.totalElements ?? 0})
                  </Button>
                </Link>
              </CardHeader>
              <CardContent className="p-0">
                {jobsQ.data && jobsQ.data.content.length > 0 ? (
                  <ul className="divide-y divide-border">
                    {jobsQ.data.content.map((j) => (
                      <li key={j.id}>
                        <Link
                          to="/jobs/$jobId"
                          params={{ jobId: j.id }}
                          className="flex items-center gap-3 px-4 py-2 hover:bg-accent/40 transition-colors text-sm"
                        >
                          <JobStatePill state={j.currentState} />
                          <span className="font-mono text-xs flex-1 truncate">
                            #{j.id} · {j.routineKey ?? routineKey(j.routineId ?? 0)}
                          </span>
                          {j.currentState === "DRAFT" && <ApprovalPill approved={j.approved} />}
                          <span className="text-xs text-muted-foreground">
                            {relativeTime(j.creationDate)}
                          </span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="p-4 text-xs text-muted-foreground">
                    The agent has not created any job in this context yet.
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete agent context?"
        description="The agent loses access. The core refuses (409) while jobs or findings of the agent still refer to the context."
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(contextId);
            toast({ title: "Context deleted", variant: "success" });
            navigate({ to: "/agent-contexts" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
