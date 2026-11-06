// Helpers for the raw HTTP messages the API serves as base64 (head + optional body).

export function base64ToBytes(b64: string | null | undefined): Uint8Array {
  if (!b64) return new Uint8Array(0);
  try {
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes;
  } catch {
    return new Uint8Array(0);
  }
}

export function bytesToBase64(bytes: Uint8Array): string {
  let bin = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(bin);
}

export function concatBytes(a: Uint8Array, b: Uint8Array): Uint8Array {
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}

export function bytesToText(bytes: Uint8Array): string {
  return new TextDecoder("utf-8", { fatal: false }).decode(bytes);
}

export function base64ToText(b64: string | null | undefined): string {
  return bytesToText(base64ToBytes(b64));
}

export function formatBytes(n: number | null | undefined): string {
  if (n === null || n === undefined) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/** Pretty-print when the text is JSON; otherwise return it untouched. */
export function prettyIfJson(text: string, contentType?: string | null): string {
  const looksJson =
    (contentType && /json/i.test(contentType)) || /^\s*[{[]/.test(text);
  if (!looksJson) return text;
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

const TEXTUAL_MIME = /^(text\/|application\/(json|xml|javascript|x-www-form-urlencoded|graphql)|.*\+(json|xml))/i;

/** Whether a body with this content type can sensibly be shown as text. */
export function isTextualContentType(contentType: string | null | undefined): boolean {
  if (!contentType) return true; // unknown: let the user try
  return TEXTUAL_MIME.test(contentType.trim());
}

/** Largest file-stored body we offer to preview inline. */
export const MAX_PREVIEW_BYTES = 1024 * 1024;
