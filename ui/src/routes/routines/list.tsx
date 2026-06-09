import * as React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus, Search, Trash2 } from "lucide-react";
import { useRoutines, useDeleteRoutine } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateRoutineDialog } from "@/components/routine/create-routine-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function RoutinesListRoute() {
  useSetTopBar([{ label: "Routines" }]);
  const navigate = useNavigate();
  const { toast } = useToast();

  const [query, setQuery] = React.useState("");
  const [debouncedQuery, setDebouncedQuery] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  React.useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(t);
  }, [query]);

  const { data, isLoading } = useRoutines(debouncedQuery || undefined, undefined, {
    page,
    size,
  });
  const deleteMut = useDeleteRoutine();

  return (
    <PageContainer>
      <PageHeader
        title="Routines"
        description="Vulnerability-class playbooks used by jobs."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New routine
          </Button>
        }
      />

      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search routines…"
            className="pl-8"
          />
        </div>
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No routines."
          onRowClick={(r) => navigate({ to: "/routines/$id", params: { id: r.id } })}
          columns={[
            {
              key: "key",
              header: "Key",
              cell: (r) => (
                <Link
                  to="/routines/$id"
                  params={{ id: r.id }}
                  className="font-mono text-xs font-medium hover:underline"
                >
                  {r.key}
                </Link>
              ),
            },
            {
              key: "overhead",
              header: "Overhead",
              cell: (r) =>
                r.overhead ? <Badge variant="muted">{r.overhead}</Badge> : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "protocol",
              header: "Protocol",
              cell: (r) =>
                r.protocol ? <Badge variant="outline">{r.protocol}</Badge> : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "targets",
              header: "Allowed targets",
              cell: (r) => (
                <div className="flex flex-wrap gap-1">
                  {r.allowedTargets?.length ? (
                    r.allowedTargets.map((t) => (
                      <Badge key={t} variant="outline" className="text-[9px]">
                        {t}
                      </Badge>
                    ))
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                </div>
              ),
            },
            {
              key: "consumer",
              header: "Consumer",
              cell: (r) =>
                r.isAvailableAtConsumer ? (
                  <Badge variant="success">Available</Badge>
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
                    setPendingDelete(r.id);
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
          onPageChange={setPage}
          onSizeChange={(s) => {
            setSize(s);
            setPage(0);
          }}
        />
      </div>

      <CreateRoutineDialog open={createOpen} onOpenChange={setCreateOpen} />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete routine?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          if (pendingDelete === null) return;
          try {
            await deleteMut.mutateAsync(pendingDelete);
            toast({ title: "Routine deleted", variant: "success" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
