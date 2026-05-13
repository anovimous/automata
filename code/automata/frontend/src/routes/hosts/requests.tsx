import * as React from "react";
import { Link, useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { Play, Plus, Shuffle, Trash2, Search, X } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useRequests, useDeleteRequest } from "@/hooks/useRequests";
import { useTenants } from "@/hooks/useTenants";
import { useSetTopBar } from "@/components/layout/top-bar";
import { FullBleed, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { MethodPill, StatusCodePill } from "@/components/job/pills";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { AddRawRequestDialog } from "@/components/request/add-raw-request-dialog";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type { RequestFilter } from "@/types/domain";

export function HostRequestsRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const search = useSearch({ strict: false }) as {
    page?: number;
    size?: number;
    method?: string;
    contentType?: string;
    source?: string;
    extension?: string;
    path?: string;
    tenantId?: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const tenants = useTenants(hostId);

  const [addOpen, setAddOpen] = React.useState(false);
  const [selected, setSelected] = React.useState<Set<string | number>>(new Set());
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = React.useState(false);

  // Local mirror for path-search (debounced into URL)
  const [pathSearch, setPathSearch] = React.useState(search.path ?? "");
  React.useEffect(() => {
    const t = setTimeout(() => {
      if (pathSearch !== (search.path ?? "")) {
        updateSearch({ path: pathSearch || undefined, page: 0 });
      }
    }, 250);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathSearch]);

  function updateSearch(patch: Partial<typeof search>) {
    navigate({
      to: "/programs/$programId/hosts/$hostId/requests",
      params: { programId, hostId },
      search: { ...search, ...patch },
    });
  }

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
      { label: "Requests" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  const filter: RequestFilter = {
    hostId,
    method: (search.method as RequestFilter["method"]) || undefined,
    contentType: (search.contentType as RequestFilter["contentType"]) || undefined,
    source: (search.source as RequestFilter["source"]) || undefined,
    extension: search.extension || undefined,
    computatedPath: search.path || undefined,
    tenantId: search.tenantId,
  };
  const page = search.page ?? 0;
  const size = search.size ?? 50;

  const { data, isLoading } = useRequests(filter, { page, size, sort: ["id,desc"] });
  const deleteMut = useDeleteRequest();
  const rows = data?.content ?? [];

  const selectedRequestIds = Array.from(selected).map((s) => Number(s));

  return (
    <FullBleed>
      <PageHeader
        title="Requests"
        description={
          <span className="font-mono text-xs text-muted-foreground">
            {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
        actions={
          <>
            <Link
              to="/programs/$programId/hosts/$hostId/equalize"
              params={{ programId, hostId }}
            >
              <Button variant="outline">
                <Shuffle className="h-3.5 w-3.5" />
                Equalize
              </Button>
            </Link>
            <Button onClick={() => setAddOpen(true)}>
              <Plus className="h-3.5 w-3.5" />
              Add raw request
            </Button>
          </>
        }
      />

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2 mb-3">
        <div className="relative flex-1 min-w-[220px] max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={pathSearch}
            onChange={(e) => setPathSearch(e.target.value)}
            placeholder="Filter by path…"
            className="pl-8 font-mono"
          />
        </div>

        <FilterSelect
          value={search.method}
          onChange={(v) => updateSearch({ method: v, page: 0 })}
          placeholder="Method"
          options={["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"]}
        />
        <FilterSelect
          value={search.contentType}
          onChange={(v) => updateSearch({ contentType: v, page: 0 })}
          placeholder="Content type"
          options={["JSON", "MULTIPART", "FORM", "UNSUPPORTED"]}
        />
        <FilterSelect
          value={search.source}
          onChange={(v) => updateSearch({ source: v, page: 0 })}
          placeholder="Source"
          options={["MANUAL", "WAYBACK"]}
        />
        <Input
          value={search.extension ?? ""}
          onChange={(e) => updateSearch({ extension: e.target.value || undefined, page: 0 })}
          placeholder="Extension"
          className="max-w-[110px] font-mono"
        />
        <Select
          value={search.tenantId !== undefined ? String(search.tenantId) : ""}
          onValueChange={(v) =>
            updateSearch({ tenantId: v ? Number(v) : undefined, page: 0 })
          }
        >
          <SelectTrigger className="w-[160px]">
            <SelectValue placeholder="Tenant" />
          </SelectTrigger>
          <SelectContent>
            {tenants.data?.content.map((t) => (
              <SelectItem key={t.id} value={String(t.id)}>
                {t.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {(search.method || search.contentType || search.source || search.extension || search.path || search.tenantId !== undefined) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setPathSearch("");
              navigate({
                to: "/programs/$programId/hosts/$hostId/requests",
                params: { programId, hostId },
                search: {},
              });
            }}
          >
            <X className="h-3 w-3" />
            Clear
          </Button>
        )}
      </div>

      {/* Selection bar */}
      {selected.size > 0 && (
        <div className="flex items-center gap-2 px-3 py-2 mb-2 rounded-md border border-foreground/15 bg-foreground/5 animate-fade-in">
          <span className="text-xs font-medium">{selected.size} selected</span>
          <span className="text-xs text-muted-foreground">— NARROW scope (this host)</span>
          <div className="flex-1" />
          <Button
            size="sm"
            onClick={() =>
              navigate({
                to: "/programs/$programId/hosts/$hostId/run-job",
                params: { programId, hostId },
                search: {
                  kind:
                    selectedRequestIds.length === 1 ? "single-request" : "multiple-requests",
                  requestIds: selectedRequestIds.join(","),
                },
              })
            }
          >
            <Play className="h-3.5 w-3.5" />
            Run job on {selected.size} request{selected.size === 1 ? "" : "s"}
          </Button>
          <Link
            to="/programs/$programId/hosts/$hostId/equalize"
            params={{ programId, hostId }}
            search={{ requestIds: selectedRequestIds.join(",") }}
          >
            <Button variant="outline" size="sm">
              <Shuffle className="h-3.5 w-3.5" />
              Equalize
            </Button>
          </Link>
          <Button variant="outline" size="sm" onClick={() => setBatchDeleteOpen(true)}>
            <Trash2 className="h-3.5 w-3.5" />
            Delete
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setSelected(new Set())}>
            Clear
          </Button>
        </div>
      )}

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={rows}
          rowKey={(r) => r.requestId}
          isLoading={isLoading}
          emptyMessage="No requests match these filters."
          selectable
          selected={selected}
          onSelectionChange={setSelected}
          onRowClick={(r) =>
            navigate({
              to: "/programs/$programId/hosts/$hostId/requests/$requestId",
              params: { programId, hostId, requestId: r.requestId },
            })
          }
          columns={[
            {
              key: "method",
              header: "Method",
              width: "w-20",
              cell: (r) => <MethodPill method={r.method} />,
            },
            {
              key: "path",
              header: "Path",
              cell: (r) => (
                <span className="font-mono text-[12px] truncate block max-w-[42ch]">
                  {r.computatedPath ?? "—"}
                </span>
              ),
            },
            {
              key: "ext",
              header: "Ext",
              cell: (r) => (
                <span className="font-mono text-xs text-muted-foreground">
                  {r.extension ?? "—"}
                </span>
              ),
            },
            {
              key: "ct",
              header: "Type",
              cell: (r) =>
                r.contentType ? (
                  <Badge variant="muted">{r.contentType}</Badge>
                ) : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "props",
              header: "Props",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">
                  {r.numberOfProperties ?? "—"}
                </span>
              ),
            },
            {
              key: "resp",
              header: "Resp",
              cell: (r) =>
                r.responseId !== null && r.responseId !== undefined ? (
                  <span className="text-xs text-success">●</span>
                ) : (
                  <span className="text-xs text-muted-foreground">○</span>
                ),
            },
            {
              key: "source",
              header: "Source",
              cell: (r) =>
                r.source ? (
                  <Badge variant="outline">{r.source}</Badge>
                ) : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "actions",
              header: "",
              align: "right",
              cell: (r) => (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  data-stop-row-click
                  onClick={(e) => {
                    e.stopPropagation();
                    setPendingDelete(r.requestId);
                  }}
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              ),
            },
          ]}
        />
        <Pagination
          page={page}
          size={size}
          total={data?.totalElements}
          onPageChange={(p) => updateSearch({ page: p })}
          onSizeChange={(s) => updateSearch({ size: s, page: 0 })}
        />
      </div>

      <AddRawRequestDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        hostId={hostId}
        programId={programId}
        hostName={hostQ.data?.host}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete request?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          if (pendingDelete === null) return;
          try {
            await deleteMut.mutateAsync({
              id: pendingDelete,
              hostId,
              programId,
            });
            toast({ title: "Request deleted", variant: "success" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />

      <ConfirmDialog
        open={batchDeleteOpen}
        onOpenChange={setBatchDeleteOpen}
        title={`Delete ${selected.size} request${selected.size === 1 ? "" : "s"}?`}
        destructive
        confirmLabel="Delete all"
        onConfirm={async () => {
          let succeeded = 0;
          let failed = 0;
          for (const id of selectedRequestIds) {
            try {
              await deleteMut.mutateAsync({ id, hostId, programId });
              succeeded++;
            } catch {
              failed++;
            }
          }
          setSelected(new Set());
          toast({
            title: `Deleted ${succeeded}`,
            description: failed > 0 ? `${failed} failed` : undefined,
            variant: failed > 0 ? "warning" : "success",
          });
        }}
      />
    </FullBleed>
  );
}

function FilterSelect({
  value,
  onChange,
  placeholder,
  options,
}: {
  value: string | undefined;
  onChange: (v: string | undefined) => void;
  placeholder: string;
  options: string[];
}) {
  return (
    <Select value={value ?? ""} onValueChange={(v) => onChange(v || undefined)}>
      <SelectTrigger className="w-[140px]">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((o) => (
          <SelectItem key={o} value={o}>
            {o}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

// Keep StatusCodePill imported so the file compiles even if not used directly
export { StatusCodePill };
