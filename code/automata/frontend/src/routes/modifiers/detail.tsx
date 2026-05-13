import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Save, Trash2 } from "lucide-react";
import {
  useDeleteModifier,
  useModifier,
  usePatchModifier,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label, Textarea } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/primitives";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function ModifierDetailRoute() {
  const { id } = useParams({ strict: false }) as { id: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const modQ = useModifier(id);
  const patchMut = usePatchModifier();
  const deleteMut = useDeleteModifier();

  const [description, setDescription] = React.useState("");
  const [available, setAvailable] = React.useState(true);
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (modQ.data) {
      setDescription(modQ.data.description ?? "");
      setAvailable(!!modQ.data.available);
    }
  }, [modQ.data]);

  useSetTopBar(
    [
      { label: "Modifiers", to: "/modifiers" },
      { label: modQ.data ? `${modQ.data.target} #${id}` : `#${id}` },
    ],
    null,
    [modQ.data?.target, id],
  );

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/modifiers" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <span>Modifier #{id}</span>
            {modQ.data?.target && <Badge variant="outline">{modQ.data.target}</Badge>}
            {modQ.data?.schema && <Badge variant="muted">{modQ.data.schema}</Badge>}
          </span>
        }
        actions={
          <>
            <Button
              onClick={async () => {
                try {
                  await patchMut.mutateAsync({
                    id,
                    req: {
                      description: description || undefined,
                      isAvailable: available,
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
            <Label>Description</Label>
            <Textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="min-h-[120px]"
            />
          </div>
          <div className="flex items-center justify-between pt-2">
            <Label>Available at consumer</Label>
            <Switch checked={available} onCheckedChange={setAvailable} />
          </div>
          <div className="text-[11px] text-muted-foreground pt-1">
            Priority: <span className="font-mono">{modQ.data?.priority ?? "—"}</span>
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete modifier?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(id);
            toast({ title: "Deleted", variant: "success" });
            navigate({ to: "/modifiers" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
