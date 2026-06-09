import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { requestsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  RawRequestAddition,
  RawResponseAddition,
  RequestFilter,
  RequestsEqualization,
} from "@/types/domain";

export const requestsKeys = {
  all: ["requests"] as const,
  list: (filter: RequestFilter | undefined, pageable: Pageable | undefined) =>
    ["requests", "list", { filter, pageable }] as const,
  detail: (id: number) => ["requests", "detail", id] as const,
};

export function useRequests(filter?: RequestFilter, pageable?: Pageable) {
  return useQuery({
    queryKey: requestsKeys.list(filter, pageable),
    queryFn: () => requestsApi.list(filter, pageable),
  });
}

export function useRequest(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: requestsKeys.detail(id ?? -1),
    queryFn: () => requestsApi.get(id as number),
  });
}

export function useAddRawRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: RawRequestAddition & { programId?: number; hostName?: string }) =>
      requestsApi.addRaw({
        requestBase64: req.requestBase64,
        source: req.source,
        hostId: req.hostId,
        tenantId: req.tenantId,
        responseBase64: req.responseBase64,
      }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.all });
      recordActivity({
        kind: "add-request",
        hostId: vars.hostId,
        programId: vars.programId,
        hostName: vars.hostName,
      });
    },
  });
}

export function useAssociateResponse() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      requestId,
      body,
    }: {
      requestId: number;
      body: RawResponseAddition;
      hostId?: number;
      programId?: number;
    }) => requestsApi.associateResponse(requestId, body),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.all });
      recordActivity({
        kind: "associate-response",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}

export function useDeleteRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; hostId?: number; programId?: number }) =>
      requestsApi.delete(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.all });
      recordActivity({
        kind: "delete-request",
        hostId: vars.hostId,
        programId: vars.programId,
      });
    },
  });
}

export function useEqualizeRequests() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: RequestsEqualization & { programId?: number; hostName?: string }) =>
      requestsApi.equalize({
        hostId: req.hostId,
        requestsIds: req.requestsIds,
        comparatorId: req.comparatorId,
      }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.all });
      recordActivity({
        kind: "equalize",
        hostId: vars.hostId,
        programId: vars.programId,
        hostName: vars.hostName,
        meta: { requestCount: vars.requestsIds.length },
      });
    },
  });
}
