import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { hostsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type { HostCreationRequest, PatchHostRequest } from "@/types/domain";

export const hostsKeys = {
  all: ["hosts"] as const,
  list: (programId: number, query?: string, pageable?: Pageable) =>
    ["hosts", "list", { programId, query, pageable }] as const,
  detail: (id: number) => ["hosts", "detail", id] as const,
};

export function useHosts(programId: number | null | undefined, query?: string, pageable?: Pageable) {
  return useQuery({
    enabled: programId !== null && programId !== undefined,
    queryKey: hostsKeys.list(programId ?? -1, query, pageable),
    queryFn: () => hostsApi.list(programId as number, query, pageable),
  });
}

export function useHost(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: hostsKeys.detail(id ?? -1),
    queryFn: () => hostsApi.get(id as number),
  });
}

export function useCreateHost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: HostCreationRequest) => hostsApi.create(req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: hostsKeys.all });
      recordActivity({
        kind: "create-host",
        programId: vars.programId,
        hostName: vars.host,
      });
    },
  });
}

export function usePatchHost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      req,
      programId,
    }: {
      id: number;
      req: PatchHostRequest;
      programId?: number;
    }) => hostsApi.patch(id, req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: hostsKeys.all });
      recordActivity({
        kind: "patch-host",
        hostId: vars.id,
        programId: vars.programId,
        hostName: vars.req.host,
      });
    },
  });
}

export function useDeleteHost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; programId?: number }) => hostsApi.delete(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: hostsKeys.all });
      recordActivity({
        kind: "delete-host",
        hostId: vars.id,
        programId: vars.programId,
      });
    },
  });
}
