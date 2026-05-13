import { useQuery } from "@tanstack/react-query";
import { responsesApi } from "@/api";

export const responsesKeys = {
  detail: (id: number) => ["responses", "detail", id] as const,
  raw: (id: number) => ["responses", "raw", id] as const,
};

export function useResponse(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: responsesKeys.detail(id ?? -1),
    queryFn: () => responsesApi.get(id as number),
  });
}

export function useRawResponse(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: responsesKeys.raw(id ?? -1),
    queryFn: () => responsesApi.getRaw(id as number),
  });
}
