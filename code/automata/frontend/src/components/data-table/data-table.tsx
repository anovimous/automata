import * as React from "react";
import { cn } from "@/lib/cn";
import { Checkbox } from "@/components/ui/primitives";
import { ChevronDown, ChevronUp } from "lucide-react";

// =============================================================================
// Types
// =============================================================================

export interface DataTableColumn<T> {
  key: string;
  header: React.ReactNode;
  /** Cell renderer. Use `row` to render. */
  cell: (row: T, index: number) => React.ReactNode;
  width?: string; // e.g. "w-24"
  align?: "left" | "right" | "center";
  /** When set, clicking the header toggles the sort field with this value. */
  sortKey?: string;
}

export interface DataTableProps<T> {
  data: T[];
  columns: DataTableColumn<T>[];
  rowKey: (row: T) => string | number;
  emptyMessage?: React.ReactNode;
  isLoading?: boolean;
  // Selection (optional)
  selectable?: boolean;
  selected?: Set<string | number>;
  onSelectionChange?: (next: Set<string | number>) => void;
  // Row click
  onRowClick?: (row: T) => void;
  // Sort indicator
  sortField?: string;
  sortDir?: "asc" | "desc";
  onSortChange?: (field: string, dir: "asc" | "desc") => void;
}

export function DataTable<T>({
  data,
  columns,
  rowKey,
  emptyMessage,
  isLoading,
  selectable,
  selected,
  onSelectionChange,
  onRowClick,
  sortField,
  sortDir,
  onSortChange,
}: DataTableProps<T>) {
  const [focusedIdx, setFocusedIdx] = React.useState(0);
  const tableRef = React.useRef<HTMLTableElement | null>(null);

  function toggleRow(k: string | number) {
    if (!onSelectionChange) return;
    const next = new Set(selected ?? new Set<string | number>());
    if (next.has(k)) next.delete(k);
    else next.add(k);
    onSelectionChange(next);
  }

  function toggleAll() {
    if (!onSelectionChange) return;
    const cur = selected ?? new Set<string | number>();
    if (cur.size === data.length && data.length > 0) {
      onSelectionChange(new Set());
    } else {
      onSelectionChange(new Set(data.map(rowKey)));
    }
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
    switch (e.key) {
      case "j":
      case "ArrowDown":
        e.preventDefault();
        setFocusedIdx((i) => Math.min(data.length - 1, i + 1));
        break;
      case "k":
      case "ArrowUp":
        e.preventDefault();
        setFocusedIdx((i) => Math.max(0, i - 1));
        break;
      case "x":
        e.preventDefault();
        if (selectable && data[focusedIdx]) toggleRow(rowKey(data[focusedIdx]));
        break;
      case "Enter":
        e.preventDefault();
        if (data[focusedIdx] && onRowClick) onRowClick(data[focusedIdx]);
        break;
    }
  }

  const allSelected =
    selectable && data.length > 0 && (selected?.size ?? 0) === data.length;
  const someSelected =
    selectable && (selected?.size ?? 0) > 0 && (selected?.size ?? 0) < data.length;

  return (
    <div
      className="hairline-y overflow-x-auto outline-none"
      onKeyDown={handleKeyDown}
      tabIndex={0}
    >
      <table ref={tableRef} className="min-w-full text-xs">
        <thead className="bg-muted/40 sticky top-0 z-10">
          <tr className="border-b border-border">
            {selectable && (
              <th className="w-8 px-3 py-2 text-left">
                <Checkbox
                  checked={allSelected ? true : someSelected ? "indeterminate" : false}
                  onCheckedChange={toggleAll}
                  aria-label="Select all"
                />
              </th>
            )}
            {columns.map((col) => {
              const isSorted = sortField === col.sortKey;
              return (
                <th
                  key={col.key}
                  className={cn(
                    "px-3 py-2 text-[10px] uppercase tracking-wider text-muted-foreground font-medium",
                    col.align === "right" && "text-right",
                    col.align === "center" && "text-center",
                    col.width,
                    col.sortKey && "cursor-pointer select-none hover:text-foreground",
                  )}
                  onClick={() => {
                    if (!col.sortKey || !onSortChange) return;
                    const nextDir = isSorted && sortDir === "desc" ? "asc" : "desc";
                    onSortChange(col.sortKey, nextDir);
                  }}
                >
                  <span className="inline-flex items-center gap-1">
                    {col.header}
                    {isSorted && (
                      sortDir === "asc" ? (
                        <ChevronUp className="h-3 w-3" />
                      ) : (
                        <ChevronDown className="h-3 w-3" />
                      )
                    )}
                  </span>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody className="row-dense">
          {isLoading && (
            <tr>
              <td
                colSpan={columns.length + (selectable ? 1 : 0)}
                className="px-3 py-8 text-center text-muted-foreground"
              >
                Loading…
              </td>
            </tr>
          )}
          {!isLoading && data.length === 0 && (
            <tr>
              <td
                colSpan={columns.length + (selectable ? 1 : 0)}
                className="px-3 py-12 text-center text-muted-foreground"
              >
                {emptyMessage ?? "No results"}
              </td>
            </tr>
          )}
          {!isLoading &&
            data.map((row, i) => {
              const k = rowKey(row);
              const isSelected = selected?.has(k) ?? false;
              const isFocused = i === focusedIdx;
              return (
                <tr
                  key={k}
                  className={cn(
                    "border-b border-border last:border-b-0 hover:bg-accent/40 transition-colors",
                    isSelected && "bg-foreground/[0.04]",
                    isFocused && "bg-accent/40",
                    onRowClick && "cursor-pointer",
                  )}
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest("[data-stop-row-click]")) return;
                    onRowClick?.(row);
                  }}
                >
                  {selectable && (
                    <td className="px-3 py-1.5" data-stop-row-click>
                      <Checkbox
                        checked={isSelected}
                        onCheckedChange={() => toggleRow(k)}
                        aria-label="Select row"
                      />
                    </td>
                  )}
                  {columns.map((col) => (
                    <td
                      key={col.key}
                      className={cn(
                        "px-3 py-1.5 align-middle",
                        col.align === "right" && "text-right",
                        col.align === "center" && "text-center",
                      )}
                    >
                      {col.cell(row, i)}
                    </td>
                  ))}
                </tr>
              );
            })}
        </tbody>
      </table>
    </div>
  );
}
