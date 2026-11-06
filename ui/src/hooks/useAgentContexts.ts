import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { agentContextsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  AgentContextCreationRequest,
  AgentContextItemAddRequest,
  AgentContextPatchRequest,
} from "@/types/domain";

export const agentContextsKeys = {
  all: ["agent-contexts"] as const,
  list: (hostId: number | undefined, pageable: Pageable | undefined) =>
    ["agent-contexts", "list", { hostId, pageable }] as const,
  detail: (id: number) => ["agent-contexts", "detail", id] as const,
  resolved: (id: number) => ["agent-contexts", "resolved", id] as const,
};

export function useAgentContexts(hostId?: number, pageable?: Pageable) {
  return useQuery({
    queryKey: agentContextsKeys.list(hostId, pageable),
    queryFn: () => agentContextsApi.list(hostId, pageable),
  });
}

export function useAgentContext(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: agentContextsKeys.detail(id ?? -1),
    queryFn: () => agentContextsApi.get(id as number),
  });
}

export function useResolvedTargets(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: agentContextsKeys.resolved(id ?? -1),
    queryFn: () => agentContextsApi.resolvedRequests(id as number),
  });
}

export function useCreateAgentContext() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: AgentContextCreationRequest) => agentContextsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: agentContextsKeys.all });
      recordActivity({ kind: "create-agent-context" });
    },
  });
}

export function usePatchAgentContext() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: AgentContextPatchRequest }) =>
      agentContextsApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: agentContextsKeys.all });
      recordActivity({ kind: "patch-agent-context" });
    },
  });
}

export function useDeleteAgentContext() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => agentContextsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: agentContextsKeys.all });
      recordActivity({ kind: "delete-agent-context" });
    },
  });
}

export function useAddContextItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: AgentContextItemAddRequest }) =>
      agentContextsApi.addItem(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: agentContextsKeys.all }),
  });
}

export function useRemoveContextItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, itemId }: { id: number; itemId: number }) =>
      agentContextsApi.removeItem(id, itemId),
    onSuccess: () => qc.invalidateQueries({ queryKey: agentContextsKeys.all }),
  });
}

export function useAddContextTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, tenantId }: { id: number; tenantId: number }) =>
      agentContextsApi.addTenant(id, tenantId),
    onSuccess: () => qc.invalidateQueries({ queryKey: agentContextsKeys.all }),
  });
}

export function useRemoveContextTenant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, tenantId }: { id: number; tenantId: number }) =>
      agentContextsApi.removeTenant(id, tenantId),
    onSuccess: () => qc.invalidateQueries({ queryKey: agentContextsKeys.all }),
  });
}
