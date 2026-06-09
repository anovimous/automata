import * as React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus, Search, Trash2 } from "lucide-react";
import {
  useComparators,
  useDeleteComparator,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateComparatorDialog } from "@/components/comparator/create-comparator-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function ComparatorsListRoute() {
  useSetTopBar([{ label: "Comparators" }]);
  const navigate = useNavigate();
  const { toast } = useToast();

  const [query, setQuery] = React.useState("");
  const [debounced, setDebounced] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  React.useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 200);
    return () => clearTimeout(t);
  }, [query]);

  const { data, isLoading } = useComparators({
    query: debounced || undefined,
    pageable: { page, size },
  });
  const deleteMut = useDeleteComparator();

  return (
    <PageContainer>
      <PageHeader
        title="Comparators"
        description="Composed of modifiers that decide which requests are equivalent."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New comparator
          </Button>
        }
      />

      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search comparators…"
            className="pl-8"
          />
        </div>
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No comparators."
          onRowClick={(r) => navigate({ to: "/comparators/$id", params: { id: r.id } })}
          columns={[
            {
              key: "name",
              header: "Name",
              cell: (r) => (
                <Link
                  to="/comparators/$id"
                  params={{ id: r.id }}
                  className="font-medium hover:underline"
                >
                  {r.name}
                </Link>
              ),
            },
            {
              key: "schema",
              header: "Schema",
              cell: (r) =>
                r.schema ? (
                  <Badge variant="outline">{r.schema}</Badge>
                ) : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "modifiers",
              header: "Modifiers",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">
                  {r.modifiers?.length ?? 0}
                </span>
              ),
            },
            {
              key: "scope",
              header: "Scope",
              cell: (r) => (
                <span className="text-xs text-muted-foreground truncate">
                  {r.host?.host ?? r.program?.name ?? "—"}
                </span>
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

      <CreateComparatorDialog open={createOpen} onOpenChange={setCreateOpen} />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete comparator?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          if (pendingDelete === null) return;
          try {
            await deleteMut.mutateAsync(pendingDelete);
            toast({ title: "Deleted", variant: "success" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
