import { apiFetch } from "@/lib/fetcher";
import type { Pageable } from "@/types/pagination";
import { pageableToParams, type PageHolder } from "@/types/pagination";
import type { RawResponseDto, ResponseDto, ResponseFilter } from "@/types/domain";

const BASE = "/api/responses";

function filterToParams(f: ResponseFilter | undefined): Record<string, string | number | undefined> {
  if (!f) return {};
  return {
    statusCode: f.statusCode,
    contentType: f.contentType,
    contentLength: f.contentLength,
    hostId: f.hostId,
  };
}

export const responsesApi = {
  list: (filter?: ResponseFilter, pageable?: Pageable) =>
    apiFetch<PageHolder<ResponseDto>>(BASE, {
      query: { ...filterToParams(filter), ...pageableToParams(pageable) },
    }),
  get: (id: number) => apiFetch<ResponseDto>(`${BASE}/${id}`),
  getRaw: (id: number) => apiFetch<RawResponseDto>(`${BASE}/${id}/raw`),
  delete: (id: number) =>
    apiFetch<void>(`${BASE}/${id}`, { method: "DELETE", responseType: "void" }),
};
