import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  PatchProgramRequest,
  ProgramCreationRequest,
  ProgramDetailed,
  ProgramSummary,
} from "@/types/domain";

const BASE = "/api/programs";

export const programsApi = {
  list: (query?: string, pageable?: Pageable) =>
    apiFetch<PageHolder<ProgramSummary>>(BASE, {
      query: { query, ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<ProgramDetailed>(`${BASE}/${id}`),
  create: (req: ProgramCreationRequest) =>
    apiFetch<void>(BASE, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: PatchProgramRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
