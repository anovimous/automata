import * as React from "react";
import { Download, Eye } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/primitives";
import {
  MAX_PREVIEW_BYTES,
  base64ToText,
  bytesToText,
  formatBytes,
  isTextualContentType,
  prettyIfJson,
} from "@/lib/http";
import type { BodyMetadata, HeaderEntry } from "@/types/domain";

/**
 * One HTTP message (request or response): parsed headers, the raw head, and the
 * body. A body is stored inline (base64 in the raw payload) or as a file; both
 * are read through the same `/body/stream` route, which `bodyUrl` points at.
 */
export function HttpMessageView({
  headers,
  body,
  rawHeadBase64,
  rawBodyBase64,
  bodyUrl,
  loadBody,
  isLoading,
}: {
  headers: HeaderEntry[] | undefined;
  body: BodyMetadata | undefined;
  rawHeadBase64: string | null | undefined;
  rawBodyBase64: string | null | undefined;
  bodyUrl: string;
  /** Fetch the body of a file-stored message as a Blob. */
  loadBody: () => Promise<Blob>;
  isLoading?: boolean;
}) {
  const head = React.useMemo(() => base64ToText(rawHeadBase64), [rawHeadBase64]);

  if (isLoading) {
    return <div className="p-6 text-xs text-muted-foreground text-center">Loading…</div>;
  }

  return (
    <Tabs defaultValue="raw">
      <div className="px-4 pt-2">
        <TabsList>
          <TabsTrigger value="raw">Raw</TabsTrigger>
          <TabsTrigger value="headers">Headers{headers ? ` (${headers.length})` : ""}</TabsTrigger>
          <TabsTrigger value="body">Body</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="raw">
        <pre className="p-4 text-[11px] font-mono whitespace-pre-wrap break-all leading-relaxed text-foreground/90 overflow-auto max-h-[70vh]">
          {head || <span className="text-muted-foreground"># no head</span>}
          {body?.storage === "INLINE" && rawBodyBase64
            ? `\n\n${prettyIfJson(base64ToText(rawBodyBase64), body.contentType)}`
            : null}
          {body?.storage === "FILE" && (
            <span className="text-muted-foreground">
              {"\n\n"}# body stored as a file ({formatBytes(body.sizeBytes)}), see the Body tab
            </span>
          )}
        </pre>
      </TabsContent>
      <TabsContent value="headers">
        {headers && headers.length > 0 ? (
          <table className="w-full text-[12px]">
            <tbody>
              {headers.map((h, i) => (
                <tr key={i} className="border-t border-border first:border-t-0 align-top">
                  <td className="px-4 py-1.5 font-mono font-medium whitespace-nowrap">{h.name}</td>
                  <td className="px-2 py-1.5 font-mono text-muted-foreground break-all">
                    {h.value}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="p-6 text-xs text-muted-foreground text-center">No headers.</div>
        )}
      </TabsContent>
      <TabsContent value="body">
        <BodyPane
          body={body}
          rawBodyBase64={rawBodyBase64}
          bodyUrl={bodyUrl}
          loadBody={loadBody}
        />
      </TabsContent>
    </Tabs>
  );
}

function BodyPane({
  body,
  rawBodyBase64,
  bodyUrl,
  loadBody,
}: {
  body: BodyMetadata | undefined;
  rawBodyBase64: string | null | undefined;
  bodyUrl: string;
  loadBody: () => Promise<Blob>;
}) {
  const [preview, setPreview] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  // A different message was opened: forget the previous preview.
  React.useEffect(() => {
    setPreview(null);
    setError(null);
  }, [bodyUrl]);

  if (!body || body.storage === "NONE") {
    return <div className="p-6 text-xs text-muted-foreground text-center">No body.</div>;
  }

  const textual = isTextualContentType(body.contentType);

  const meta = (
    <div className="flex items-center gap-2 flex-wrap px-4 py-2 border-b border-border">
      <Badge variant="outline">{body.storage === "FILE" ? "Stored as file" : "Inline"}</Badge>
      {body.contentType && <Badge variant="muted">{body.contentType}</Badge>}
      {body.mimeFamily && <Badge variant="muted">{body.mimeFamily}</Badge>}
      <span className="text-xs font-mono text-muted-foreground">{formatBytes(body.sizeBytes)}</span>
      <div className="flex-1" />
      <a href={bodyUrl} download>
        <Button variant="outline" size="sm">
          <Download className="h-3.5 w-3.5" />
          Download
        </Button>
      </a>
    </div>
  );

  let content: React.ReactNode;
  if (body.storage === "INLINE") {
    content = textual ? (
      <pre className="p-4 text-[11px] font-mono whitespace-pre-wrap break-all leading-relaxed overflow-auto max-h-[60vh]">
        {prettyIfJson(base64ToText(rawBodyBase64), body.contentType)}
      </pre>
    ) : (
      <div className="p-6 text-xs text-muted-foreground text-center">
        Binary content, download it to inspect.
      </div>
    );
  } else if (preview !== null) {
    content = (
      <pre className="p-4 text-[11px] font-mono whitespace-pre-wrap break-all leading-relaxed overflow-auto max-h-[60vh]">
        {preview}
      </pre>
    );
  } else if (!textual || body.sizeBytes > MAX_PREVIEW_BYTES) {
    content = (
      <div className="p-6 text-xs text-muted-foreground text-center">
        {textual
          ? `Too large to preview (${formatBytes(body.sizeBytes)}), download it instead.`
          : "Binary content, download it to inspect."}
      </div>
    );
  } else {
    content = (
      <div className="p-6 text-center space-y-2">
        <Button
          variant="outline"
          size="sm"
          disabled={loading}
          onClick={async () => {
            setLoading(true);
            setError(null);
            try {
              const blob = await loadBody();
              const text = bytesToText(new Uint8Array(await blob.arrayBuffer()));
              setPreview(prettyIfJson(text, body.contentType));
            } catch (e) {
              setError(e instanceof Error ? e.message : "Could not load the body");
            } finally {
              setLoading(false);
            }
          }}
        >
          <Eye className="h-3.5 w-3.5" />
          Preview body
        </Button>
        {error && <div className="text-xs text-destructive">{error}</div>}
      </div>
    );
  }

  return (
    <div>
      {meta}
      {content}
    </div>
  );
}
