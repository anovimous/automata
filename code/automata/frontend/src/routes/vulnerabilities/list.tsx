import * as React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus, Trash2 } from "lucide-react";
import {
  useCreateVulnerability,
  useDeleteVulnerability,
  useVulnerabilities,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function VulnerabilitiesListRoute() {
  useSetTopBar([{ label: "Vulnerabilities" }]);
  const navigate = useNavigate();
  const { toast } = useToast();
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);

  const { data, isLoading } = useVulnerabilities({ page, size });
  const deleteMut = useDeleteVulnerability();
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  return (
    <PageContainer>
      <PageHeader
        title="Vulnerabilities"
        description="The vulnerability taxonomy referenced by routines and wordlists."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New vulnerability
          </Button>
        }
      />

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No vulnerabilities."
          onRowClick={(r) => navigate({ to: "/vulnerabilities/$id", params: { id: r.id } })}
          columns={[
            {
              key: "name",
              header: "Name",
              cell: (r) => (
                <Link
                  to="/vulnerabilities/$id"
                  params={{ id: r.id }}
                  className="font-medium hover:underline"
                >
                  {r.name}
                </Link>
              ),
            },
            {
              key: "parent",
              header: "Parent",
              cell: (r) =>
                r.parentId ? (
                  <Link
                    to="/vulnerabilities/$id"
                    params={{ id: r.parentId }}
                    className="text-xs font-mono text-muted-foreground hover:underline"
                  >
                    #{r.parentId}
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

      <CreateVulnDialog open={createOpen} onOpenChange={setCreateOpen} />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete vulnerability?"
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

function CreateVulnDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const [name, setName] = React.useState("");
  const [parentId, setParentId] = React.useState<string>("");
  const createMut = useCreateVulnerability();
  const vulns = useVulnerabilities({ page: 0, size: 200 });
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setName("");
      setParentId("");
    }
  }, [open]);

  async function submit() {
    if (!name.trim()) return;
    try {
      await createMut.mutateAsync({
        name: name.trim(),
        parentId: parentId ? Number(parentId) : undefined,
      });
      toast({ title: "Created", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Create failed";
      toast({ title: "Create failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New vulnerability</DialogTitle>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label>Name</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
              placeholder="SQL Injection"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Parent (optional)</Label>
            <Select value={parentId} onValueChange={setParentId}>
              <SelectTrigger>
                <SelectValue placeholder="—" />
              </SelectTrigger>
              <SelectContent>
                {vulns.data?.content.map((v) => (
                  <SelectItem key={v.id} value={String(v.id)}>
                    {v.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!name.trim() || createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
