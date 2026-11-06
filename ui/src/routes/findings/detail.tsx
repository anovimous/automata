import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Check, Save, Trash2, X } from "lucide-react";
import {
  useDeleteFinding,
  useFinding,
  usePatchFinding,
  useReviewFinding,
} from "@/hooks/useFindings";
import { useHost } from "@/hooks/useHosts";
import { useVulnerability } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CreatorPill, FindingStatusPill, SeverityPill, VerdictPill } from "@/components/job/pills";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { ErrorState } from "@/components/common/states";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { formatDate } from "@/lib/utils";
import type { FindingStatus, HumanVerdict } from "@/types/domain";

export function FindingDetailRoute() {
  const { findingId } = useParams({ strict: false }) as { findingId: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const findingQ = useFinding(findingId);
  const f = findingQ.data;
  const hostQ = useHost(f?.hostId ?? null);
  const vulnQ = useVulnerability(f?.vulnerabilityId ?? null);
  const patchMut = usePatchFinding();
  const reviewMut = useReviewFinding();
  const deleteMut = useDeleteFinding();

  const [severity, setSeverity] = React.useState("");
  const [confidence, setConfidence] = React.useState("");
  const [status, setStatus] = React.useState<FindingStatus>("SUGGESTED");
  const [evidence, setEvidence] = React.useState("");
  const [poc, setPoc] = React.useState("");
  const [comment, setComment] = React.useState("");
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (f) {
      setSeverity(String(f.severity));
      setConfidence(String(f.confidence));
      setStatus(f.status);
      setEvidence(f.evidence ?? "");
      setPoc(f.pocReproductionSteps ?? "");
      setComment(f.humanComment ?? "");
    }
  }, [f]);

  useSetTopBar(
    [{ label: "Findings", to: "/findings" }, { label: `#${findingId}` }],
    null,
    [findingId],
  );

  if (findingQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load finding"
          description={findingQ.error instanceof Error ? findingQ.error.message : ""}
        />
      </PageContainer>
    );
  }

  const programId = hostQ.data?.programId;

  async function save() {
    try {
      await patchMut.mutateAsync({
        id: findingId,
        req: {
          severity: Number(severity),
          confidence: Number(confidence),
          status,
          evidence,
          pocReproductionSteps: poc.trim() || undefined,
          setPocReproductionStepsNull: !poc.trim() && !!f?.pocReproductionSteps,
        },
      });
      toast({ title: "Finding updated", variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Update failed";
      toast({ title: "Update failed", description: msg, variant: "destructive" });
    }
  }

  async function review(verdict: HumanVerdict) {
    try {
      await reviewMut.mutateAsync({
        id: findingId,
        req: { verdict, comment: comment.trim() || undefined },
      });
      toast({
        title: verdict === "TRUE_POSITIVE" ? "Marked as true positive" : "Marked as false positive",
        variant: "success",
      });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Review failed";
      toast({ title: "Review failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/findings" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to findings
      </Button>

      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <span>Finding #{findingId}</span>
            {f && <SeverityPill severity={f.severity} />}
            {f && <FindingStatusPill status={f.status} />}
            {f && <CreatorPill creator={f.createdBy} />}
          </span>
        }
        description={vulnQ.data?.name}
        actions={
          <>
            <Button onClick={save} disabled={patchMut.isPending || !evidence.trim()}>
              <Save className="h-3.5 w-3.5" />
              Save
            </Button>
            <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </>
        }
      />

      {!f ? (
        <div className="text-xs text-muted-foreground">Loading…</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-4">
          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Details</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="grid grid-cols-3 gap-3">
                  <div className="space-y-1.5">
                    <Label>Severity (CVSS)</Label>
                    <Input
                      type="number"
                      min={0}
                      max={10}
                      step={0.1}
                      value={severity}
                      onChange={(e) => setSeverity(e.target.value)}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>Confidence</Label>
                    <Input
                      type="number"
                      min={0}
                      max={10}
                      step={0.1}
                      value={confidence}
                      onChange={(e) => setConfidence(e.target.value)}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label>Status</Label>
                    <Select value={status} onValueChange={(v) => setStatus(v as FindingStatus)}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="SUGGESTED">Suggested</SelectItem>
                        <SelectItem value="CONFIRMED">Confirmed</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div className="space-y-1.5">
                  <Label>Evidence (required)</Label>
                  <Textarea
                    value={evidence}
                    onChange={(e) => setEvidence(e.target.value)}
                    rows={5}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label>Reproduction steps{status === "CONFIRMED" ? " (required)" : ""}</Label>
                  <Textarea value={poc} onChange={(e) => setPoc(e.target.value)} rows={6} />
                </div>
              </CardContent>
            </Card>

            {f.aiDescription && (
              <Card>
                <CardHeader>
                  <CardTitle>AI description</CardTitle>
                </CardHeader>
                <CardContent className="text-sm whitespace-pre-wrap">{f.aiDescription}</CardContent>
              </Card>
            )}
          </div>

          <div className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Human review</CardTitle>
                <VerdictPill verdict={f.humanVerdict} />
              </CardHeader>
              <CardContent className="space-y-3">
                <Textarea
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={3}
                  placeholder="Comment (optional)"
                />
                <div className="flex gap-2">
                  <Button
                    className="flex-1"
                    variant="outline"
                    disabled={reviewMut.isPending}
                    onClick={() => review("TRUE_POSITIVE")}
                  >
                    <Check className="h-3.5 w-3.5" />
                    True positive
                  </Button>
                  <Button
                    className="flex-1"
                    variant="outline"
                    disabled={reviewMut.isPending}
                    onClick={() => review("FALSE_POSITIVE")}
                  >
                    <X className="h-3.5 w-3.5" />
                    False positive
                  </Button>
                </div>
                {f.reviewedAt && (
                  <div className="text-[11px] text-muted-foreground">
                    Reviewed {formatDate(f.reviewedAt)}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Links</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                <Row label="Host">
                  {f.hostId && programId ? (
                    <Link
                      to="/programs/$programId/hosts/$hostId"
                      params={{ programId, hostId: f.hostId }}
                      className="font-mono hover:underline"
                    >
                      {hostQ.data?.host ?? `#${f.hostId}`}
                    </Link>
                  ) : (
                    "—"
                  )}
                </Row>
                <Row label="Request">
                  {f.requestId && f.hostId && programId ? (
                    <Link
                      to="/programs/$programId/hosts/$hostId/requests/$requestId"
                      params={{ programId, hostId: f.hostId, requestId: f.requestId }}
                      className="font-mono hover:underline"
                    >
                      #{f.requestId}
                    </Link>
                  ) : (
                    "—"
                  )}
                </Row>
                <Row label="Job">
                  {f.jobId ? (
                    <Link
                      to="/jobs/$jobId"
                      params={{ jobId: f.jobId }}
                      className="font-mono hover:underline"
                    >
                      #{f.jobId}
                    </Link>
                  ) : (
                    "—"
                  )}
                </Row>
                <Row label="Vulnerability">
                  {f.vulnerabilityId ? (
                    <Link
                      to="/vulnerabilities/$id"
                      params={{ id: f.vulnerabilityId }}
                      className="hover:underline"
                    >
                      {vulnQ.data?.name ?? `#${f.vulnerabilityId}`}
                    </Link>
                  ) : (
                    "—"
                  )}
                </Row>
                <Row label="Created">{formatDate(f.creationDate)}</Row>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete finding?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(findingId);
            toast({ title: "Finding deleted", variant: "success" });
            navigate({ to: "/findings" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</span>
      <span className="text-sm text-right min-w-0 truncate">{children}</span>
    </div>
  );
}
