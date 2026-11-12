import * as React from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";
import { VulnerabilityMultiPicker } from "@/components/common/vulnerability-picker";
import { useCreateWordlist } from "@/hooks/useResources";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import { formatBytes } from "@/lib/http";

/** The core refuses a file above this (spring.servlet.multipart.max-file-size). */
export const MAX_WORDLIST_BYTES = 512 * 1024 * 1024;
export const MAX_WORDLIST_NAME = 255;
export const MAX_WORDLIST_SMALL_DESCRIPTION = 500;
export const MAX_WORDLIST_PAYLOAD_CONTENT_DESCRIPTION = 2000;

export function CreateWordlistDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (o: boolean) => void;
}) {
  const [file, setFile] = React.useState<File | null>(null);
  const [name, setName] = React.useState("");
  const [smallDescription, setSmallDescription] = React.useState("");
  const [payloadContentDescription, setPayloadContentDescription] = React.useState("");
  const [vulnIds, setVulnIds] = React.useState<number[]>([]);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const createMut = useCreateWordlist();
  const { toast } = useToast();

  React.useEffect(() => {
    if (open) {
      setFile(null);
      setName("");
      setSmallDescription("");
      setPayloadContentDescription("");
      setVulnIds([]);
      if (inputRef.current) inputRef.current.value = "";
    }
  }, [open]);

  const problems: string[] = [];
  if (!file) problems.push("Pick the wordlist file.");
  else if (file.size === 0) problems.push("The file is empty.");
  else if (file.size > MAX_WORDLIST_BYTES)
    problems.push(`The file is above the ${formatBytes(MAX_WORDLIST_BYTES)} limit.`);
  if (name.length > MAX_WORDLIST_NAME) problems.push(`The name is at most ${MAX_WORDLIST_NAME} characters.`);
  if (!smallDescription.trim()) problems.push("Give a short description.");
  else if (smallDescription.length > MAX_WORDLIST_SMALL_DESCRIPTION)
    problems.push(`The short description is at most ${MAX_WORDLIST_SMALL_DESCRIPTION} characters.`);
  if (!payloadContentDescription.trim()) problems.push("Give a content description.");
  else if (payloadContentDescription.length > MAX_WORDLIST_PAYLOAD_CONTENT_DESCRIPTION)
    problems.push(
      `The content description is at most ${MAX_WORDLIST_PAYLOAD_CONTENT_DESCRIPTION} characters.`,
    );

  async function submit() {
    if (!file || problems.length > 0) return;
    try {
      await createMut.mutateAsync({
        file,
        metadata: {
          name: name.trim() || undefined,
          smallDescription: smallDescription.trim(),
          payloadContentDescription: payloadContentDescription.trim(),
          vulnIds: vulnIds.length > 0 ? vulnIds : undefined,
        },
      });
      toast({ title: "Wordlist created", variant: "success" });
      onOpenChange(false);
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Create failed";
      toast({ title: "Create failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New wordlist</DialogTitle>
          <DialogDescription>
            Upload a text file of up to 512 MB. The core keeps it, and cannot change it later; a
            file that is already a wordlist is refused.
          </DialogDescription>
        </DialogHeader>
        <div className="px-5 py-4 space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="wl-file">File</Label>
            <input
              id="wl-file"
              ref={inputRef}
              type="file"
              onChange={(e) => {
                const f = e.target.files?.[0] ?? null;
                setFile(f);
                if (f && !name.trim()) setName(f.name);
              }}
              className="block w-full text-xs file:mr-3 file:rounded-md file:border file:border-border file:bg-muted file:px-3 file:py-1.5 file:text-xs"
            />
            {file && (
              <div className="text-[11px] text-muted-foreground font-mono">
                {file.name} · {formatBytes(file.size)}
              </div>
            )}
          </div>
          <div className="space-y-1.5">
            <Label>Name (optional, the file name by default)</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="common-paths"
              className="font-mono"
            />
          </div>
          <div className="space-y-1.5">
            <Label>Short description (shown in the list)</Label>
            <Textarea
              value={smallDescription}
              onChange={(e) => setSmallDescription(e.target.value)}
              rows={2}
              placeholder="What it is good for."
            />
          </div>
          <div className="space-y-1.5">
            <Label>Content description</Label>
            <Textarea
              value={payloadContentDescription}
              onChange={(e) => setPayloadContentDescription(e.target.value)}
              rows={4}
              placeholder="What the entries are: their kind, format and where they came from."
            />
          </div>
          <div className="space-y-1.5">
            <Label>Vulnerabilities (optional)</Label>
            <VulnerabilityMultiPicker value={vulnIds} onChange={setVulnIds} />
          </div>
          {file && problems.length > 0 && (
            <ul className="text-[11px] text-destructive list-disc pl-4 space-y-0.5">
              {problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={problems.length > 0 || createMut.isPending}>
            {createMut.isPending ? "Uploading…" : "Upload"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
