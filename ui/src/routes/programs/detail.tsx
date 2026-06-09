import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { Plus, Search, Trash2 } from "lucide-react";
import { useProgram } from "@/hooks/usePrograms";
import { useHosts, useDeleteHost } from "@/hooks/useHosts";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateHostDialog } from "@/components/host/create-host-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { ErrorState } from "@/components/common/states";

export function ProgramDetailRoute() {
  const { programId } = useParams({ strict: false }) as { programId: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  useSetTopBar(
    [
      { label: "Programs", to: "/programs" },
      { label: programQ.data?.name ?? `#${programId}` },
    ],
    null,
    [programQ.data?.name, programId],
  );

  const [query, setQuery] = React.useState("");
  const [debouncedQuery, setDebouncedQuery] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(20);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  React.useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(t);
  }, [query]);

  const hostsQ = useHosts(programId, debouncedQuery || undefined, { page, size });
  const deleteMut = useDeleteHost();

  if (programQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load program"
          description={programQ.error instanceof Error ? programQ.error.message : ""}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title={programQ.data?.name ?? `Program #${programId}`}
        description={
          <div className="flex items-center gap-2 flex-wrap">
            {programQ.data?.platform && <Badge variant="outline">{programQ.data.platform}</Badge>}
            {programQ.data?.link && (
              <a
                href={programQ.data.link}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-muted-foreground hover:text-foreground underline-offset-2 hover:underline truncate max-w-md"
              >
                {programQ.data.link}
              </a>
            )}
            {programQ.data?.programRateLimit !== undefined &&
              programQ.data?.programRateLimit !== null && (
                <span className="text-xs text-muted-foreground">
                  Rate limit: {programQ.data.programRateLimit} req/s
                </span>
              )}
          </div>
        }
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New host
          </Button>
        }
      />

      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search hosts in this program…"
            className="pl-8"
          />
        </div>
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={hostsQ.data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={hostsQ.isLoading}
          emptyMessage="No hosts in this program yet."
          onRowClick={(r) =>
            navigate({
              to: "/programs/$programId/hosts/$hostId",
              params: { programId, hostId: r.id },
            })
          }
          columns={[
            {
              key: "host",
              header: "Host",
              cell: (r) => (
                <Link
                  to="/programs/$programId/hosts/$hostId"
                  params={{ programId, hostId: r.id }}
                  className="font-mono text-[13px] font-medium hover:underline"
                >
                  {r.host}
                </Link>
              ),
            },
            {
              key: "scope",
              header: "Scope",
              cell: (r) => (
                <Badge variant={r.scope === "WILDCARD" ? "accent" : "outline"}>{r.scope}</Badge>
              ),
            },
            {
              key: "in-scope",
              header: "In scope",
              cell: (r) =>
                r.outOfScope ? (
                  <Badge variant="destructive">Out of scope</Badge>
                ) : (
                  <Badge variant="success">In scope</Badge>
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
                    setPendingDelete(r.id);
                  }}
                  aria-label="Delete"
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
          total={hostsQ.data?.totalElements}
          onPageChange={setPage}
          onSizeChange={(s) => {
            setSize(s);
            setPage(0);
          }}
        />
      </div>

      <CreateHostDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        programId={programId}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete host?"
        description="All tenants, requests, and jobs under this host will be affected."
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          if (pendingDelete === null) return;
          try {
            await deleteMut.mutateAsync({ id: pendingDelete, programId });
            toast({ title: "Host deleted", variant: "success" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
