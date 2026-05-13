import { apiFetch, apiUrl } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  HttpJobCreationRequest,
  HttpJobFilter,
  HttpJobResponse,
  HttpJobSummary,
} from "@/types/domain";

const BASE = "/api/jobs/http";

function filterToParams(
  f: HttpJobFilter | undefined,
): Record<string, string | number | string[] | undefined> {
  if (!f) return {};
  return {
    programId: f.programId,
    hostId: f.hostId,
    routineId: f.routineId,
    scope: f.scope,
    currentStates: f.currentStates,
  };
}

export const jobsApi = {
  list: (filter?: HttpJobFilter, pageable?: Pageable) =>
    apiFetch<PageHolder<HttpJobSummary>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<HttpJobResponse>(`${BASE}/${id}`),

  // POST returns 201 Created with a Location header. We discard the body
  // and read the location via a one-off raw fetch when we need the new id.
  createReturningId: async (req: HttpJobCreationRequest): Promise<number | null> => {
    const res = await fetch(BASE, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(req),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || `Job creation failed: ${res.status}`);
    }
    const loc = res.headers.get("Location");
    if (!loc) return null;
    const m = loc.match(/\/api\/jobs\/http\/(\d+)/);
    return m ? Number(m[1]) : null;
  },

  queue: (draftJobId: number) =>
    apiFetch<void>(`${BASE}/${draftJobId}/queue`, { method: "POST", responseType: "void" }),
  pause: (runningJobId: number) =>
    apiFetch<void>(`${BASE}/${runningJobId}/pause`, { method: "POST", responseType: "void" }),
  resume: (pausedJobId: number) =>
    apiFetch<void>(`${BASE}/${pausedJobId}/resume`, { method: "POST", responseType: "void" }),
  cancel: (id: number) =>
    apiFetch<void>(`${BASE}/${id}/cancel`, { method: "POST", responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),

  // Direct URL of the streaming download — usable as href on <a download>
  resultDownloadUrl: (id: number) => apiUrl(`${BASE}/${id}/result`),
};
