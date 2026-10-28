import { apiFetch, apiUrl } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  Equalization,
  EqualizationCreationRequest,
  EqualitySet,
  EqualitySetPatchRequest,
  RawRequestAddition,
  RawResponseAddition,
  RequestFilter,
  RequestMetadata,
  RequestRaw,
  RequestSummary,
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
    apiFetch<PageHolder<RequestSummary>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  /** Requests of one equality set. */
  listEqual: (equalitySetId: number, pageable?: Pageable) =>
    apiFetch<PageHolder<RequestSummary>>(`${BASE}/equal`, {
      query: { equalitySetId, ...pageableToParams(pageable) },
    }),
  metadata: (id: number) => apiFetch<RequestMetadata>(`${BASE}/${id}/metadata`),
  raw: (id: number) => apiFetch<RequestRaw>(`${BASE}/${id}/raw`),
  /** Download URL of the body (inline or file, served as an attachment). */
  bodyUrl: (id: number) => apiUrl(`${BASE}/${id}/body/stream`),
  bodyBlob: (id: number) =>
    apiFetch<Blob>(`${BASE}/${id}/body/stream`, { responseType: "blob" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),

  addRaw: (req: RawRequestAddition) =>
    apiFetch<RequestSummary>(`${BASE}/raw`, { method: "POST", body: req }),

  associateResponse: (requestId: number, body: RawResponseAddition) =>
    apiFetch<RequestSummary>(`${BASE}/${requestId}/response`, {
      method: "POST",
      body,
    }),

  // ----- Equalizations (a comparator applied to the requests of a host) -----
  listEqualizations: async (hostId: number): Promise<Equalization[]> => {
    const res = await apiFetch<{ list: Equalization[] }>(`${BASE}/equalizations`, {
      query: { hostId },
    });
    return res?.list ?? [];
  },
  createEqualization: (req: EqualizationCreationRequest) =>
    apiFetch<void>(`${BASE}/equalizations`, {
      method: "POST",
      body: req,
      responseType: "void",
    }),
  /** The body of this PATCH is a bare JSON boolean. */
  setAutoEqualize: (equalizationId: number, autoEqualize: boolean) =>
    apiFetch<void>(`${BASE}/equalizations/${equalizationId}`, {
      method: "PATCH",
      body: autoEqualize,
      responseType: "void",
    }),
  runEqualization: (equalizationId: number) =>
    apiFetch<void>(`${BASE}/equalizations/run`, {
      method: "POST",
      query: { equalizationId },
      responseType: "void",
    }),

  // ----- Equality sets -----
  getEqualitySet: (setId: number) =>
    apiFetch<EqualitySet>(`${BASE}/equality-sets/${setId}`),
  patchEqualitySet: (setId: number, req: EqualitySetPatchRequest) =>
    apiFetch<void>(`${BASE}/equality-sets/${setId}`, {
      method: "PATCH",
      body: req,
      responseType: "void",
    }),
};
