import { apiUrl } from "@/lib/fetcher";

export const exportApi = {
  /** Distinct body property names, one per line (text/plain attachment). */
  bodyPropertiesUrl: (scope: { hostId?: number; programId?: number }) =>
    apiUrl("/api/export/body-properties", scope),
};
