import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Save, Trash2 } from "lucide-react";
import {
  useDeleteRoutine,
  usePatchRoutine,
  useRoutine,
  useVulnerabilities,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label, Textarea } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch, Checkbox } from "@/components/ui/primitives";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { ErrorState } from "@/components/common/states";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type {
  AllowedTarget,
  Overhead,
  Protocol,
} from "@/types/domain";

const TARGETS: AllowedTarget[] = [
  "SINGLE_HOST",
  "MULTILPLE_HOSTS",
  "SINGLE_REQUEST",
  "MULTIPLE_REQUESTS",
];

export function RoutineDetailRoute() {
  const { id } = useParams({ strict: false }) as { id: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const routineQ = useRoutine(id);
  const patchMut = usePatchRoutine();
  const deleteMut = useDeleteRoutine();
  const vulns = useVulnerabilities({ page: 0, size: 200 });

  const [description, setDescription] = React.useState("");
  const [overhead, setOverhead] = React.useState<Overhead | "">("");
  const [protocol, setProtocol] = React.useState<Protocol | "">("");
  const [vulnId, setVulnId] = React.useState("");
  const [available, setAvailable] = React.useState(true);
  const [allowedTargets, setAllowedTargets] = React.useState<AllowedTarget[]>([]);
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (routineQ.data) {
      setDescription(routineQ.data.description ?? "");
      setOverhead(routineQ.data.overhead ?? "");
      setProtocol(routineQ.data.protocol ?? "");
      setVulnId(
        routineQ.data.vulnerabilityId !== null && routineQ.data.vulnerabilityId !== undefined
          ? String(routineQ.data.vulnerabilityId)
          : "",
      );
      setAvailable(!!routineQ.data.isAvailableAtConsumer);
      setAllowedTargets(routineQ.data.allowedTargets ?? []);
    }
  }, [routineQ.data]);

  useSetTopBar(
    [
      { label: "Routines", to: "/routines" },
      { label: routineQ.data?.key ?? `#${id}` },
    ],
    null,
    [routineQ.data?.key, id],
  );

  async function save() {
    try {
      await patchMut.mutateAsync({
        id,
        req: {
          description: description || undefined,
          overhead: overhead || undefined,
          protocol: protocol || undefined,
          vulnerabilityId: vulnId ? Number(vulnId) : undefined,
          isAvailableAtConsumer: available,
          allowedTargets: allowedTargets.length ? allowedTargets : undefined,
        },
      });
      toast({ title: "Routine updated", variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Update failed";
      toast({ title: "Update failed", description: msg, variant: "destructive" });
    }
  }

  function toggle(t: AllowedTarget) {
    setAllowedTargets((cur) =>
      cur.includes(t) ? cur.filter((x) => x !== t) : [...cur, t],
    );
  }

  if (routineQ.error) {
    return (
      <PageContainer>
        <ErrorState
          title="Failed to load routine"
          description={routineQ.error instanceof Error ? routineQ.error.message : ""}
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
        onClick={() => navigate({ to: "/routines" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to routines
      </Button>

      <PageHeader
        title={<span className="font-mono">{routineQ.data?.key ?? `#${id}`}</span>}
        actions={
          <>
            <Button onClick={save} disabled={patchMut.isPending}>
              <Save className="h-3.5 w-3.5" />
              Save
            </Button>
            <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Edit</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1.5">
            <Label>Description</Label>
            <Textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="min-h-[100px]"
            />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <Label>Overhead</Label>
              <Select value={overhead} onValueChange={(v) => setOverhead(v as Overhead)}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="LOW">LOW</SelectItem>
                  <SelectItem value="MEDIUM">MEDIUM</SelectItem>
                  <SelectItem value="HIGH">HIGH</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Protocol</Label>
              <Select value={protocol} onValueChange={(v) => setProtocol(v as Protocol)}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HTTP">HTTP</SelectItem>
                  <SelectItem value="NETWORK">NETWORK</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Vulnerability</Label>
              <Select value={vulnId} onValueChange={setVulnId}>
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
          <div className="space-y-1.5">
            <Label>Allowed targets</Label>
            <div className="grid grid-cols-2 gap-1.5">
              {TARGETS.map((t) => (
                <label
                  key={t}
                  className="flex items-center gap-2 text-xs cursor-pointer font-mono"
                >
                  <Checkbox
                    checked={allowedTargets.includes(t)}
                    onCheckedChange={() => toggle(t)}
                  />
                  {t}
                </label>
              ))}
            </div>
          </div>
          <div className="flex items-center justify-between pt-2">
            <Label>Available at consumer</Label>
            <Switch checked={available} onCheckedChange={setAvailable} />
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete routine?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(id);
            toast({ title: "Routine deleted", variant: "success" });
            navigate({ to: "/routines" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
