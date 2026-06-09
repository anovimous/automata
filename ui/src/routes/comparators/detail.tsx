import * as React from "react";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Save, Trash2 } from "lucide-react";
import {
  useComparator,
  useDeleteComparator,
  usePatchComparator,
} from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
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
import type { ComparatorSchema as ComparatorSchemaType } from "@/types/domain";

export function ComparatorDetailRoute() {
  const { id } = useParams({ strict: false }) as { id: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const compQ = useComparator(id);
  const patchMut = usePatchComparator();
  const deleteMut = useDeleteComparator();

  const [name, setName] = React.useState("");
  const [schema, setSchema] = React.useState<ComparatorSchemaType | "">("");
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (compQ.data) {
      setName(compQ.data.name);
      setSchema(compQ.data.schema ?? "");
    }
  }, [compQ.data]);

  useSetTopBar(
    [
      { label: "Comparators", to: "/comparators" },
      { label: compQ.data?.name ?? `#${id}` },
    ],
    null,
    [compQ.data?.name, id],
  );

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() => navigate({ to: "/comparators" })}
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>
      <PageHeader
        title={compQ.data?.name ?? `Comparator #${id}`}
        actions={
          <>
            <Button
              onClick={async () => {
                try {
                  await patchMut.mutateAsync({
                    id,
                    req: {
                      name: name.trim() || undefined,
                      schema: schema || undefined,
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

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
              <Label>Schema</Label>
              <Select value={schema} onValueChange={(v) => setSchema(v as ComparatorSchemaType)}>
                <SelectTrigger>
                  <SelectValue placeholder="—" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="REST">REST</SelectItem>
                  <SelectItem value="GRAPHQL">GRAPHQL</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Modifiers</CardTitle>
            <Badge variant="muted">{compQ.data?.modifiers?.length ?? 0}</Badge>
          </CardHeader>
          <CardContent>
            {compQ.data?.modifiers?.length ? (
              <ul className="space-y-1">
                {compQ.data.modifiers.map((m) => (
                  <li key={m.id} className="text-xs flex items-center gap-2">
                    <Link
                      to="/modifiers/$id"
                      params={{ id: m.id }}
                      className="font-mono hover:underline"
                    >
                      #{m.id}
                    </Link>
                    <Badge variant="outline">{m.target}</Badge>
                    {m.description && (
                      <span className="text-muted-foreground truncate">{m.description}</span>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <div className="text-xs text-muted-foreground">No modifiers attached.</div>
            )}
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete comparator?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteMut.mutateAsync(id);
            toast({ title: "Deleted", variant: "success" });
            navigate({ to: "/comparators" });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}
