import { useQuery } from "@tanstack/react-query";
import { responsesApi } from "@/api";
import type { Pageable } from "@/types/pagination";
import type { ResponseFilter } from "@/types/domain";

export const responsesKeys = {
  all: ["responses"] as const,
  list: (filter: ResponseFilter | undefined, pageable: Pageable | undefined) =>
    ["responses", "list", { filter, pageable }] as const,
  metadata: (id: number) => ["responses", "metadata", id] as const,
  raw: (id: number) => ["responses", "raw", id] as const,
};

export function useResponses(filter?: ResponseFilter, pageable?: Pageable) {
  return useQuery({
    queryKey: responsesKeys.list(filter, pageable),
    queryFn: () => responsesApi.list(filter, pageable),
  });
}

export function useResponseMetadata(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: responsesKeys.metadata(id ?? -1),
    queryFn: () => responsesApi.metadata(id as number),
  });
}

export function useRawResponse(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: responsesKeys.raw(id ?? -1),
    queryFn: () => responsesApi.raw(id as number),
  });
}
