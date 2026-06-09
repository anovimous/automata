/**
 * Base API fetcher.
 *
 * All requests go through here. The browser always talks to /api on its own origin
 * — in production nginx reverse-proxies that to the spring backend, in dev the
 * Vite dev server does the same.
 */

export class ApiError extends Error {
  status: number;
  body: unknown;
  constructor(status: number, message: string, body: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

type QueryValue = string | number | boolean | undefined | null | (string | number)[];
export type QueryParams = Record<string, QueryValue>;

function buildQuery(params?: QueryParams): string {
  if (!params) return "";
  const parts: string[] = [];
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      for (const v of value) {
        if (v === undefined || v === null || v === "") continue;
        parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(v))}`);
      }
    } else {
      parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    }
  }
  return parts.length ? `?${parts.join("&")}` : "";
}

export interface FetchOptions {
  query?: QueryParams;
  body?: unknown;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  // For endpoints that return non-JSON (e.g. blob downloads, raw text)
  responseType?: "json" | "blob" | "text" | "void";
  // Spring sometimes returns the created resource's location as a header.
  // If you need it, set responseType: "void" and inspect the headers via fetch directly.
  signal?: AbortSignal;
  headers?: Record<string, string>;
}

async function readError(res: Response): Promise<{ message: string; body: unknown }> {
  const contentType = res.headers.get("Content-Type") || "";
  let body: unknown = null;
  let message = `${res.status} ${res.statusText}`;

  try {
    if (contentType.includes("application/json")) {
      body = await res.json();
      const b = body as { message?: string; error?: string };
      if (b && typeof b === "object") {
        message = b.message || b.error || message;
      }
    } else {
      const text = await res.text();
      body = text;
      if (text) message = text.slice(0, 200);
    }
  } catch {
    /* ignore */
  }
  return { message, body };
}

export async function apiFetch<T = unknown>(
  path: string,
  opts: FetchOptions = {},
): Promise<T> {
  const url = `${path}${buildQuery(opts.query)}`;
  const init: RequestInit = {
    method: opts.method || "GET",
    headers: {
      ...(opts.body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...(opts.headers || {}),
    },
    signal: opts.signal,
  };
  if (opts.body !== undefined) {
    init.body = JSON.stringify(opts.body);
  }

  const res = await fetch(url, init);

  if (!res.ok) {
    const { message, body } = await readError(res);
    throw new ApiError(res.status, message, body);
  }

  if (opts.responseType === "void" || res.status === 204) {
    return undefined as unknown as T;
  }
  if (opts.responseType === "blob") {
    return (await res.blob()) as unknown as T;
  }
  if (opts.responseType === "text") {
    return (await res.text()) as unknown as T;
  }
  // Default: JSON, but be lenient if backend returns empty.
  const text = await res.text();
  if (!text) return undefined as unknown as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as unknown as T;
  }
}

/**
 * Convenience helper: full URL of an API path.
 * Useful for things like `<a href={apiUrl('/api/jobs/http/123/result')}>Download</a>`.
 */
export function apiUrl(path: string, query?: QueryParams): string {
  return `${path}${buildQuery(query)}`;
}
