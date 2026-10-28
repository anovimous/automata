import * as React from "react";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { Plus, X } from "lucide-react";
import { useAgentContexts } from "@/hooks/useAgentContexts";
import { useHost } from "@/hooks/useHosts";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { CreateAgentContextDialog } from "@/components/agentcontext/create-agent-context-dialog";
import { relativeTime } from "@/lib/utils";

export function AgentContextsListRoute() {
  useSetTopBar([{ label: "Agent contexts" }]);
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as {
    hostId?: number;
    page?: number;
    size?: number;
  };
  const page = search.page ?? 0;
  const size = search.size ?? 25;

  const hostQ = useHost(search.hostId ?? null);
  const { data, isLoading } = useAgentContexts(search.hostId, { page, size });
  const [createOpen, setCreateOpen] = React.useState(false);

  return (
    <PageContainer>
      <PageHeader
        title="Agent contexts"
        description="What an AI agent may work on: one host, with the requests, routines and tenants it is allowed to use."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New context
          </Button>
        }
      />

      {search.hostId !== undefined && (
        <div className="flex items-center gap-2 mb-3">
          <Badge variant="accent" className="gap-1">
            Host {hostQ.data?.host ?? `#${search.hostId}`}
            <button
              type="button"
              onClick={() => navigate({ to: "/agent-contexts", search: {} as never })}
              aria-label="Clear host filter"
            >
              <X className="h-3 w-3" />
            </button>
          </Badge>
        </div>
      )}

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={data?.content ?? []}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No agent context yet."
          onRowClick={(r) => navigate({ to: "/agent-contexts/$contextId", params: { contextId: r.id } })}
          columns={[
            {
              key: "name",
              header: "Name",
              cell: (r) => (
                <Link
                  to="/agent-contexts/$contextId"
                  params={{ contextId: r.id }}
                  className="font-medium hover:underline"
                >
                  {r.name}
                </Link>
              ),
            },
            {
              key: "host",
              header: "Host",
              cell: (r) => <HostCell hostId={r.hostId} />,
            },
            {
              key: "strategy",
              header: "Representative",
              cell: (r) => (
                <Badge variant="outline">
                  {r.representativeStrategy === "LATEST" ? "Latest" : "Agent chooses"}
                </Badge>
              ),
            },
            {
              key: "items",
              header: "Items",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">{r.itemCount}</span>
              ),
            },
            {
              key: "uuid",
              header: "UUID",
              cell: (r) => (
                <span className="font-mono text-[11px] text-muted-foreground">{r.uuid}</span>
              ),
            },
            {
              key: "created",
              header: "Created",
              cell: (r) => (
                <span className="text-muted-foreground">{relativeTime(r.creationDate)}</span>
              ),
            },
          ]}
        />
        <Pagination
          page={page}
          size={size}
          total={data?.totalElements}
          onPageChange={(p) => navigate({ to: "/agent-contexts", search: { ...search, page: p } as never })}
          onSizeChange={(s) =>
            navigate({ to: "/agent-contexts", search: { ...search, size: s, page: 0 } as never })
          }
        />
      </div>

      <CreateAgentContextDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        initialHostId={search.hostId}
        onCreated={(id) => {
          if (id) navigate({ to: "/agent-contexts/$contextId", params: { contextId: id } });
        }}
      />
    </PageContainer>
  );
}

function HostCell({ hostId }: { hostId: number }) {
  const hostQ = useHost(hostId);
  if (hostQ.data) {
    return (
      <Link
        to="/programs/$programId/hosts/$hostId"
        params={{ programId: hostQ.data.programId, hostId }}
        className="font-mono text-xs hover:underline"
        onClick={(e) => e.stopPropagation()}
      >
        {hostQ.data.host}
      </Link>
    );
  }
  return <span className="font-mono text-xs text-muted-foreground">#{hostId}</span>;
}
