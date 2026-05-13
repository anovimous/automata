import * as React from "react";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface PaginationProps {
  page: number; // zero-based
  size: number;
  total?: number;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
}

export function Pagination({
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
  pageSizeOptions = [20, 50, 100, 200],
}: PaginationProps) {
  const totalPages = total !== undefined ? Math.max(1, Math.ceil(total / size)) : undefined;
  const isLastPage = totalPages !== undefined ? page >= totalPages - 1 : false;

  return (
    <div className="flex items-center justify-between gap-3 px-3 py-2 border-t border-border text-xs text-muted-foreground">
      <div className="flex items-center gap-2">
        <span>Rows per page</span>
        <Select
          value={String(size)}
          onValueChange={(v) => onSizeChange?.(Number(v))}
          disabled={!onSizeChange}
        >
          <SelectTrigger className="h-7 w-[5rem] text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {pageSizeOptions.map((s) => (
              <SelectItem key={s} value={String(s)}>
                {s}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center gap-3">
        <div className="tabular-nums">
          {total !== undefined ? (
            <>
              {page * size + 1}–{Math.min((page + 1) * size, total)} of {total}
            </>
          ) : (
            <>Page {page + 1}</>
          )}
        </div>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon-sm"
            disabled={page === 0}
            onClick={() => onPageChange(0)}
            aria-label="First page"
          >
            <ChevronsLeft className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            aria-label="Previous page"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            disabled={isLastPage}
            onClick={() => onPageChange(page + 1)}
            aria-label="Next page"
          >
            <ChevronRight className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            disabled={isLastPage || totalPages === undefined}
            onClick={() => totalPages !== undefined && onPageChange(totalPages - 1)}
            aria-label="Last page"
          >
            <ChevronsRight className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
    </div>
  );
}
