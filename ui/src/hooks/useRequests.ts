import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { requestsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  EqualizationCreationRequest,
  EqualitySetPatchRequest,
  RawRequestAddition,
  RawResponseAddition,
  RequestFilter,
} from "@/types/domain";

export const requestsKeys = {
  all: ["requests"] as const,
  list: (filter: RequestFilter | undefined, pageable: Pageable | undefined) =>
    ["requests", "list", { filter, pageable }] as const,
  listEqual: (setId: number, pageable: Pageable | undefined) =>
    ["requests", "list-equal", setId, pageable] as const,
  metadata: (id: number) => ["requests", "metadata", id] as const,
  raw: (id: number) => ["requests", "raw", id] as const,
  equalizations: (hostId: number) => ["requests", "equalizations", hostId] as const,
  equalitySet: (id: number) => ["requests", "equality-set", id] as const,
};

export function useRequests(filter?: RequestFilter, pageable?: Pageable) {
  return useQuery({
    queryKey: requestsKeys.list(filter, pageable),
    queryFn: () => requestsApi.list(filter, pageable),
  });
}

export function useEqualRequests(equalitySetId: number | null | undefined, pageable?: Pageable) {
  return useQuery({
    enabled: equalitySetId !== null && equalitySetId !== undefined,
    queryKey: requestsKeys.listEqual(equalitySetId ?? -1, pageable),
    queryFn: () => requestsApi.listEqual(equalitySetId as number, pageable),
  });
}

export function useRequestMetadata(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: requestsKeys.metadata(id ?? -1),
    queryFn: () => requestsApi.metadata(id as number),
  });
}

export function useRawRequest(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: requestsKeys.raw(id ?? -1),
    queryFn: () => requestsApi.raw(id as number),
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
      qc.invalidateQueries({ queryKey: ["responses"] });
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

// ----- Equalizations -----
export function useEqualizations(hostId: number | null | undefined) {
  return useQuery({
    enabled: hostId !== null && hostId !== undefined,
    queryKey: requestsKeys.equalizations(hostId ?? -1),
    queryFn: () => requestsApi.listEqualizations(hostId as number),
  });
}

export function useCreateEqualization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: EqualizationCreationRequest & { programId?: number; hostName?: string }) =>
      requestsApi.createEqualization({
        hostId: req.hostId,
        comparatorId: req.comparatorId,
        autoEqualize: req.autoEqualize,
      }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.equalizations(vars.hostId) });
      recordActivity({
        kind: "create-equalization",
        hostId: vars.hostId,
        programId: vars.programId,
        hostName: vars.hostName,
      });
    },
  });
}

export function useSetAutoEqualize() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      equalizationId,
      autoEqualize,
    }: {
      equalizationId: number;
      autoEqualize: boolean;
      hostId: number;
    }) => requestsApi.setAutoEqualize(equalizationId, autoEqualize),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.equalizations(vars.hostId) });
    },
  });
}

export function useRunEqualization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      equalizationId,
    }: {
      equalizationId: number;
      hostId: number;
      programId?: number;
      hostName?: string;
    }) => requestsApi.runEqualization(equalizationId),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.equalizations(vars.hostId) });
      qc.invalidateQueries({ queryKey: ["requests", "equality-set"] });
      qc.invalidateQueries({ queryKey: ["requests", "list-equal"] });
      recordActivity({
        kind: "run-equalization",
        hostId: vars.hostId,
        programId: vars.programId,
        hostName: vars.hostName,
      });
    },
  });
}

// ----- Equality sets -----
export function useEqualitySet(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined && Number.isFinite(id),
    queryKey: requestsKeys.equalitySet(id ?? -1),
    queryFn: () => requestsApi.getEqualitySet(id as number),
  });
}

export function usePatchEqualitySet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: EqualitySetPatchRequest }) =>
      requestsApi.patchEqualitySet(id, req),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: requestsKeys.equalitySet(vars.id) });
      qc.invalidateQueries({ queryKey: ["agent-contexts"] });
      recordActivity({ kind: "patch-equality-set" });
    },
  });
}
