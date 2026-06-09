import * as React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus, Search, Trash2 } from "lucide-react";
import { useCreateProgram, useDeleteProgram, usePrograms } from "@/hooks/usePrograms";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { useToast } from "@/components/ui/toast";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateProgramDialog } from "@/components/program/create-program-dialog";
import { relativeTime } from "@/lib/utils";
import { ApiError } from "@/lib/fetcher";

export function ProgramsListRoute() {
  useSetTopBar([{ label: "Programs" }]);
  const navigate = useNavigate();
  const { toast } = useToast();

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

  const { data, isLoading } = usePrograms(debouncedQuery || undefined, { page, size });
  const deleteMut = useDeleteProgram();

  return (
    <PageContainer>
      <PageHeader
        title="Programs"
        description="Bug bounty programs and other top-level scopes."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New program
          </Button>
        }
      />

      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search programs…"
            className="pl-8"
          />
        </div>
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No programs yet. Create one to get started."
          onRowClick={(r) =>
            navigate({ to: "/programs/$programId", params: { programId: r.id } })
          }
          columns={[
            {
              key: "name",
              header: "Name",
              cell: (r) => (
                <Link
                  to="/programs/$programId"
                  params={{ programId: r.id }}
                  className="font-medium hover:underline"
                >
                  {r.name}
                </Link>
              ),
            },
            {
              key: "platform",
              header: "Platform",
              cell: (r) =>
                r.platform ? <Badge variant="outline">{r.platform}</Badge> : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "rate",
              header: "Rate limit",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">
                  {r.programRateLimit ?? "—"}
                </span>
              ),
            },
            {
              key: "added",
              header: "Added",
              cell: (r) => (
                <span className="text-muted-foreground">{relativeTime(r.insertionDate)}</span>
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
          total={data?.totalElements}
          onPageChange={setPage}
          onSizeChange={(s) => {
            setSize(s);
            setPage(0);
          }}
        />
      </div>

      <CreateProgramDialog open={createOpen} onOpenChange={setCreateOpen} />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete program?"
        description="All hosts, tenants, requests, and jobs under this program will be affected."
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          if (pendingDelete === null) return;
          try {
            await deleteMut.mutateAsync(pendingDelete);
            toast({ title: "Program deleted", variant: "success" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
