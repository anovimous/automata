import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { GitMerge, Play, Plus, Search } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import {
  useCreateEqualization,
  useEqualizations,
  useRunEqualization,
  useSetAutoEqualize,
} from "@/hooks/useRequests";
import { useComparators } from "@/hooks/useResources";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { DataTable } from "@/components/data-table/data-table";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { relativeTime } from "@/lib/utils";

/**
 * An *equalization* applies a comparator to the requests of a host and groups the
 * equivalent ones into equality sets. The backend can list the equalizations of a
 * host and run one, but has no route that lists the sets it produced, so a set is
 * opened by its id (agent context items carry these ids).
 */
export function HostEqualitySetsRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const equalizationsQ = useEqualizations(hostId);
  const comparatorsQ = useComparators({ pageable: { page: 0, size: 200 } });
  const runMut = useRunEqualization();
  const autoMut = useSetAutoEqualize();

  const [createOpen, setCreateOpen] = React.useState(false);
  const [setId, setSetId] = React.useState("");

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
      { label: "Equalizations" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  const comparatorName = (id: number) =>
    comparatorsQ.data?.content.find((c) => c.id === id)?.name ?? `#${id}`;

  async function run(equalizationId: number) {
    try {
      await runMut.mutateAsync({
        equalizationId,
        hostId,
        programId,
        hostName: hostQ.data?.host,
      });
      toast({
        title: "Equalization started",
        description: "It runs in the background; the sets appear when it ends.",
        variant: "success",
      });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Run failed";
      toast({ title: "Run failed", description: msg, variant: "destructive" });
    }
  }

  async function toggleAuto(equalizationId: number, autoEqualize: boolean) {
    try {
      await autoMut.mutateAsync({ equalizationId, autoEqualize, hostId });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Update failed";
      toast({ title: "Update failed", description: msg, variant: "destructive" });
    }
  }

  function openSet() {
    const id = Number(setId);
    if (!Number.isInteger(id) || id <= 0) return;
    navigate({
      to: "/programs/$programId/hosts/$hostId/equality-sets/$equalitySetId",
      params: { programId, hostId, equalitySetId: id },
    });
  }

  return (
    <PageContainer>
      <PageHeader
        title="Equalizations"
        description={
          <span className="font-mono text-xs text-muted-foreground">
            on {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            New equalization
          </Button>
        }
      />

      <div className="border border-border rounded-lg overflow-hidden mb-4">
        <DataTable
          data={equalizationsQ.data ?? []}
          rowKey={(r) => r.id}
          isLoading={equalizationsQ.isLoading}
          emptyMessage="No equalization on this host. Create one to group equivalent requests."
          columns={[
            {
              key: "id",
              header: "ID",
              width: "w-20",
              cell: (r) => <span className="font-mono text-xs">#{r.id}</span>,
            },
            {
              key: "comparator",
              header: "Comparator",
              cell: (r) => (
                <Link
                  to="/comparators/$id"
                  params={{ id: r.comparatorId }}
                  className="hover:underline"
                >
                  {comparatorName(r.comparatorId)}
                </Link>
              ),
            },
            {
              key: "lastRun",
              header: "Last run",
              cell: (r) =>
                r.lastRun ? (
                  <span className="text-muted-foreground">{relativeTime(r.lastRun)}</span>
                ) : (
                  <Badge variant="muted">Never</Badge>
                ),
            },
            {
              key: "auto",
              header: "Auto equalize",
              cell: (r) => (
                <Switch
                  checked={r.autoEqualize}
                  onCheckedChange={(v) => toggleAuto(r.id, v)}
                  disabled={autoMut.isPending}
                />
              ),
            },
            {
              key: "actions",
              header: "",
              align: "right",
              cell: (r) => (
                <Button
                  variant="outline"
                  size="sm"
                  disabled={runMut.isPending}
                  onClick={() => run(r.id)}
                >
                  <Play className="h-3.5 w-3.5" />
                  Run
                </Button>
              ),
            },
          ]}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <GitMerge className="h-3.5 w-3.5 text-muted-foreground" />
            Open an equality set
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <p className="text-xs text-muted-foreground">
            The API has no route that lists the equality sets of a host. Enter the id of a set
            (an agent context shows the ids of its equality set items) to see its requests and
            edit its alias and auto-approval.
          </p>
          <div className="flex items-center gap-2 max-w-sm">
            <div className="relative flex-1">
              <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                value={setId}
                onChange={(e) => setSetId(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && openSet()}
                placeholder="Equality set id"
                inputMode="numeric"
                className="pl-8 font-mono"
              />
            </div>
            <Button onClick={openSet} disabled={!setId.trim()}>
              Open
            </Button>
          </div>
        </CardContent>
      </Card>

      <CreateEqualizationDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        hostId={hostId}
        programId={programId}
        hostName={hostQ.data?.host}
      />
    </PageContainer>
  );
}

function CreateEqualizationDialog({
  open,
  onOpenChange,
  hostId,
  programId,
  hostName,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
  hostId: number;
  programId: number;
  hostName?: string;
}) {
  const comparatorsQ = useComparators({ pageable: { page: 0, size: 200 } });
  const createMut = useCreateEqualization();
  const { toast } = useToast();
  const [comparatorId, setComparatorId] = React.useState("");
  const [auto, setAuto] = React.useState(false);

  React.useEffect(() => {
    if (open) {
      setComparatorId("");
      setAuto(false);
    }
  }, [open]);

  async function submit() {
    if (!comparatorId) return;
    try {
      await createMut.mutateAsync({
        hostId,
        comparatorId: Number(comparatorId),
        autoEqualize: auto,
        programId,
        hostName,
      });
      toast({ title: "Equalization created", variant: "success" });
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
          <DialogTitle>New equalization</DialogTitle>
          <DialogDescription>
            Groups the requests of this host that the comparator considers equal into equality
            sets. Run it afterwards, or let it run automatically.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label>Comparator</Label>
            <Select value={comparatorId} onValueChange={setComparatorId}>
              <SelectTrigger>
                <SelectValue placeholder="Pick a comparator" />
              </SelectTrigger>
              <SelectContent>
                {comparatorsQ.data?.content.map((c) => (
                  <SelectItem key={c.id} value={String(c.id)}>
                    {c.name}
                    {c.schema && <span className="text-muted-foreground"> · {c.schema}</span>}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {comparatorsQ.data?.content.length === 0 && (
              <div className="text-xs text-muted-foreground">
                No comparators yet. Create one under Comparators.
              </div>
            )}
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="auto-eq">Equalize automatically</Label>
            <Switch id="auto-eq" checked={auto} onCheckedChange={setAuto} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!comparatorId || createMut.isPending}>
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
