import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  RawRequestAddition,
  RawResponseAddition,
  RequestFilter,
  RequestResponse,
  RequestsEqualization,
} from "@/types/domain";

const BASE = "/api/requests";

function filterToParams(f: RequestFilter | undefined): Record<string, string | number | undefined> {
  if (!f) return {};
  return {
    hostId: f.hostId,
    programId: f.programId,
    tenantId: f.tenantId,
    source: f.source,
    method: f.method,
    computatedPath: f.computatedPath,
    extension: f.extension,
    contentType: f.contentType,
  };
}

export const requestsApi = {
  list: (filter?: RequestFilter, pageable?: Pageable) =>
    apiFetch<PageHolder<RequestResponse>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<RequestResponse>(`${BASE}/${id}`),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),

  addRaw: (req: RawRequestAddition) =>
    apiFetch<void>(`${BASE}/raw`, { method: "POST", body: req, responseType: "void" }),

  associateResponse: (requestId: number, body: RawResponseAddition) =>
    apiFetch<void>(`${BASE}/${requestId}/response`, {
      method: "POST",
      body,
      responseType: "void",
    }),

  equalize: (req: RequestsEqualization) =>
    apiFetch<unknown>(`${BASE}/equalize`, {
      method: "POST",
      body: req,
    }),
};
