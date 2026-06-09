import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { Play, Shuffle, Trash2 } from "lucide-react";
import { useRequest, useDeleteRequest } from "@/hooks/useRequests";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useRawResponse, useResponse } from "@/hooks/useResponses";
import { useSetTopBar } from "@/components/layout/top-bar";
import { FullBleed, PageHeader } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { MethodPill, StatusCodePill } from "@/components/job/pills";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { fromBase64 } from "@/lib/utils";
import { ApiError } from "@/lib/fetcher";

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
  const reqQ = useRequest(requestId);
  const respQ = useResponse(reqQ.data?.responseId ?? null);
  const rawRespQ = useRawResponse(reqQ.data?.responseId ?? null);
  const deleteMut = useDeleteRequest();

  const [deleteOpen, setDeleteOpen] = React.useState(false);

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

  const r = reqQ.data;
  const rawRequestText = React.useMemo(() => {
    if (!r?.base64Request) return "";
    try {
      return fromBase64(r.base64Request);
    } catch {
      return "";
    }
  }, [r?.base64Request]);
  const rawResponseText = React.useMemo(() => {
    const b64 = rawRespQ.data?.responseBase64;
    if (b64) {
      try {
        return fromBase64(b64);
      } catch {
        return "";
      }
    }
    return rawRespQ.data?.raw ?? "";
  }, [rawRespQ.data?.responseBase64, rawRespQ.data?.raw]);

  return (
    <FullBleed>
      <PageHeader
        title={
          <span className="flex items-center gap-3 min-w-0">
            {r?.method && <MethodPill method={r.method} />}
            <span className="font-mono text-base truncate">
              {r?.computatedPath ?? `Request #${requestId}`}
            </span>
          </span>
        }
        actions={
          <>
            <Button
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
                  to: "/programs/$programId/hosts/$hostId/equalize",
                  params: { programId, hostId },
                  search: { requestIds: String(requestId) },
                })
              }
            >
              <Shuffle className="h-3.5 w-3.5" />
              Equalize
            </Button>
            <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-[260px_1fr_1fr] gap-4">
        {/* Metadata pane */}
        <Card>
          <CardContent className="p-4 space-y-3">
            <Field label="Method">{r?.method ? <MethodPill method={r.method} /> : "—"}</Field>
            <Field label="Path">
              <span className="font-mono text-xs break-all">{r?.computatedPath ?? "—"}</span>
            </Field>
            <Field label="Extension">
              <span className="font-mono text-xs">{r?.extension ?? "—"}</span>
            </Field>
            <Field label="Version">{r?.version ?? "—"}</Field>
            <Field label="Content type">
              {r?.contentType ? <Badge variant="muted">{r.contentType}</Badge> : "—"}
            </Field>
            <Field label="Source">
              {r?.source ? <Badge variant="outline">{r.source}</Badge> : "—"}
            </Field>
            <Field label="Properties">
              <span className="font-mono tabular-nums text-xs">{r?.numberOfProperties ?? "—"}</span>
            </Field>
            <Field label="Tenant">
              <span className="font-mono tabular-nums text-xs">
                {r?.tenantId ? `#${r.tenantId}` : "—"}
              </span>
            </Field>
          </CardContent>
        </Card>

        {/* Request raw */}
        <Card>
          <CardContent className="p-0">
            <div className="px-4 py-2.5 border-b border-border">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                Request
              </div>
            </div>
            {reqQ.isLoading ? (
              <div className="p-6 text-xs text-muted-foreground text-center">Loading…</div>
            ) : rawRequestText ? (
              <pre className="p-4 text-[11px] font-mono whitespace-pre-wrap break-all leading-relaxed text-foreground/90 overflow-auto max-h-[70vh]">
                {rawRequestText}
              </pre>
            ) : (
              <div className="p-6 text-xs text-muted-foreground text-center">
                No raw request body returned by the API.
              </div>
            )}
          </CardContent>
        </Card>

        {/* Response */}
        <Card>
          <CardContent className="p-0">
            <div className="px-4 py-2.5 border-b border-border flex items-center justify-between">
              <div className="text-[10px] uppercase tracking-wider text-muted-foreground">
                Response
              </div>
              {respQ.data?.statusCode && <StatusCodePill code={respQ.data.statusCode} />}
            </div>
            {!r?.responseId ? (
              <div className="p-6 text-xs text-muted-foreground text-center">
                No response associated with this request.
              </div>
            ) : rawRespQ.isLoading ? (
              <div className="p-6 text-xs text-muted-foreground text-center">Loading…</div>
            ) : (
              <pre className="p-4 text-[11px] font-mono whitespace-pre-wrap break-all leading-relaxed text-foreground/90 overflow-auto max-h-[70vh]">
                {rawResponseText || (
                  <span className="text-muted-foreground"># Empty response body</span>
                )}
              </pre>
            )}
          </CardContent>
        </Card>
      </div>

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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1">{label}</div>
      <div className="text-sm">{children}</div>
    </div>
  );
}
