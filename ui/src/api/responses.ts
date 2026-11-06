import { apiFetch, apiUrl } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  ResponseFilter,
  ResponseMetadata,
  ResponseRaw,
  ResponseSummary,
} from "@/types/domain";

const BASE = "/api/responses";

function filterToParams(f: ResponseFilter | undefined): Record<string, string | number | undefined> {
  if (!f) return {};
  return {
    statusCode: f.statusCode,
    contentType: f.contentType,
    contentLength: f.contentLength,
    hostId: f.hostId,
  };
}

export const responsesApi = {
  list: (filter?: ResponseFilter, pageable?: Pageable) =>
    apiFetch<PageHolder<ResponseSummary>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  metadata: (id: number) => apiFetch<ResponseMetadata>(`${BASE}/${id}/metadata`),
  raw: (id: number) => apiFetch<ResponseRaw>(`${BASE}/${id}/raw`),
  bodyUrl: (id: number) => apiUrl(`${BASE}/${id}/body/stream`),
  bodyBlob: (id: number) =>
    apiFetch<Blob>(`${BASE}/${id}/body/stream`, { responseType: "blob" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
