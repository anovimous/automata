import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  RoutineCreationRequest,
  RoutineDetailed,
  RoutinePatchRequest,
  RoutineSummary,
} from "@/types/domain";

const BASE = "/api/routines";

export const routinesApi = {
  list: (query?: string, vulnId?: number, pageable?: Pageable) =>
    apiFetch<PageHolder<RoutineSummary>>(BASE, {
      query: { query, vulnId, ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<RoutineDetailed>(`${BASE}/${id}`),
  create: (req: RoutineCreationRequest) =>
    apiFetch<void>(BASE, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: RoutinePatchRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
