import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Save, Trash2 } from "lucide-react";
import { useDeleteWordlist, usePatchWordlist, useWordlist } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";

export function WordlistDetailRoute() {
  const { id } = useParams({ strict: false }) as { id: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const wlQ = useWordlist(id);
  const patchMut = usePatchWordlist();
  const deleteMut = useDeleteWordlist();

  const [name, setName] = React.useState("");
  const [path, setPath] = React.useState("");
  const [lines, setLines] = React.useState("");
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (wlQ.data) {
      setName(wlQ.data.name ?? "");
      setPath(wlQ.data.path);
      setLines(wlQ.data.numberOfLines?.toString() ?? "");
    }
  }, [wlQ.data]);

  useSetTopBar(
    [
      { label: "Wordlists", to: "/wordlists" },
      { label: wlQ.data?.name ?? `#${id}` },
    ],
    null,
    [wlQ.data?.name, id],
  );

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/wordlists" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>
      <PageHeader
        title={<span className="font-mono">{wlQ.data?.name || `Wordlist #${id}`}</span>}
        actions={
          <>
            <Button
              onClick={async () => {
                try {
                  await patchMut.mutateAsync({
                    id,
                    req: {
                      name: name.trim() || undefined,
                      setNameNull: !name.trim() && !!wlQ.data?.name,
                      path: path.trim() || undefined,
                      numberOfLines: lines ? Number(lines) : undefined,
                      setNumberOfLinesNull: !lines && wlQ.data?.numberOfLines != null,
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
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="font-mono"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Path</Label>
            <Input
              value={path}
              onChange={(e) => setPath(e.target.value)}
              className="font-mono"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Number of lines</Label>
            <Input type="number" value={lines} onChange={(e) => setLines(e.target.value)} />
          </div>
          <div className="text-xs text-muted-foreground">
            The vulnerabilities a wordlist is linked to are set when it is created; the API
            doesn't return them.
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete wordlist?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(id);
            toast({ title: "Deleted", variant: "success" });
            navigate({ to: "/wordlists" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
