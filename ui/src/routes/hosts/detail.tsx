import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  Users,
  FileText,
  GitMerge,
  Cpu,
  Play,
  Plus,
  Layers as LayersIcon,
  Bot,
  Download,
  ShieldAlert,
  Trash2,
  Pencil,
} from "lucide-react";
import { useHost, useDeleteHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useTenants } from "@/hooks/useTenants";
import { useRequests } from "@/hooks/useRequests";
import { useJobs } from "@/hooks/useJobs";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ErrorState } from "@/components/common/states";
import { AddRawRequestDialog } from "@/components/request/add-raw-request-dialog";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { EditHostDialog } from "@/components/host/edit-host-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { cn } from "@/lib/cn";
import { exportApi } from "@/api";
import { DELETE_HOST_BACKEND_READY } from "@/config/features";
import { useAgentContexts } from "@/hooks/useAgentContexts";
import { useFindings } from "@/hooks/useFindings";
import { useEqualizations } from "@/hooks/useRequests";

export function HostDetailRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const deleteMut = useDeleteHost();

  const tenants = useTenants(hostId, { page: 0, size: 1 });
  const requests = useRequests({ hostId }, { page: 0, size: 1 });
  const jobs = useJobs({ hostId }, { page: 0, size: 1 }, { poll: false });
  const activeJobs = useJobs(
    { hostId, currentStates: ["RUNNING", "QUEUED", "TOQUEUE", "PAUSED"] },
    { page: 0, size: 1 },
    { poll: false },
  );
  const contexts = useAgentContexts(hostId, { page: 0, size: 1 });
  const findings = useFindings({ hostId }, { page: 0, size: 1 });
  const equalizations = useEqualizations(hostId);

  const [addRequestOpen, setAddRequestOpen] = React.useState(false);
  const [editOpen, setEditOpen] = React.useState(false);
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  useSetTopBar(
    [
      { label: "Programs", to: "/programs" },
      {
        label: programQ.data?.name ?? `#${programId}`,
        to: `/programs/${programId}` as string,
      },
      { label: hostQ.data?.host ?? `#${hostId}` },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  if (hostQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load host"
          description={hostQ.error instanceof Error ? hostQ.error.message : ""}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title={<span className="font-mono">{hostQ.data?.host ?? `Host #${hostId}`}</span>}
        description={
          <div className="flex items-center gap-2 flex-wrap">
            {hostQ.data?.scope && (
              <Badge variant={hostQ.data.scope === "WILDCARD" ? "accent" : "outline"}>
                {hostQ.data.scope}
              </Badge>
            )}
            {hostQ.data?.outOfScope ? (
              <Badge variant="destructive">Out of scope</Badge>
            ) : (
              <Badge variant="success">In scope</Badge>
            )}
            {hostQ.data?.hostRateLimit !== undefined && hostQ.data?.hostRateLimit !== null && (
              <span className="text-xs text-muted-foreground">
                Rate: {hostQ.data.hostRateLimit} req/s
              </span>
            )}
          </div>
        }
        actions={
          <>
            <Button
              variant="default"
              onClick={() =>
                navigate({
                  to: "/programs/$programId/hosts/$hostId/run-job",
                  params: { programId, hostId },
                  search: { kind: "single-host" },
                })
              }
            >
              <Play className="h-3.5 w-3.5" />
              Run job
            </Button>
            <Button variant="outline" onClick={() => setAddRequestOpen(true)}>
              <Plus className="h-3.5 w-3.5" />
              Add request
            </Button>
            <a href={exportApi.bodyPropertiesUrl({ hostId })} download>
              <Button variant="outline" title="Distinct body property names of this host, one per line">
                <Download className="h-3.5 w-3.5" />
                Export properties
              </Button>
            </a>
            <Button variant="outline" size="icon" onClick={() => setEditOpen(true)} aria-label="Edit host">
              <Pencil className="h-3.5 w-3.5" />
            </Button>
            {DELETE_HOST_BACKEND_READY && (
              <Button
                variant="outline"
                size="icon"
                onClick={() => setDeleteOpen(true)}
                aria-label="Delete host"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            )}
          </>
        }
      />

      {/* Host quick info */}
      <Card className="mb-5">
        <CardContent className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4">
          <KV label="Rate limit" value={hostQ.data?.hostRateLimit ?? "—"} />
          <KV label="Short burst" value={hostQ.data?.shortRateLimit ?? "—"} />
          <KV label="Long burst" value={hostQ.data?.longRateLimit ?? "—"} />
          <KV label="Level" value={hostQ.data?.level ?? "—"} />
          {(hostQ.data?.description ||
            (hostQ.data?.technologies?.length ?? 0) > 0 ||
            hostQ.data?.notes) && (
            <div className="col-span-2 md:col-span-4 space-y-2 border-t border-border pt-3">
              {hostQ.data?.description && (
                <div>
                  <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                    Description
                  </div>
                  <div className="text-sm mt-1 whitespace-pre-wrap">{hostQ.data.description}</div>
                </div>
              )}
              {(hostQ.data?.technologies?.length ?? 0) > 0 && (
                <div>
                  <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1">
                    Technologies
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {hostQ.data?.technologies?.map((t) => (
                      <Badge key={t} variant="muted">
                        {t}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
              {hostQ.data?.notes && (
                <div>
                  <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                    Notes
                  </div>
                  <div className="text-sm mt-1 whitespace-pre-wrap">{hostQ.data.notes}</div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Sub-pages */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <NavCard
          to="/programs/$programId/hosts/$hostId/requests"
          params={{ programId, hostId }}
          icon={FileText}
          title="Requests"
          count={requests.data?.totalElements ?? 0}
          accent="primary"
        />
        <NavCard
          to="/programs/$programId/hosts/$hostId/equality-sets"
          params={{ programId, hostId }}
          icon={GitMerge}
          title="Equalizations"
          count={equalizations.data?.length ?? 0}
        />
        <NavCard
          to="/programs/$programId/hosts/$hostId/tenants"
          params={{ programId, hostId }}
          icon={Users}
          title="Tenants"
          count={tenants.data?.totalElements ?? 0}
        />
        <NavCard
          to="/programs/$programId/hosts/$hostId/jobs"
          params={{ programId, hostId }}
          icon={Cpu}
          title="Jobs"
          count={jobs.data?.totalElements ?? 0}
          subtitle={
            (activeJobs.data?.totalElements ?? 0) > 0
              ? `${activeJobs.data?.totalElements} active`
              : undefined
          }
        />
      </div>

      <div className="mt-5 grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Link to="/agent-contexts" search={{ hostId }} className="block">
          <Card className="hover:bg-accent/30 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <Bot className="h-4 w-4 text-muted-foreground" />
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium">
                  Agent contexts
                  <span className="ml-2 font-mono text-xs text-muted-foreground">
                    {contexts.data?.totalElements ?? 0}
                  </span>
                </div>
                <div className="text-xs text-muted-foreground">
                  What an AI agent may test on this host.
                </div>
              </div>
            </CardContent>
          </Card>
        </Link>
        <Link to="/findings" search={{ hostId }} className="block">
          <Card className="hover:bg-accent/30 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <ShieldAlert className="h-4 w-4 text-muted-foreground" />
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium">
                  Findings
                  <span className="ml-2 font-mono text-xs text-muted-foreground">
                    {findings.data?.totalElements ?? 0}
                  </span>
                </div>
                <div className="text-xs text-muted-foreground">
                  Vulnerabilities reported on this host.
                </div>
              </div>
            </CardContent>
          </Card>
        </Link>
        <Link to="/programs/$programId" params={{ programId }} className="block">
          <Card className="hover:bg-accent/30 transition-colors">
            <CardContent className="p-4 flex items-center gap-3">
              <LayersIcon className="h-4 w-4 text-muted-foreground" />
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium">
                  Up to {programQ.data?.name ?? "program"}
                </div>
                <div className="text-xs text-muted-foreground">
                  Browse other hosts in this program.
                </div>
              </div>
            </CardContent>
          </Card>
        </Link>
      </div>

      <AddRawRequestDialog
        open={addRequestOpen}
        onOpenChange={setAddRequestOpen}
        hostId={hostId}
        programId={programId}
        hostName={hostQ.data?.host}
      />

      <EditHostDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        hostId={hostId}
        programId={programId}
      />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete host?"
        description="All tenants, requests, and jobs under this host will be affected."
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync({ id: hostId, programId });
            toast({ title: "Host deleted", variant: "success" });
            navigate({ to: "/programs/$programId", params: { programId } });
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

function NavCard({
  to,
  params,
  icon: Icon,
  title,
  count,
  subtitle,
  accent,
}: {
  to: string;
  params: Record<string, number | string>;
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  count?: number;
  subtitle?: string;
  accent?: "primary";
}) {
  return (
    <Link to={to as never} params={params as never} className="block">
      <Card
        className={cn(
          "transition-colors hover:bg-accent/30",
          accent === "primary" && "border-foreground/20",
        )}
      >
        <CardContent className="p-4">
          <div className="flex items-center gap-2">
            <Icon className="h-3.5 w-3.5 text-muted-foreground" />
            <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
              {title}
            </div>
          </div>
          <div className="text-3xl font-medium tabular-nums tracking-tight mt-2">
            {count ?? "—"}
          </div>
          {subtitle && <div className="text-xs text-success mt-0.5">{subtitle}</div>}
        </CardContent>
      </Card>
    </Link>
  );
}
