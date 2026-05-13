import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  Authentication,
  AuthenticationCreationRequest,
  AuthenticationPatchRequest,
  TenantCreationRequest,
  TenantDetailed,
  TenantPatchRequest,
  TenantSummary,
} from "@/types/domain";

const BASE = "/api/tenants";

export const tenantsApi = {
  list: (hostId: number, pageable?: Pageable) =>
    apiFetch<PageHolder<TenantSummary>>(BASE, {
      query: { hostId, ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<TenantDetailed>(`${BASE}/${id}`),
  create: (req: TenantCreationRequest) =>
    apiFetch<void>(BASE, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: TenantPatchRequest) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),

  // Authentication subresource
  getAuth: (tenantId: number) =>
    apiFetch<Authentication>(`${BASE}/${tenantId}/authentication`),
  createAuth: (tenantId: number, req: AuthenticationCreationRequest) =>
    apiFetch<void>(`${BASE}/${tenantId}/authentication`, {
      method: "POST",
      body: req,
      responseType: "void",
    }),
  patchAuth: (tenantId: number, req: AuthenticationPatchRequest) =>
    apiFetch<void>(`${BASE}/${tenantId}/authentication`, {
      method: "PATCH",
      body: req,
      responseType: "void",
    }),
  deleteAuth: (tenantId: number) =>
    apiFetch<void>(`${BASE}/${tenantId}/authentication`, {
      method: "DELETE",
      responseType: "void",
    }),
};
