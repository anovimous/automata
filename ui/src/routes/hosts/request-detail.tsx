import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { Download, Link2, Play, Plus, ShieldAlert, Trash2 } from "lucide-react";
import {
  useAssociateResponse,
  useDeleteRequest,
  useRawRequest,
  useRequestMetadata,
} from "@/hooks/useRequests";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useRawResponse, useResponseMetadata } from "@/hooks/useResponses";
import { useJobs } from "@/hooks/useJobs";
import { useFindings } from "@/hooks/useFindings";
import { useSetTopBar } from "@/components/layout/top-bar";
import { FullBleed, PageHeader } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  CreatorPill,
  FindingStatusPill,
  JobStatePill,
  MethodPill,
  SeverityPill,
  StatusCodePill,
} from "@/components/job/pills";
import { HttpMessageView } from "@/components/request/http-message-view";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { requestsApi, responsesApi } from "@/api";
import { ApiError } from "@/lib/fetcher";
import { formatBytes } from "@/lib/http";
import { formatDate, relativeTime, toBase64 } from "@/lib/utils";
import { DELETE_REQUEST_BACKEND_READY } from "@/config/features";
import { NOT_ELIGIBLE_REASON, isJobEligibleBody } from "@/lib/jobRules";

export function HostRequestDetailRoute() {
  const { programId, hostId, requestId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
    requestId: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const metaQ = useRequestMetadata(requestId);
  const rawQ = useRawRequest(requestId);
  const responseId = metaQ.data?.responseId ?? null;
  const respMetaQ = useResponseMetadata(responseId);
  const respRawQ = useRawResponse(responseId);
  const jobsQ = useJobs(
    { targetRequestId: requestId },
    { page: 0, size: 10 },
    { poll: false },
  );
  const findingsQ = useFindings({ requestId }, { page: 0, size: 10 });
  const deleteMut = useDeleteRequest();

  const [deleteOpen, setDeleteOpen] = React.useState(false);
  const [associateOpen, setAssociateOpen] = React.useState(false);

  useSetTopBar(
    [
      { label: "Programs", to: "/programs" },
      {
        label: programQ.data?.name ?? `#${programId}`,
        to: `/programs/${programId}` as string,
      },
      {
        label: hostQ.data?.host ?? `Host #${hostId}`,
        to: `/programs/${programId}/hosts/${hostId}` as string,
      },
      {
        label: "Requests",
        to: `/programs/${programId}/hosts/${hostId}/requests` as string,
      },
      { label: `#${requestId}` },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId, requestId],
  );

  const m = metaQ.data;
  const line = m?.line;
  const jobEligible = m ? isJobEligibleBody(m.body) : true;

  return (
    <FullBleed>
      <PageHeader
        title={
          <span className="flex items-center gap-3 min-w-0">
            {line?.method && <MethodPill method={line.method} />}
            <span className="font-mono text-base truncate">
              {line?.computatedPath ?? `Request #${requestId}`}
            </span>
          </span>
        }
        actions={
          <>
            <Button
              disabled={!!m && !jobEligible}
              title={m && !jobEligible ? `${NOT_ELIGIBLE_REASON}.` : undefined}
              onClick={() =>
                navigate({
                  to: "/programs/$programId/hosts/$hostId/run-job",
                  params: { programId, hostId },
                  search: { kind: "single-request", requestIds: String(requestId) },
                })
              }
            >
              <Play className="h-3.5 w-3.5" />
              Run job
            </Button>
            <Button
              variant="outline"
              onClick={() =>
                navigate({
                  to: "/findings",
                  search: { requestId, hostId, create: true },
                })
              }
            >
              <ShieldAlert className="h-3.5 w-3.5" />
              Report finding
            </Button>
            {DELETE_REQUEST_BACKEND_READY && (
              <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            )}
          </>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr_1fr] gap-4">
        {/* Metadata pane */}
        <Card className="self-start">
          <CardContent className="p-4 space-y-3">
            <Field label="Method">{line?.method ? <MethodPill method={line.method} /> : "—"}</Field>
            <Field label="Path">
              <span className="font-mono text-xs break-all">{line?.computatedPath ?? "—"}</span>
            </Field>
            <Field label="Target">
              <span className="font-mono text-xs break-all">{line?.target ?? "—"}</span>
            </Field>
            <Field label="Extension">
              <span className="font-mono text-xs">{line?.extension ?? "—"}</span>
            </Field>
            <Field label="Version">{line?.version ?? "—"}</Field>
            <Field label="Content type">
              {m?.body.contentType ? <Badge variant="muted">{m.body.contentType}</Badge> : "—"}
            </Field>
            <Field label="Body">
              <span className="text-xs">
                {m ? `${m.body.storage.toLowerCase()} · ${formatBytes(m.body.sizeBytes)}` : "—"}
              </span>
            </Field>
            <Field label="Source">
              {m?.source ? <Badge variant="outline">{m.source}</Badge> : "—"}
            </Field>
            <Field label="Added">
              <span className="text-xs">{m?.insertionDate ? formatDate(m.insertionDate) : "—"}</span>
            </Field>
            <Field label="Tenant">
              <span className="font-mono tabular-nums text-xs">
                {m?.tenantId ? (
                  <Link
                    to="/programs/$programId/hosts/$hostId/tenants/$tenantId"
                    params={{ programId, hostId, tenantId: m.tenantId }}
                    className="hover:underline"
                  >
                    #{m.tenantId}
                  </Link>
                ) : (
                  "—"
                )}
              </span>
            </Field>
          </CardContent>
        </Card>

        {/* Request */}
        <Card>
          <CardContent className="p-0">
            <div className="px-4 py-2.5 border-b border-border flex items-center justify-between">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                Request
              </div>
              {m?.body.storage !== "NONE" && m && (
                <a href={requestsApi.bodyUrl(requestId)} download>
                  <Button variant="ghost" size="sm">
                    <Download className="h-3.5 w-3.5" />
                    Body
                  </Button>
                </a>
              )}
            </div>
            <HttpMessageView
              isLoading={metaQ.isLoading || rawQ.isLoading}
              headers={m?.headers}
              body={m?.body}
              rawHeadBase64={rawQ.data?.rawHeadBase64}
              rawBodyBase64={rawQ.data?.rawBodyBase64}
              bodyUrl={requestsApi.bodyUrl(requestId)}
              loadBody={() => requestsApi.bodyBlob(requestId)}
            />
          </CardContent>
        </Card>

        {/* Response */}
        <Card>
          <CardContent className="p-0">
            <div className="px-4 py-2.5 border-b border-border flex items-center justify-between">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                Response
              </div>
              {respMetaQ.data?.statusCode && <StatusCodePill code={respMetaQ.data.statusCode} />}
            </div>
            {!responseId ? (
              <div className="p-6 text-xs text-muted-foreground text-center space-y-3">
                <div>No response associated with this request.</div>
                <Button variant="outline" size="sm" onClick={() => setAssociateOpen(true)}>
                  <Link2 className="h-3.5 w-3.5" />
                  Associate a response
                </Button>
              </div>
            ) : (
              <HttpMessageView
                isLoading={respMetaQ.isLoading || respRawQ.isLoading}
                headers={respMetaQ.data?.headers}
                body={respMetaQ.data?.body}
                rawHeadBase64={respRawQ.data?.rawHeadBase64}
                rawBodyBase64={respRawQ.data?.rawBodyBase64}
                bodyUrl={responsesApi.bodyUrl(responseId)}
                loadBody={() => responsesApi.bodyBlob(responseId)}
              />
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mt-4">
        <Card>
          <CardHeader>
            <CardTitle>Jobs that targeted this request</CardTitle>
            <Badge variant="muted">{jobsQ.data?.totalElements ?? 0}</Badge>
          </CardHeader>
          <CardContent className="p-0">
            {jobsQ.data && jobsQ.data.content.length > 0 ? (
              <table className="w-full text-[12px]">
                <tbody>
                  {jobsQ.data.content.map((j) => (
                    <tr key={j.id} className="border-t border-border first:border-t-0">
                      <td className="px-4 py-2">
                        <Link
                          to="/jobs/$jobId"
                          params={{ jobId: j.id }}
                          className="font-mono hover:underline"
                        >
                          #{j.id}
                        </Link>
                      </td>
                      <td className="px-2 py-2 font-mono">{j.routineKey ?? "—"}</td>
                      <td className="px-2 py-2">
                        <CreatorPill creator={j.createdBy} />
                      </td>
                      <td className="px-2 py-2">
                        <JobStatePill state={j.currentState} />
                      </td>
                      <td className="px-4 py-2 text-right text-muted-foreground">
                        {relativeTime(j.creationDate)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-6 text-xs text-muted-foreground text-center">
                No job targeted this request yet.
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Findings</CardTitle>
            <Badge variant="muted">{findingsQ.data?.totalElements ?? 0}</Badge>
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
              <div className="p-6 text-xs text-muted-foreground text-center">
                No findings on this request.
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <AssociateResponseDialog
        open={associateOpen}
        onOpenChange={setAssociateOpen}
        requestId={requestId}
        hostId={hostId}
        programId={programId}
      />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete request?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync({ id: requestId, hostId, programId });
            toast({ title: "Request deleted", variant: "success" });
            navigate({
              to: "/programs/$programId/hosts/$hostId/requests",
              params: { programId, hostId },
            });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </FullBleed>
  );
}

function AssociateResponseDialog({
  open,
  onOpenChange,
  requestId,
  hostId,
  programId,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  requestId: number;
  hostId: number;
  programId: number;
}) {
  const [raw, setRaw] = React.useState("");
  const mut = useAssociateResponse();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) setRaw("");
  }, [open]);

  async function submit() {
    if (!raw.trim()) return;
    try {
      await mut.mutateAsync({
        requestId,
        body: { responseBase64: toBase64(raw) },
        hostId,
        programId,
      });
      toast({ title: "Response associated", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Failed";
      toast({ title: "Could not associate", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Associate a response</DialogTitle>
          <DialogDescription>Paste the full raw HTTP response.</DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4">
          <Textarea
            value={raw}
            onChange={(e) => setRaw(e.target.value)}
            placeholder={"HTTP/1.1 200 OK\nContent-Type: application/json\n\n{…}"}
            className="min-h-[200px] text-[12px]"
          />
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!raw.trim() || mut.isPending}>
            <Plus className="h-3.5 w-3.5" />
            Associate
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1">{label}</div>
      <div className="text-sm">{children}</div>
    </div>
  );
}
