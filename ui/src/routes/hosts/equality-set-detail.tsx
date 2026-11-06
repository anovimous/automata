import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Bot, Save } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useEqualRequests, useEqualitySet, usePatchEqualitySet } from "@/hooks/useRequests";
import { useAddContextItem, useAgentContexts } from "@/hooks/useAgentContexts";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { Switch } from "@/components/ui/primitives";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { DataTable } from "@/components/data-table/data-table";
import { Pagination } from "@/components/data-table/pagination";
import { ErrorState } from "@/components/common/states";
import { MethodPill } from "@/components/job/pills";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function HostEqualitySetDetailRoute() {
  const { programId, hostId, equalitySetId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
    equalitySetId: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const setQ = useEqualitySet(equalitySetId);
  const patchMut = usePatchEqualitySet();
  const contextsQ = useAgentContexts(hostId, { page: 0, size: 100 });
  const addItemMut = useAddContextItem();

  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(25);
  const membersQ = useEqualRequests(equalitySetId, { page, size });

  const [alias, setAlias] = React.useState("");
  const [autoApproval, setAutoApproval] = React.useState(false);
  const [contextId, setContextId] = React.useState("");

  React.useEffect(() => {
    if (setQ.data) {
      setAlias(setQ.data.alias ?? "");
      setAutoApproval(setQ.data.autoApproval);
    }
  }, [setQ.data]);

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
      {
        label: "Equalizations",
        to: `/programs/${programId}/hosts/${hostId}/equality-sets` as string,
      },
      { label: `Set #${equalitySetId}` },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId, equalitySetId],
  );

  async function save() {
    try {
      await patchMut.mutateAsync({
        id: equalitySetId,
        req: { alias: alias.trim() || undefined, autoApproval },
      });
      toast({ title: "Equality set updated", variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Update failed";
      toast({ title: "Update failed", description: msg, variant: "destructive" });
    }
  }

  async function addToContext() {
    if (!contextId) return;
    try {
      await addItemMut.mutateAsync({
        id: Number(contextId),
        req: { itemType: "EQUALITY_SET", equalitySetId },
      });
      toast({ title: "Added to the agent context", variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Add failed";
      toast({ title: "Add failed", description: msg, variant: "destructive" });
    }
  }

  if (setQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load the equality set"
          description={setQ.error instanceof Error ? setQ.error.message : ""}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() =>
          navigate({
            to: "/programs/$programId/hosts/$hostId/equality-sets",
            params: { programId, hostId },
          })
        }
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>

      <PageHeader
        title={setQ.data?.alias || `Equality set #${equalitySetId}`}
        description={
          <span className="flex items-center gap-2">
            <Badge variant="muted">{setQ.data?.numberOfRequests ?? 0} requests</Badge>
            {setQ.data?.equalizationId != null && (
              <span className="text-xs text-muted-foreground">
                from equalization #{setQ.data.equalizationId}
              </span>
            )}
          </span>
        }
        actions={
          <Button onClick={save} disabled={patchMut.isPending}>
            <Save className="h-3.5 w-3.5" />
            Save
          </Button>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <Card>
          <CardHeader>
            <CardTitle>Settings</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="alias">Alias</Label>
              <Input
                id="alias"
                value={alias}
                onChange={(e) => setAlias(e.target.value)}
                placeholder="e.g. user profile endpoints"
              />
            </div>
            <div className="flex items-center justify-between">
              <div>
                <Label htmlFor="auto-approval">Auto-approval</Label>
                <div className="text-[11px] text-muted-foreground">
                  Jobs an AI agent creates on requests of this set skip the human approval.
                </div>
              </div>
              <Switch
                id="auto-approval"
                checked={autoApproval}
                onCheckedChange={setAutoApproval}
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Bot className="h-3.5 w-3.5 text-muted-foreground" />
              Agent context
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-xs text-muted-foreground">
              Equality sets aren't job targets themselves. Put the set in an agent context and
              the agent tests its representative request.
            </p>
            <div className="flex items-center gap-2">
              <Select value={contextId} onValueChange={setContextId}>
                <SelectTrigger className="flex-1">
                  <SelectValue placeholder="Pick a context" />
                </SelectTrigger>
                <SelectContent>
                  {contextsQ.data?.content.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button onClick={addToContext} disabled={!contextId || addItemMut.isPending}>
                Add
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={membersQ.data?.content ?? []}
          rowKey={(r) => r.requestId}
          isLoading={membersQ.isLoading}
          emptyMessage="No requests in this set."
          onRowClick={(r) =>
            navigate({
              to: "/programs/$programId/hosts/$hostId/requests/$requestId",
              params: { programId, hostId, requestId: r.requestId },
            })
          }
          columns={[
            {
              key: "id",
              header: "ID",
              width: "w-20",
              cell: (r) => (
                <Link
                  to="/programs/$programId/hosts/$hostId/requests/$requestId"
                  params={{ programId, hostId, requestId: r.requestId }}
                  className="font-mono text-xs hover:underline"
                >
                  #{r.requestId}
                </Link>
              ),
            },
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
                <span className="font-mono text-[12px]">{r.computatedPath ?? "—"}</span>
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
          ]}
        />
        <Pagination
          page={page}
          size={size}
          total={membersQ.data?.totalElements}
          onPageChange={setPage}
          onSizeChange={(s) => {
            setSize(s);
            setPage(0);
          }}
        />
      </div>
    </PageContainer>
  );
}
