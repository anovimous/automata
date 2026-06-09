import * as React from "react";
import { Link, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  Layers,
  Cpu,
  ListTree,
  Bug,
  GitCompare,
  Wrench,
  FileText,
  Settings,
  Triangle,
} from "lucide-react";
import { cn } from "@/lib/cn";

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  // Match the active state when pathname starts with this.
  // Useful for nested routes (e.g. /programs/123).
  matchPrefix?: string;
}

const NAV: NavItem[][] = [
  [{ to: "/", label: "Dashboard", icon: LayoutDashboard, matchPrefix: "/" }],
  [
    { to: "/programs", label: "Programs", icon: Layers, matchPrefix: "/programs" },
    { to: "/jobs", label: "Jobs", icon: Cpu, matchPrefix: "/jobs" },
  ],
  [
    { to: "/routines", label: "Routines", icon: ListTree, matchPrefix: "/routines" },
    { to: "/vulnerabilities", label: "Vulnerabilities", icon: Bug, matchPrefix: "/vulnerabilities" },
  ],
  [
    { to: "/comparators", label: "Comparators", icon: GitCompare, matchPrefix: "/comparators" },
    { to: "/modifiers", label: "Modifiers", icon: Wrench, matchPrefix: "/modifiers" },
    { to: "/wordlists", label: "Wordlists", icon: FileText, matchPrefix: "/wordlists" },
  ],
  [{ to: "/settings", label: "Settings", icon: Settings, matchPrefix: "/settings" }],
];

export function Sidebar() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  return (
    <aside className="w-52 shrink-0 border-r border-border bg-card flex flex-col">
      <div className="h-12 px-4 flex items-center gap-2 border-b border-border">
        <Triangle className="h-4 w-4 fill-foreground stroke-foreground" />
        <span className="text-sm font-semibold tracking-tight">Automata</span>
      </div>

      <nav className="flex-1 overflow-y-auto py-2">
        {NAV.map((group, gi) => (
          <div key={gi} className="px-2 py-1.5">
            {group.map((item) => {
              const active =
                item.to === "/"
                  ? pathname === "/"
                  : pathname.startsWith(item.matchPrefix || item.to);
              const Icon = item.icon;
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={cn(
                    "group flex items-center gap-2 rounded-md px-2 py-1.5 text-[13px] transition-colors",
                    active
                      ? "bg-accent text-foreground"
                      : "text-muted-foreground hover:bg-accent/40 hover:text-foreground",
                  )}
                >
                  <Icon className="h-3.5 w-3.5 shrink-0" />
                  <span className="truncate">{item.label}</span>
                </Link>
              );
            })}
            {gi < NAV.length - 1 && <div className="my-1.5 mx-2 h-px bg-border" />}
          </div>
        ))}
      </nav>

      <div className="border-t border-border p-3 text-[10px] uppercase tracking-wider text-muted-foreground/60">
        <span>v0.1</span>
      </div>
    </aside>
  );
}
