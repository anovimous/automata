import { z } from "zod";

// Spring's Pageable, as accepted on the wire (flat query params).
export interface Pageable {
  page?: number;
  size?: number;
  sort?: string[]; // e.g. ["creationDate,desc"]
}

export function pageableToParams(p: Pageable | undefined): Record<string, string | string[]> {
  if (!p) return {};
  const out: Record<string, string | string[]> = {};
  if (p.page !== undefined) out.page = String(p.page);
  if (p.size !== undefined) out.size = String(p.size);
  if (p.sort && p.sort.length > 0) out.sort = p.sort;
  return out;
}

// PageHolderResponse<T> shape used by the backend.
export const pageHolderSchema = <T extends z.ZodTypeAny>(item: T) =>
  z.object({
    content: z.array(item),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().nonnegative().optional(),
    totalElements: z.number().int().nonnegative().optional(),
    totalPages: z.number().int().nonnegative().optional(),
    first: z.boolean().optional(),
    last: z.boolean().optional(),
    numberOfElements: z.number().int().nonnegative().optional(),
    empty: z.boolean().optional(),
  });

export type PageHolder<T> = {
  content: T[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
  numberOfElements?: number;
  empty?: boolean;
};
