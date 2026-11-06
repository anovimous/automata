import { apiCreateReturningId, apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  FindingCreationRequest,
  FindingDetailed,
  FindingFilter,
  FindingPatchRequest,
  FindingReviewRequest,
  FindingSummary,
} from "@/types/domain";

const BASE = "/api/findings";

function filterToParams(f: FindingFilter | undefined): Record<string, string | number | undefined> {
  if (!f) return {};
  return {
    hostId: f.hostId,
    requestId: f.requestId,
    jobId: f.jobId,
    status: f.status,
    humanVerdict: f.humanVerdict,
    createdBy: f.createdBy,
  };
}

export const findingsApi = {
  list: (filter?: FindingFilter, pageable?: Pageable) =>
    apiFetch<PageHolder<FindingSummary>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<FindingDetailed>(`${BASE}/${id}`),
  create: (req: FindingCreationRequest) => apiCreateReturningId(BASE, req),
  patch: (id: number, req: FindingPatchRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  review: (id: number, req: FindingReviewRequest) =>
    apiFetch<void>(`${BASE}/${id}/review`, { method: "POST", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
