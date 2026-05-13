import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { programsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  PatchProgramRequest,
  ProgramCreationRequest,
} from "@/types/domain";

export const programsKeys = {
  all: ["programs"] as const,
  list: (query?: string, pageable?: Pageable) =>
    ["programs", "list", { query, pageable }] as const,
  detail: (id: number) => ["programs", "detail", id] as const,
};

export function usePrograms(query?: string, pageable?: Pageable) {
  return useQuery({
    queryKey: programsKeys.list(query, pageable),
    queryFn: () => programsApi.list(query, pageable),
  });
}

export function useProgram(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: programsKeys.detail(id ?? -1),
    queryFn: () => programsApi.get(id as number),
  });
}

export function useCreateProgram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ProgramCreationRequest) => programsApi.create(req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: programsKeys.all });
      recordActivity({
        kind: "create-program",
        programName: vars.name,
      });
    },
  });
}

export function usePatchProgram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: PatchProgramRequest }) =>
      programsApi.patch(id, req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: programsKeys.all });
      recordActivity({
        kind: "patch-program",
        programId: vars.id,
        programName: vars.req.name,
      });
    },
  });
}

export function useDeleteProgram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => programsApi.delete(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: programsKeys.all });
      recordActivity({ kind: "delete-program", programId: id });
    },
  });
}
