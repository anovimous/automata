import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { Plus, Trash2, ShieldCheck, ShieldOff } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useTenants, useDeleteTenant } from "@/hooks/useTenants";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { CreateTenantDialog } from "@/components/tenant/create-tenant-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function HostTenantsRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);

  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(20);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [pendingDelete, setPendingDelete] = React.useState<number | null>(null);

  const { data, isLoading } = useTenants(hostId, { page, size });
  const deleteMut = useDeleteTenant();

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
      { label: "Tenants" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  return (
    <PageContainer>
      <PageHeader
        title="Tenants"
        description={
          <span className="font-mono text-xs text-muted-foreground">
            {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New tenant
          </Button>
        }
      />

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No tenants. Create one to attach authentication data."
          onRowClick={(r) =>
            navigate({
              to: "/programs/$programId/hosts/$hostId/tenants/$tenantId",
              params: { programId, hostId, tenantId: r.id },
            })
          }
          columns={[
            {
              key: "name",
              header: "Name",
              cell: (r) => (
                <Link
                  to="/programs/$programId/hosts/$hostId/tenants/$tenantId"
                  params={{ programId, hostId, tenantId: r.id }}
                  className="font-medium hover:underline"
                >
                  {r.name}
                </Link>
              ),
            },
            {
              key: "email",
              header: "Email",
              cell: (r) => (
                <span className="font-mono text-xs text-muted-foreground">
                  {r.email ?? "—"}
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

      <CreateTenantDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        hostId={hostId}
        programId={programId}
        hostName={hostQ.data?.host}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(o) => !o && setPendingDelete(null)}
        title="Delete tenant?"
        description="All requests and jobs associated with this tenant will lose the association."
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          if (pendingDelete === null) return;
          try {
            await deleteMut.mutateAsync({ id: pendingDelete, hostId, programId });
            toast({ title: "Tenant deleted", variant: "success" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}

// Keep these imported icons referenced so tree-shaking doesn't kick in on dev
const _ = [ShieldCheck, ShieldOff];
