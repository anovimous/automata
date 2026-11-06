import { apiFetch, apiUrl } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type {
  Comparator,
  ComparatorCreationRequest,
  ComparatorPatchRequest,
  ComparatorSchema as ComparatorSchemaType,
  ModifierCreationRequest,
  ModifierDetailed,
  ModifierPatchRequest,
  ModifierSummary,
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
    schema?: ComparatorSchemaType;
    pageable?: Pageable;
  }) =>
    apiFetch<PageHolder<Comparator>>(COMP, {
      query: {
        query: params?.query,
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
    apiFetch<PageHolder<ModifierSummary>>(MOD, {
      query: {
        target: params?.target,
        schema: params?.schema,
        ...pageableToParams(params?.pageable),
      },
    }),
  get: (id: number) => apiFetch<ModifierDetailed>(`${MOD}/${id}`),
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
  /**
   * Multipart upload: the file is the part `file`, the other properties the JSON part
   * `metadata`. The core reads the file once (sha256, size, lines) and answers 409 when
   * it is already a wordlist, 400 for an empty or binary file, 413 above 512 MB.
   */
  create: (file: File, metadata?: WordlistCreationRequest) => {
    const form = new FormData();
    form.append("file", file);
    if (metadata) {
      form.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
    }
    return apiFetch<void>(WL, { method: "POST", formData: form, responseType: "void" });
  },
  /** The file as an attachment (`<a href download>`). */
  contentUrl: (id: number) => apiUrl(`${WL}/${id}/content`),
  patch: (id: number, req: WordlistPatchRequest) =>
    apiFetch<void>(`${WL}/${id}`, { method: "PATCH", body: req, responseType: "void" }),
  delete: (id: number) =>
    apiFetch<void>(`${WL}/${id}`, { method: "DELETE", responseType: "void" }),
};
