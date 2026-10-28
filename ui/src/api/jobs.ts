import { apiCreateReturningId, apiFetch, apiUrl } from "@/lib/fetcher";
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
    createdBy: f.createdBy,
    agentContextId: f.agentContextId,
    targetRequestId: f.targetRequestId,
  };
}

export const jobsApi = {
  list: (filter?: HttpJobFilter, pageable?: Pageable) =>
    apiFetch<PageHolder<HttpJobSummary>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<HttpJobResponse>(`${BASE}/${id}`),

  // POST answers 201 with a Location header naming the new job.
  createReturningId: (req: HttpJobCreationRequest) => apiCreateReturningId(BASE, req),

  /** DRAFT -> TOQUEUE. Needs the config files READY (and, for AI jobs, an approval). */
  queue: (draftJobId: number) =>
    apiFetch<void>(`${BASE}/${draftJobId}/queue`, { method: "POST", responseType: "void" }),
  /** Rebuild the config files of a DRAFT job whose preparation is PREPARING or FAILED. */
  retryPreparation: (draftJobId: number) =>
    apiFetch<void>(`${BASE}/${draftJobId}/retry-preparation`, {
      method: "POST",
      responseType: "void",
    }),
  /** Human approval of an AI-created DRAFT job. */
  approve: (jobId: number) =>
    apiFetch<void>(`${BASE}/${jobId}/approve`, { method: "POST", responseType: "void" }),
  /** Pause/resume/cancel of a job a worker holds are only *requested* here (202). */
  pause: (runningJobId: number) =>
    apiFetch<void>(`${BASE}/${runningJobId}/pause`, { method: "POST", responseType: "void" }),
  resume: (pausedJobId: number) =>
    apiFetch<void>(`${BASE}/${pausedJobId}/resume`, { method: "POST", responseType: "void" }),
  cancel: (id: number) =>
    apiFetch<void>(`${BASE}/${id}/cancel`, { method: "POST", responseType: "void" }),
  /** The backend answers 501 (not implemented) today. */
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),

  /** The result file as an attachment (`<a href download>`). */
  resultDownloadUrl: (id: number) => apiUrl(`${BASE}/${id}/result`),
  /** The result with the routine's own content type, for viewing in the page. */
  resultText: (id: number) =>
    apiFetch<string>(`${BASE}/${id}/result`, {
      query: { inline: true },
      responseType: "text",
    }),
};
