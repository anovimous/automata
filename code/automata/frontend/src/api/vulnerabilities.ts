import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  VulnerabilityCreationRequest,
  VulnerabilityDetailed,
  VulnerabilityPatchRequest,
  VulnerabilitySummary,
} from "@/types/domain";

const BASE = "/api/vulnerabilities";

export const vulnerabilitiesApi = {
  list: (pageable?: Pageable) =>
    apiFetch<PageHolder<VulnerabilitySummary>>(BASE, {
      query: { ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<VulnerabilityDetailed>(`${BASE}/${id}`),
  create: (req: VulnerabilityCreationRequest) =>
    apiFetch<void>(BASE, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: VulnerabilityPatchRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
