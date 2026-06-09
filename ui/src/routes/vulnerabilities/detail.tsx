import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Save, Trash2 } from "lucide-react";
import {
  useDeleteVulnerability,
  usePatchVulnerability,
  useVulnerabilities,
  useVulnerability,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function VulnerabilityDetailRoute() {
  const { id } = useParams({ strict: false }) as { id: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const vulnQ = useVulnerability(id);
  const vulns = useVulnerabilities({ page: 0, size: 200 });
  const patchMut = usePatchVulnerability();
  const deleteMut = useDeleteVulnerability();

  const [name, setName] = React.useState("");
  const [parentId, setParentId] = React.useState<string>("");
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (vulnQ.data) {
      setName(vulnQ.data.name);
      setParentId(vulnQ.data.parentId ? String(vulnQ.data.parentId) : "");
    }
  }, [vulnQ.data]);

  useSetTopBar(
    [
      { label: "Vulnerabilities", to: "/vulnerabilities" },
      { label: vulnQ.data?.name ?? `#${id}` },
    ],
    null,
    [vulnQ.data?.name, id],
  );

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/vulnerabilities" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>
      <PageHeader
        title={vulnQ.data?.name ?? `Vulnerability #${id}`}
        actions={
          <>
            <Button
              onClick={async () => {
                try {
                  await patchMut.mutateAsync({
                    id,
                    req: {
                      name: name.trim() || undefined,
                      parentId: parentId ? Number(parentId) : undefined,
                    },
                  });
                  toast({ title: "Saved", variant: "success" });
                } catch (e) {
                  const msg = e instanceof ApiError ? e.message : "Save failed";
                  toast({ title: "Save failed", description: msg, variant: "destructive" });
                }
              }}
              disabled={patchMut.isPending}
            >
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
            <Label>Name</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label>Parent</Label>
            <Select value={parentId} onValueChange={setParentId}>
              <SelectTrigger>
                <SelectValue placeholder="—" />
              </SelectTrigger>
              <SelectContent>
                {vulns.data?.content
                  .filter((v) => v.id !== id)
                  .map((v) => (
                    <SelectItem key={v.id} value={String(v.id)}>
                      {v.name}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete vulnerability?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(id);
            toast({ title: "Deleted", variant: "success" });
            navigate({ to: "/vulnerabilities" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
