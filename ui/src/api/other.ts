import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  Comparator,
  ComparatorCreationRequest,
  ComparatorPatchRequest,
  ComparatorSchema as ComparatorSchemaType,
  Modifier,
  ModifierCreationRequest,
  ModifierPatchRequest,
  ModifierTarget,
  WordlistCreationRequest,
  WordlistDetailed,
  WordlistPatchRequest,
  WordlistSummary,
} from "@/types/domain";

const COMP = "/api/comparators";
const MOD = "/api/modifiers";
const WL = "/api/wordlists";

export const comparatorsApi = {
  list: (params?: {
    query?: string;
    programId?: number;
    hostId?: number;
    schema?: ComparatorSchemaType;
    pageable?: Pageable;
  }) =>
    apiFetch<PageHolder<Comparator>>(COMP, {
      query: {
        query: params?.query,
        programId: params?.programId,
        hostId: params?.hostId,
        schema: params?.schema,
        ...pageableToParams(params?.pageable),
      },
    }),
  get: (id: number) => apiFetch<Comparator>(`${COMP}/${id}`),
  create: (req: ComparatorCreationRequest) =>
    apiFetch<void>(COMP, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: ComparatorPatchRequest) =>
    apiFetch<void>(`${COMP}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${COMP}/${id}`, { method: "DELETE", responseType: "void" }),
};

export const modifiersApi = {
  list: (params?: {
    target?: ModifierTarget;
    schema?: ComparatorSchemaType;
    pageable?: Pageable;
  }) =>
    apiFetch<PageHolder<Modifier>>(MOD, {
      query: {
        target: params?.target,
        schema: params?.schema,
        ...pageableToParams(params?.pageable),
      },
    }),
  get: (id: number) => apiFetch<Modifier>(`${MOD}/${id}`),
  create: (req: ModifierCreationRequest) =>
    apiFetch<void>(MOD, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: ModifierPatchRequest) =>
    apiFetch<void>(`${MOD}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${MOD}/${id}`, { method: "DELETE", responseType: "void" }),
};

export const wordlistsApi = {
  list: (params?: { query?: string; vulnId?: number; pageable?: Pageable }) =>
    apiFetch<PageHolder<WordlistSummary>>(WL, {
      query: {
        query: params?.query,
        vulnId: params?.vulnId,
        ...pageableToParams(params?.pageable),
      },
    }),
  get: (id: number) => apiFetch<WordlistDetailed>(`${WL}/${id}`),
  create: (req: WordlistCreationRequest) =>
    apiFetch<void>(WL, { method: "POST", body: req, responseType: "void" }),
  patch: (id: number, req: WordlistPatchRequest) =>
    apiFetch<void>(`${WL}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${WL}/${id}`, { method: "DELETE", responseType: "void" }),
};
