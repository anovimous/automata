import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { ArrowLeft, Download, Save, Trash2 } from "lucide-react";
import { useDeleteWordlist, usePatchWordlist, useWordlist } from "@/hooks/useResources";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";
import { VulnerabilityMultiPicker } from "@/components/common/vulnerability-picker";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { formatBytes } from "@/lib/http";
import { wordlistsApi } from "@/api";
import {
  MAX_WORDLIST_DESCRIPTION,
  MAX_WORDLIST_NAME,
} from "@/components/wordlist/create-wordlist-dialog";

export function WordlistDetailRoute() {
  const { id } = useParams({ strict: false }) as { id: number };
  const navigate = useNavigate();
  const { toast } = useToast();

  const wlQ = useWordlist(id);
  const patchMut = usePatchWordlist();
  const deleteMut = useDeleteWordlist();

  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  // The API doesn't return the vulnerabilities of a wordlist, so they are only sent when the
  // user chooses to replace them.
  const [changeVulns, setChangeVulns] = React.useState(false);
  const [vulnIds, setVulnIds] = React.useState<number[]>([]);
  const [deleteOpen, setDeleteOpen] = React.useState(false);

  React.useEffect(() => {
    if (wlQ.data) {
      setName(wlQ.data.name ?? "");
      setDescription(wlQ.data.description ?? "");
      setChangeVulns(false);
      setVulnIds([]);
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

  const tooLong =
    name.length > MAX_WORDLIST_NAME || description.length > MAX_WORDLIST_DESCRIPTION;

  async function save() {
    if (tooLong) return;
    try {
      await patchMut.mutateAsync({
        id,
        req: {
          name: name.trim() || undefined,
          setNameNull: !name.trim() && !!wlQ.data?.name,
          description: description.trim() || undefined,
          setDescriptionNull: !description.trim() && !!wlQ.data?.description,
          vulnIds: changeVulns ? vulnIds : undefined,
        },
      });
      toast({ title: "Saved", variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Save failed";
      toast({ title: "Save failed", description: msg, variant: "destructive" });
    }
  }

  const data = wlQ.data;

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
        title={<span className="font-mono">{data?.name || `Wordlist #${id}`}</span>}
        actions={
          <>
            <a href={wordlistsApi.contentUrl(id)} download>
              <Button variant="outline">
                <Download className="h-3.5 w-3.5" />
                Download
              </Button>
            </a>
            <Button onClick={save} disabled={patchMut.isPending || tooLong}>
              <Save className="h-3.5 w-3.5" />
              Save
            </Button>
            <Button variant="outline" size="icon" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4">
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
              <Label>Description</Label>
              <Textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={3}
              />
            </div>
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <Label>Vulnerabilities</Label>
                <label className="flex items-center gap-2 text-xs text-muted-foreground cursor-pointer">
                  <input
                    type="checkbox"
                    checked={changeVulns}
                    onChange={(e) => setChangeVulns(e.target.checked)}
                    className="h-3 w-3"
                  />
                  Replace the linked vulnerabilities
                </label>
              </div>
              {changeVulns ? (
                <VulnerabilityMultiPicker value={vulnIds} onChange={setVulnIds} />
              ) : (
                <div className="text-xs text-muted-foreground">
                  The API doesn't return the vulnerabilities a wordlist is linked to. Tick the box
                  to set them again (an empty choice unlinks all of them).
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        <Card className="self-start">
          <CardHeader>
            <CardTitle>File</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <Fact label="Lines" value={data ? data.numberOfLines.toLocaleString() : "—"} />
            <Fact label="Size" value={data ? formatBytes(data.sizeBytes) : "—"} />
            <Fact label="SHA-256" value={data?.sha256 ?? "—"} mono />
            <div className="text-xs text-muted-foreground">
              The file never changes: to use other content, upload a new wordlist.
            </div>
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="Delete wordlist?"
        description="Refused while any job refers to it."
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

function Fact({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</div>
      <div className={`mt-1 break-all ${mono ? "font-mono text-[11px]" : "font-mono tabular-nums"}`}>
        {value}
      </div>
    </div>
  );
}
