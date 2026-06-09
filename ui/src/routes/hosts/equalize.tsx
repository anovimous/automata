import * as React from "react";
import { useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { ArrowLeft, Shuffle, Search } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useRequests, useEqualizeRequests } from "@/hooks/useRequests";
import { useComparators } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { MethodPill } from "@/components/job/pills";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

const LAST_COMPARATOR_KEY = "automata.equalize.lastComparatorByHost";

function getLastComparator(hostId: number): number | null {
  try {
    const raw = localStorage.getItem(LAST_COMPARATOR_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (typeof parsed?.[hostId] === "number") return parsed[hostId];
    return null;
  } catch {
    return null;
  }
}

function setLastComparator(hostId: number, comparatorId: number) {
  try {
    const raw = localStorage.getItem(LAST_COMPARATOR_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    parsed[hostId] = comparatorId;
    localStorage.setItem(LAST_COMPARATOR_KEY, JSON.stringify(parsed));
  } catch {
    /* noop */
  }
}

export function HostEqualizeRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const search = useSearch({ strict: false }) as { requestIds?: string };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const equalizeMut = useEqualizeRequests();

  // Comparators filtered to host or its program (or unscoped).
  const comparatorsQ = useComparators({ hostId, programId });

  const preselected = React.useMemo(() => {
    if (!search.requestIds) return [] as number[];
    return search.requestIds
      .split(",")
      .map((s) => Number(s.trim()))
      .filter(Number.isFinite);
  }, [search.requestIds]);

  const [comparatorId, setComparatorId] = React.useState<number | null>(null);
  const [selected, setSelected] = React.useState<Set<string | number>>(
    new Set(preselected),
  );
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(50);
  const [pathSearch, setPathSearch] = React.useState("");
  const [debouncedPath, setDebouncedPath] = React.useState("");

  React.useEffect(() => {
    const t = setTimeout(() => setDebouncedPath(pathSearch), 250);
    return () => clearTimeout(t);
  }, [pathSearch]);

  // Initialize comparator from last-used or first option.
  React.useEffect(() => {
    if (comparatorId !== null) return;
    const last = getLastComparator(hostId);
    if (last !== null) {
      setComparatorId(last);
    } else if (comparatorsQ.data?.content?.length) {
      setComparatorId(comparatorsQ.data.content[0].id);
    }
  }, [hostId, comparatorsQ.data, comparatorId]);

  const requestsQ = useRequests(
    { hostId, computatedPath: debouncedPath || undefined },
    { page, size, sort: ["id,desc"] },
  );

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
      { label: "Equalize" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  const selectedIds = Array.from(selected).map((s) => Number(s));
  const canSubmit = comparatorId !== null && selectedIds.length >= 2 && !equalizeMut.isPending;

  async function submit() {
    if (!canSubmit || comparatorId === null) return;
    try {
      await equalizeMut.mutateAsync({
        hostId,
        comparatorId,
        requestsIds: selectedIds,
        programId,
        hostName: hostQ.data?.host,
      });
      setLastComparator(hostId, comparatorId);
      toast({
        title: "Equalization complete",
        description: `Grouped ${selectedIds.length} requests`,
        variant: "success",
      });
      navigate({
        to: "/programs/$programId/hosts/$hostId/requests",
        params: { programId, hostId },
      });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Equalize failed";
      toast({ title: "Equalize failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() =>
          navigate({
            to: "/programs/$programId/hosts/$hostId",
            params: { programId, hostId },
          })
        }
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to host
      </Button>

      <PageHeader
        title="Equalize requests"
        description={
          <span className="font-mono text-xs text-muted-foreground">
            on {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
      />

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Comparator</CardTitle>
        </CardHeader>
        <CardContent>
          <Select
            value={comparatorId !== null ? String(comparatorId) : ""}
            onValueChange={(v) => setComparatorId(Number(v))}
          >
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
            <div className="text-xs text-muted-foreground mt-2">
              No comparators available for this host or program. Create one in Comparators.
            </div>
          )}
        </CardContent>
      </Card>

      <div className="flex items-center justify-between mb-3 gap-2">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            value={pathSearch}
            onChange={(e) => setPathSearch(e.target.value)}
            placeholder="Filter requests by path…"
            className="pl-8 font-mono"
          />
        </div>
        <div className="text-xs text-muted-foreground">
          {selectedIds.length} selected
        </div>
        <Button onClick={submit} disabled={!canSubmit}>
          <Shuffle className="h-3.5 w-3.5" />
          Equalize {selectedIds.length || ""}
        </Button>
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={requestsQ.data?.content ?? []}
          rowKey={(r) => r.requestId}
          isLoading={requestsQ.isLoading}
          emptyMessage="No requests."
          selectable
          selected={selected}
          onSelectionChange={setSelected}
          columns={[
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
          total={requestsQ.data?.totalElements}
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
