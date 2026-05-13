import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { tenantsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  AuthenticationCreationRequest,
  AuthenticationPatchRequest,
  TenantCreationRequest,
  TenantPatchRequest,
} from "@/types/domain";

export const tenantsKeys = {
  all: ["tenants"] as const,
  list: (hostId: number, pageable?: Pageable) =>
    ["tenants", "list", { hostId, pageable }] as const,
  detail: (id: number) => ["tenants", "detail", id] as const,
  auth: (tenantId: number) => ["tenants", "auth", tenantId] as const,
};

export function useTenants(hostId: number | null | undefined, pageable?: Pageable) {
  return useQuery({
    enabled: hostId !== null && hostId !== undefined,
    queryKey: tenantsKeys.list(hostId ?? -1, pageable),
    queryFn: () => tenantsApi.list(hostId as number, pageable),
  });
}

export function useTenant(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: tenantsKeys.detail(id ?? -1),
    queryFn: () => tenantsApi.get(id as number),
  });
}

export function useCreateTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: TenantCreationRequest & { programId?: number; hostName?: string }) =>
      tenantsApi.create({ name: req.name, email: req.email, hostId: req.hostId }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: tenantsKeys.all });
      recordActivity({
        kind: "create-tenant",
        hostId: vars.hostId,
        programId: vars.programId,
        hostName: vars.hostName,
      });
    },
  });
}

export function usePatchTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      req,
    }: {
      id: number;
      req: TenantPatchRequest;
      hostId?: number;
      programId?: number;
    }) => tenantsApi.patch(id, req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: tenantsKeys.all });
      recordActivity({
        kind: "patch-tenant",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}

export function useDeleteTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; hostId?: number; programId?: number }) =>
      tenantsApi.delete(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: tenantsKeys.all });
      recordActivity({
        kind: "delete-tenant",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}

export function useAuth(tenantId: number | null | undefined) {
  return useQuery({
    enabled: tenantId !== null && tenantId !== undefined,
    queryKey: tenantsKeys.auth(tenantId ?? -1),
    queryFn: () => tenantsApi.getAuth(tenantId as number),
    retry: false,
  });
}

export function useCreateAuth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      tenantId,
      req,
    }: {
      tenantId: number;
      req: AuthenticationCreationRequest;
      hostId?: number;
      programId?: number;
    }) => tenantsApi.createAuth(tenantId, req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: tenantsKeys.auth(vars.tenantId) });
      recordActivity({
        kind: "create-auth",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}

export function usePatchAuth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      tenantId,
      req,
    }: {
      tenantId: number;
      req: AuthenticationPatchRequest;
      hostId?: number;
      programId?: number;
    }) => tenantsApi.patchAuth(tenantId, req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: tenantsKeys.auth(vars.tenantId) });
      recordActivity({
        kind: "patch-auth",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}

export function useDeleteAuth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ tenantId }: { tenantId: number; hostId?: number; programId?: number }) =>
      tenantsApi.deleteAuth(tenantId),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: tenantsKeys.auth(vars.tenantId) });
      recordActivity({
        kind: "delete-auth",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}
