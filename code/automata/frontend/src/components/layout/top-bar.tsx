import * as React from "react";
import { Link, useMatches } from "@tanstack/react-router";
import { ChevronRight, Command } from "lucide-react";
import { ThemeToggle } from "./theme-toggle";
import { Button } from "@/components/ui/button";

export interface Crumb {
  label: string;
  to?: string;
}

export interface TopBarContext {
  setCrumbs: (crumbs: Crumb[]) => void;
  setActions: (actions: React.ReactNode | null) => void;
  openCommand: () => void;
}

const Ctx = React.createContext<TopBarContext | null>(null);

export function useTopBar() {
  const c = React.useContext(Ctx);
  if (!c) throw new Error("useTopBar must be used inside <TopBarProvider>");
  return c;
}

export function TopBarProvider({
  children,
  openCommand,
}: {
  children: React.ReactNode;
  openCommand: () => void;
}) {
  const [crumbs, setCrumbs] = React.useState<Crumb[]>([]);
  const [actions, setActions] = React.useState<React.ReactNode | null>(null);

  const ctx = React.useMemo<TopBarContext>(
    () => ({ setCrumbs, setActions, openCommand }),
    [openCommand],
  );

  return (
    <Ctx.Provider value={ctx}>
      <TopBar crumbs={crumbs} actions={actions} openCommand={openCommand} />
      {children}
    </Ctx.Provider>
  );
}

function TopBar({
  crumbs,
  actions,
  openCommand,
}: {
  crumbs: Crumb[];
  actions: React.ReactNode | null;
  openCommand: () => void;
}) {
  const isMac =
    typeof navigator !== "undefined" &&
    navigator.platform.toUpperCase().indexOf("MAC") >= 0;

  return (
    <header className="h-12 shrink-0 border-b border-border flex items-center justify-between px-4 bg-background/80 backdrop-blur sticky top-0 z-30">
      <div className="flex items-center gap-1 text-xs min-w-0">
        {crumbs.length === 0 ? (
          <span className="text-muted-foreground">&nbsp;</span>
        ) : (
          crumbs.map((c, i) => (
            <React.Fragment key={i}>
              {c.to ? (
                <Link
                  to={c.to}
                  className="text-muted-foreground hover:text-foreground transition-colors truncate"
                >
                  {c.label}
                </Link>
              ) : (
                <span className="text-foreground truncate">{c.label}</span>
              )}
              {i < crumbs.length - 1 && (
                <ChevronRight className="h-3 w-3 shrink-0 text-muted-foreground/60" />
              )}
            </React.Fragment>
          ))
        )}
      </div>
      <div className="flex items-center gap-2">
        {actions}
        <Button
          variant="outline"
          size="sm"
          className="text-xs gap-1.5"
          onClick={openCommand}
          aria-label="Open command palette"
        >
          <Command className="h-3 w-3" />
          <span className="hidden sm:inline">{isMac ? "⌘K" : "Ctrl K"}</span>
        </Button>
        <ThemeToggle />
      </div>
    </header>
  );
}

/**
 * Sub-hook that lets a route declaratively set crumbs/actions on mount.
 */
export function useSetTopBar(
  crumbs: Crumb[],
  actions?: React.ReactNode,
  deps: React.DependencyList = [],
) {
  const { setCrumbs, setActions } = useTopBar();
  React.useEffect(() => {
    setCrumbs(crumbs);
    setActions(actions ?? null);
    return () => {
      setCrumbs([]);
      setActions(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}

// Re-export to silence "useMatches not used" warning when tree-shaken
export { useMatches as _useMatches };
