import * as React from "react";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { Plus, X } from "lucide-react";
import { useFindings } from "@/hooks/useFindings";
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
import {
  CreatorPill,
  FindingStatusPill,
  SeverityPill,
  VerdictPill,
} from "@/components/job/pills";
import { CreateFindingDialog } from "@/components/finding/create-finding-dialog";
import { relativeTime } from "@/lib/utils";
import type {
  FindingCreator,
  FindingFilter,
  FindingStatus,
  HumanVerdict,
} from "@/types/domain";

export interface FindingsSearch {
  page?: number;
  size?: number;
  hostId?: number;
  requestId?: number;
  jobId?: number;
  status?: string;
  verdict?: string;
  createdBy?: string;
  /** Open the "report a finding" dialog (prefilled with requestId/jobId). */
  create?: boolean;
}

const ANY = "__any__";
const UNREVIEWED = "__unreviewed__";

export function FindingsListRoute() {
  useSetTopBar([{ label: "Findings" }]);
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as FindingsSearch;

  const page = search.page ?? 0;
  const size = search.size ?? 25;

  const filter: FindingFilter = {
    hostId: search.hostId,
    requestId: search.requestId,
    jobId: search.jobId,
    status: (search.status as FindingStatus) || undefined,
    humanVerdict:
      search.verdict && search.verdict !== UNREVIEWED
        ? (search.verdict as HumanVerdict)
        : undefined,
    createdBy: (search.createdBy as FindingCreator) || undefined,
  };

  const { data, isLoading } = useFindings(filter, {
    page,
    size,
    sort: ["creationDate,desc"],
  });
  // The API can filter on a verdict but not on "no verdict yet": do that here.
  const rows = (data?.content ?? []).filter((f) =>
    search.verdict === UNREVIEWED ? !f.humanVerdict : true,
  );

  const [createOpen, setCreateOpen] = React.useState(!!search.create);

  function update(patch: Partial<FindingsSearch>) {
    navigate({ to: "/findings", search: { ...search, create: undefined, ...patch } as never });
  }

  const hasScope = search.hostId || search.requestId || search.jobId;

  return (
    <PageContainer>
      <PageHeader
        title="Findings"
        description="Vulnerabilities reported by you or by the AI agent, with the human review."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-3.5 w-3.5" />
            Report finding
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-2 mb-3">
        <FilterSelect
          value={search.status}
          placeholder="Status"
          options={[
            ["SUGGESTED", "Suggested"],
            ["CONFIRMED", "Confirmed"],
          ]}
          onChange={(v) => update({ status: v, page: 0 })}
        />
        <FilterSelect
          value={search.verdict}
          placeholder="Review"
          options={[
            [UNREVIEWED, "Unreviewed"],
            ["TRUE_POSITIVE", "True positive"],
            ["FALSE_POSITIVE", "False positive"],
          ]}
          onChange={(v) => update({ verdict: v, page: 0 })}
        />
        <FilterSelect
          value={search.createdBy}
          placeholder="Created by"
          options={[
            ["AI", "AI"],
            ["USER", "User"],
          ]}
          onChange={(v) => update({ createdBy: v, page: 0 })}
        />
        {search.hostId && <ScopeChip label={`Host #${search.hostId}`} onClear={() => update({ hostId: undefined, page: 0 })} />}
        {search.requestId && <ScopeChip label={`Request #${search.requestId}`} onClear={() => update({ requestId: undefined, page: 0 })} />}
        {search.jobId && <ScopeChip label={`Job #${search.jobId}`} onClear={() => update({ jobId: undefined, page: 0 })} />}
        {(search.status || search.verdict || search.createdBy || hasScope) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate({ to: "/findings", search: {} as never })}
          >
            <X className="h-3 w-3" />
            Clear
          </Button>
        )}
      </div>

      <div className="border border-border rounded-lg overflow-hidden">
        <DataTable
          data={rows}
          rowKey={(r) => r.id}
          isLoading={isLoading}
          emptyMessage="No findings match these filters."
          onRowClick={(r) => navigate({ to: "/findings/$findingId", params: { findingId: r.id } })}
          columns={[
            {
              key: "id",
              header: "Finding",
              width: "w-20",
              cell: (r) => (
                <Link
                  to="/findings/$findingId"
                  params={{ findingId: r.id }}
                  className="font-mono tabular-nums text-xs hover:underline"
                >
                  #{r.id}
                </Link>
              ),
            },
            {
              key: "severity",
              header: "Severity",
              cell: (r) => <SeverityPill severity={r.severity} />,
            },
            {
              key: "status",
              header: "Status",
              cell: (r) => <FindingStatusPill status={r.status} />,
            },
            {
              key: "confidence",
              header: "Confidence",
              align: "right",
              cell: (r) => (
                <span className="font-mono tabular-nums text-muted-foreground">
                  {r.confidence.toFixed(1)}
                </span>
              ),
            },
            {
              key: "request",
              header: "Request",
              cell: (r) => (
                <span className="font-mono text-xs text-muted-foreground">
                  {r.requestId ? `#${r.requestId}` : "—"}
                </span>
              ),
            },
            {
              key: "creator",
              header: "By",
              cell: (r) => <CreatorPill creator={r.createdBy} />,
            },
            {
              key: "verdict",
              header: "Review",
              cell: (r) => <VerdictPill verdict={r.humanVerdict} />,
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
          onPageChange={(p) => update({ page: p })}
          onSizeChange={(s) => update({ size: s, page: 0 })}
        />
      </div>

      <CreateFindingDialog
        open={createOpen}
        onOpenChange={(o) => {
          setCreateOpen(o);
          if (!o && search.create) update({});
        }}
        initialRequestId={search.requestId}
        initialJobId={search.jobId}
        onCreated={(id) => {
          if (id) navigate({ to: "/findings/$findingId", params: { findingId: id } });
        }}
      />
    </PageContainer>
  );
}

function FilterSelect({
  value,
  placeholder,
  options,
  onChange,
}: {
  value: string | undefined;
  placeholder: string;
  options: [string, string][];
  onChange: (v: string | undefined) => void;
}) {
  return (
    <Select value={value ?? ANY} onValueChange={(v) => onChange(v === ANY ? undefined : v)}>
      <SelectTrigger className="w-[150px] h-8">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ANY}>Any {placeholder.toLowerCase()}</SelectItem>
        {options.map(([v, label]) => (
          <SelectItem key={v} value={v}>
            {label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function ScopeChip({ label, onClear }: { label: string; onClear: () => void }) {
  return (
    <Badge variant="accent" className="gap-1">
      {label}
      <button type="button" onClick={onClear} aria-label="Clear filter">
        <X className="h-3 w-3" />
      </button>
    </Badge>
  );
}
