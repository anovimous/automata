import * as React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus, Search, Trash2 } from "lucide-react";
import {
  useDeleteWordlist,
  useVulnerabilities,
  useWordlists,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateWordlistDialog } from "@/components/wordlist/create-wordlist-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function WordlistsListRoute() {
  useSetTopBar([{ label: "Wordlists" }]);
  const navigate = useNavigate();
  const { toast } = useToast();

  const [query, setQuery] = React.useState("");
  const [debounced, setDebounced] = React.useState("");
  const [vulnId, setVulnId] = React.useState<string>("");
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  React.useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 200);
    return () => clearTimeout(t);
  }, [query]);

  const vulns = useVulnerabilities({ page: 0, size: 200 });
  const { data, isLoading } = useWordlists({
    query: debounced || undefined,
    vulnId: vulnId ? Number(vulnId) : undefined,
    pageable: { page, size },
  });
  const deleteMut = useDeleteWordlist();

  return (
    <PageContainer>
      <PageHeader
        title="Wordlists"
        description="Pointers to wordlist files used by routines."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New wordlist
          </Button>
        }
      />

      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search wordlists…"
            className="pl-8"
          />
        </div>
        <Select value={vulnId} onValueChange={setVulnId}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder="Vulnerability" />
          </SelectTrigger>
          <SelectContent>
            {vulns.data?.content.map((v) => (
              <SelectItem key={v.id} value={String(v.id)}>
                {v.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {vulnId && (
          <Button variant="ghost" size="sm" onClick={() => setVulnId("")}>
            Clear
          </Button>
        )}
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No wordlists."
          onRowClick={(r) => navigate({ to: "/wordlists/$id", params: { id: r.id } })}
          columns={[
            {
              key: "name",
              header: "Name",
              cell: (r) => (
                <Link
                  to="/wordlists/$id"
                  params={{ id: r.id }}
                  className="font-mono text-xs font-medium hover:underline"
                >
                  {r.name}
                </Link>
              ),
            },
            {
              key: "path",
              header: "Path",
              cell: (r) => (
                <span className="font-mono text-xs text-muted-foreground truncate block max-w-[50ch]">
                  {r.path}
                </span>
              ),
            },
            {
              key: "vuln",
              header: "Vulnerability",
              cell: (r) =>
                r.vulnerabilityId ? (
                  <Link
                    to="/vulnerabilities/$id"
                    params={{ id: r.vulnerabilityId }}
                    className="text-xs hover:underline"
                  >
                    #{r.vulnerabilityId}
                  </Link>
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

      <CreateWordlistDialog open={createOpen} onOpenChange={setCreateOpen} />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete wordlist?"
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
