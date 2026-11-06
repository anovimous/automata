import { apiCreateReturningId, apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  AgentContextCreationRequest,
  AgentContextDetailed,
  AgentContextItem,
  AgentContextItemAddRequest,
  AgentContextPatchRequest,
  AgentContextSummary,
  ResolvedTarget,
} from "@/types/domain";

const BASE = "/api/agent-contexts";

// Only the human-facing routes live here. The routes under
// /api/agent-contexts/uuid/{uuid}/... (and /api/external/ai/...) are for the AI
// agent (through the MCP server) and deliberately have no UI.
export const agentContextsApi = {
  list: (hostId?: number, pageable?: Pageable) =>
    apiFetch<PageHolder<AgentContextSummary>>(BASE, {
      query: { hostId, ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<AgentContextDetailed>(`${BASE}/${id}`),
  create: (req: AgentContextCreationRequest) => apiCreateReturningId(BASE, req),
  patch: (id: number, req: AgentContextPatchRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),

  addItem: (id: number, req: AgentContextItemAddRequest) =>
    apiFetch<AgentContextItem>(`${BASE}/${id}/items`, { method: "POST", body: req }),
  removeItem: (id: number, itemId: number) =>
    apiFetch<void>(`${BASE}/${id}/items/${itemId}`, { method: "DELETE", responseType: "void" }),

  addTenant: (id: number, tenantId: number) =>
    apiFetch<void>(`${BASE}/${id}/tenants`, {
      method: "POST",
      body: { tenantId },
      responseType: "void",
    }),
  removeTenant: (id: number, tenantId: number) =>
    apiFetch<void>(`${BASE}/${id}/tenants/${tenantId}`, {
      method: "DELETE",
      responseType: "void",
    }),

  resolvedRequests: (id: number) =>
    apiFetch<ResolvedTarget[]>(`${BASE}/${id}/resolved-requests`),
};
