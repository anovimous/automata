import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { findingsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  FindingCreationRequest,
  FindingFilter,
  FindingPatchRequest,
  FindingReviewRequest,
} from "@/types/domain";

export const findingsKeys = {
  all: ["findings"] as const,
  list: (filter: FindingFilter | undefined, pageable: Pageable | undefined) =>
    ["findings", "list", { filter, pageable }] as const,
  detail: (id: number) => ["findings", "detail", id] as const,
};

export function useFindings(filter?: FindingFilter, pageable?: Pageable) {
  return useQuery({
    queryKey: findingsKeys.list(filter, pageable),
    queryFn: () => findingsApi.list(filter, pageable),
  });
}

export function useFinding(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: findingsKeys.detail(id ?? -1),
    queryFn: () => findingsApi.get(id as number),
  });
}

export function useCreateFinding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: FindingCreationRequest) => findingsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: findingsKeys.all });
      recordActivity({ kind: "create-finding" });
    },
  });
}

export function usePatchFinding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: FindingPatchRequest }) =>
      findingsApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: findingsKeys.all });
      recordActivity({ kind: "patch-finding" });
    },
  });
}

export function useReviewFinding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: FindingReviewRequest }) =>
      findingsApi.review(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: findingsKeys.all });
      recordActivity({ kind: "review-finding" });
    },
  });
}

export function useDeleteFinding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => findingsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: findingsKeys.all });
      recordActivity({ kind: "delete-finding" });
    },
  });
}
