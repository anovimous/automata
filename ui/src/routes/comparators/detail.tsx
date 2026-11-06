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
import { Badge } from "@/components/ui/badge";
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
                      setSchemaNull: !schema && !!compQ.data?.schema,
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
          </CardHeader>
          <CardContent className="p-0">
            {(compQ.data?.modifiers ?? []).length > 0 ? (
              <ul className="divide-y divide-border">
                {[...(compQ.data?.modifiers ?? [])]
                  .sort((a, b) => (a.priority ?? 0) - (b.priority ?? 0))
                  .map((m) => (
                    <li key={m.id} className="px-4 py-2.5 text-xs flex items-start gap-3">
                      <Link
                        to="/modifiers/$id"
                        params={{ id: m.id }}
                        className="font-mono font-medium hover:underline"
                      >
                        {m.target}
                      </Link>
                      <span className="flex-1 text-muted-foreground">{m.description ?? "—"}</span>
                      <span className="font-mono tabular-nums text-muted-foreground">
                        priority {m.priority ?? "—"}
                      </span>
                      {m.isAvailable === false && <Badge variant="warning">unavailable</Badge>}
                    </li>
                  ))}
              </ul>
            ) : (
              <div className="p-4 text-xs text-muted-foreground">No modifier.</div>
            )}
            <div className="px-4 py-3 border-t border-border text-xs text-muted-foreground">
              The modifiers are chosen when the comparator is created; the API has no route to
              change them afterwards.{" "}
              <Link to="/modifiers" className="text-foreground hover:underline">
                Browse modifiers
              </Link>
            </div>
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
