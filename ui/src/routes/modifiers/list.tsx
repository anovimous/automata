import * as React from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus, Trash2 } from "lucide-react";
import { useModifiers, useDeleteModifier } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateModifierDialog } from "@/components/modifier/create-modifier-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type {
  ComparatorSchema as ComparatorSchemaType,
  ModifierTarget,
} from "@/types/domain";

export function ModifiersListRoute() {
  useSetTopBar([{ label: "Modifiers" }]);
  const navigate = useNavigate();
  const { toast } = useToast();

  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);
  const [target, setTarget] = React.useState<ModifierTarget | "">("");
  const [schema, setSchema] = React.useState<ComparatorSchemaType | "">("");
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  const { data, isLoading } = useModifiers({
    target: target || undefined,
    schema: schema || undefined,
    pageable: { page, size },
  });
  const deleteMut = useDeleteModifier();

  return (
    <PageContainer>
      <PageHeader
        title="Modifiers"
        description="Building blocks that compose comparators."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New modifier
          </Button>
        }
      />

      <div className="flex items-center gap-2 mb-3">
        <Select value={target} onValueChange={(v) => setTarget(v as ModifierTarget)}>
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="Target" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="METHOD">METHOD</SelectItem>
            <SelectItem value="PATH">PATH</SelectItem>
            <SelectItem value="QUERYSTRING">QUERYSTRING</SelectItem>
            <SelectItem value="BODY">BODY</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={schema}
          onValueChange={(v) => setSchema(v as ComparatorSchemaType)}
        >
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="Schema" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="REST">REST</SelectItem>
            <SelectItem value="GRAPHQL">GRAPHQL</SelectItem>
          </SelectContent>
        </Select>
        {(target || schema) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setTarget("");
              setSchema("");
            }}
          >
            Clear
          </Button>
        )}
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No modifiers."
          onRowClick={(r) => navigate({ to: "/modifiers/$id", params: { id: r.id } })}
          columns={[
            {
              key: "id",
              header: "ID",
              cell: (r) => (
                <Link
                  to="/modifiers/$id"
                  params={{ id: r.id }}
                  className="font-mono tabular-nums text-xs hover:underline"
                >
                  #{r.id}
                </Link>
              ),
            },
            {
              key: "target",
              header: "Target",
              cell: (r) => <Badge variant="outline">{r.target}</Badge>,
            },
            {
              key: "schema",
              header: "Schema",
              cell: (r) =>
                r.schema ? (
                  <Badge variant="muted">{r.schema}</Badge>
                ) : (
                  <span className="text-muted-foreground">—</span>
                ),
            },
            {
              key: "priority",
              header: "Priority",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">
                  {r.priority ?? "—"}
                </span>
              ),
            },
            {
              key: "desc",
              header: "Description",
              cell: (r) => (
                <span className="text-xs text-muted-foreground truncate block max-w-[40ch]">
                  {r.description ?? "—"}
                </span>
              ),
            },
            {
              key: "avail",
              header: "Available",
              cell: (r) =>
                r.available ? (
                  <Badge variant="success">●</Badge>
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

      <CreateModifierDialog open={createOpen} onOpenChange={setCreateOpen} />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete modifier?"
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
