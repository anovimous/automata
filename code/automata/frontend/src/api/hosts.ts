import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  HostCreationRequest,
  HostDetailed,
  HostSummary,
  PatchHostRequest,
} from "@/types/domain";

const BASE = "/api/hosts";

export const hostsApi = {
  list: (programId: number, query?: string, pageable?: Pageable) =>
    apiFetch<PageHolder<HostSummary>>(BASE, {
      query: { programId, query, ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<HostDetailed>(`${BASE}/${id}`),
  create: (req: HostCreationRequest) =>
    apiFetch<void>(BASE, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: PatchHostRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
