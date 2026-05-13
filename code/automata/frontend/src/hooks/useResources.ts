import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  comparatorsApi,
  modifiersApi,
  routinesApi,
  vulnerabilitiesApi,
  wordlistsApi,
} from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  ComparatorCreationRequest,
  ComparatorPatchRequest,
  ComparatorSchema as ComparatorSchemaType,
  ModifierCreationRequest,
  ModifierPatchRequest,
  ModifierTarget,
  RoutineCreationRequest,
  RoutinePatchRequest,
  VulnerabilityCreationRequest,
  VulnerabilityPatchRequest,
  WordlistCreationRequest,
  WordlistPatchRequest,
} from "@/types/domain";

// ===== Routines =====
export const routinesKeys = {
  all: ["routines"] as const,
  list: (query?: string, vulnId?: number, pageable?: Pageable) =>
    ["routines", "list", { query, vulnId, pageable }] as const,
  detail: (id: number) => ["routines", "detail", id] as const,
};

export function useRoutines(query?: string, vulnId?: number, pageable?: Pageable) {
  return useQuery({
    queryKey: routinesKeys.list(query, vulnId, pageable),
    queryFn: () => routinesApi.list(query, vulnId, pageable),
  });
}

export function useRoutine(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: routinesKeys.detail(id ?? -1),
    queryFn: () => routinesApi.get(id as number),
  });
}

export function useCreateRoutine() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: RoutineCreationRequest) => routinesApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: routinesKeys.all });
      recordActivity({ kind: "create-routine" });
    },
  });
}

export function usePatchRoutine() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: RoutinePatchRequest }) =>
      routinesApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: routinesKeys.all });
      recordActivity({ kind: "patch-routine" });
    },
  });
}

export function useDeleteRoutine() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => routinesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: routinesKeys.all });
      recordActivity({ kind: "delete-routine" });
    },
  });
}

// ===== Vulnerabilities =====
export const vulnsKeys = {
  all: ["vulnerabilities"] as const,
  list: (pageable?: Pageable) => ["vulnerabilities", "list", { pageable }] as const,
  detail: (id: number) => ["vulnerabilities", "detail", id] as const,
};

export function useVulnerabilities(pageable?: Pageable) {
  return useQuery({
    queryKey: vulnsKeys.list(pageable),
    queryFn: () => vulnerabilitiesApi.list(pageable),
  });
}

export function useVulnerability(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: vulnsKeys.detail(id ?? -1),
    queryFn: () => vulnerabilitiesApi.get(id as number),
  });
}

export function useCreateVulnerability() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: VulnerabilityCreationRequest) => vulnerabilitiesApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: vulnsKeys.all });
      recordActivity({ kind: "create-vulnerability" });
    },
  });
}

export function usePatchVulnerability() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: VulnerabilityPatchRequest }) =>
      vulnerabilitiesApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: vulnsKeys.all });
      recordActivity({ kind: "patch-vulnerability" });
    },
  });
}

export function useDeleteVulnerability() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => vulnerabilitiesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: vulnsKeys.all });
      recordActivity({ kind: "delete-vulnerability" });
    },
  });
}

// ===== Comparators =====
export const comparatorsKeys = {
  all: ["comparators"] as const,
  list: (p: {
    query?: string;
    programId?: number;
    hostId?: number;
    schema?: ComparatorSchemaType;
    pageable?: Pageable;
  }) => ["comparators", "list", p] as const,
  detail: (id: number) => ["comparators", "detail", id] as const,
};

export function useComparators(p: {
  query?: string;
  programId?: number;
  hostId?: number;
  schema?: ComparatorSchemaType;
  pageable?: Pageable;
} = {}) {
  return useQuery({
    queryKey: comparatorsKeys.list(p),
    queryFn: () => comparatorsApi.list(p),
  });
}

export function useComparator(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: comparatorsKeys.detail(id ?? -1),
    queryFn: () => comparatorsApi.get(id as number),
  });
}

export function useCreateComparator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ComparatorCreationRequest) => comparatorsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: comparatorsKeys.all });
      recordActivity({ kind: "create-comparator" });
    },
  });
}

export function usePatchComparator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: ComparatorPatchRequest }) =>
      comparatorsApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: comparatorsKeys.all });
      recordActivity({ kind: "patch-comparator" });
    },
  });
}

export function useDeleteComparator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => comparatorsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: comparatorsKeys.all });
      recordActivity({ kind: "delete-comparator" });
    },
  });
}

// ===== Modifiers =====
export const modifiersKeys = {
  all: ["modifiers"] as const,
  list: (p: { target?: ModifierTarget; schema?: ComparatorSchemaType; pageable?: Pageable }) =>
    ["modifiers", "list", p] as const,
  detail: (id: number) => ["modifiers", "detail", id] as const,
};

export function useModifiers(p: {
  target?: ModifierTarget;
  schema?: ComparatorSchemaType;
  pageable?: Pageable;
} = {}) {
  return useQuery({
    queryKey: modifiersKeys.list(p),
    queryFn: () => modifiersApi.list(p),
  });
}

export function useModifier(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: modifiersKeys.detail(id ?? -1),
    queryFn: () => modifiersApi.get(id as number),
  });
}

export function useCreateModifier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ModifierCreationRequest) => modifiersApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: modifiersKeys.all });
      recordActivity({ kind: "create-modifier" });
    },
  });
}

export function usePatchModifier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: ModifierPatchRequest }) =>
      modifiersApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: modifiersKeys.all });
      recordActivity({ kind: "patch-modifier" });
    },
  });
}

export function useDeleteModifier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => modifiersApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: modifiersKeys.all });
      recordActivity({ kind: "delete-modifier" });
    },
  });
}

// ===== Wordlists =====
export const wordlistsKeys = {
  all: ["wordlists"] as const,
  list: (p: { query?: string; vulnId?: number; pageable?: Pageable }) =>
    ["wordlists", "list", p] as const,
  detail: (id: number) => ["wordlists", "detail", id] as const,
};

export function useWordlists(
  p: { query?: string; vulnId?: number; pageable?: Pageable } = {},
) {
  return useQuery({
    queryKey: wordlistsKeys.list(p),
    queryFn: () => wordlistsApi.list(p),
  });
}

export function useWordlist(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: wordlistsKeys.detail(id ?? -1),
    queryFn: () => wordlistsApi.get(id as number),
  });
}

export function useCreateWordlist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: WordlistCreationRequest) => wordlistsApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: wordlistsKeys.all });
      recordActivity({ kind: "create-wordlist" });
    },
  });
}

export function usePatchWordlist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: WordlistPatchRequest }) =>
      wordlistsApi.patch(id, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: wordlistsKeys.all });
      recordActivity({ kind: "patch-wordlist" });
    },
  });
}

export function useDeleteWordlist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => wordlistsApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: wordlistsKeys.all });
      recordActivity({ kind: "delete-wordlist" });
    },
  });
}
