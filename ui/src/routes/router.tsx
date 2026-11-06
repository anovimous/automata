import * as React from "react";
import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from "@tanstack/react-router";
import { Sidebar } from "@/components/layout/sidebar";
import { TopBarProvider } from "@/components/layout/top-bar";
import { CommandPalette } from "@/components/cmdk/command-palette";

// Lazy route component imports
import { DashboardRoute } from "./dashboard";
import { ProgramsListRoute } from "./programs/list";
import { ProgramDetailRoute } from "./programs/detail";
import { HostDetailRoute } from "./hosts/detail";
import { HostTenantsRoute } from "./hosts/tenants";
import { HostTenantDetailRoute } from "./hosts/tenant-detail";
import { HostRequestsRoute } from "./hosts/requests";
import { HostRequestDetailRoute } from "./hosts/request-detail";
import { HostEqualitySetsRoute } from "./hosts/equality-sets";
import { HostEqualitySetDetailRoute } from "./hosts/equality-set-detail";
import { HostJobsRoute } from "./hosts/jobs";
import { RunJobRoute } from "./hosts/run-job";
import { JobsListRoute } from "./jobs/list";
import { JobDetailRoute } from "./jobs/detail";
import { FindingsListRoute, type FindingsSearch } from "./findings/list";
import { FindingDetailRoute } from "./findings/detail";
import { AgentContextsListRoute } from "./agentcontexts/list";
import { AgentContextDetailRoute } from "./agentcontexts/detail";
import { RoutinesListRoute } from "./routines/list";
import { RoutineDetailRoute } from "./routines/detail";
import { VulnerabilitiesListRoute } from "./vulnerabilities/list";
import { VulnerabilityDetailRoute } from "./vulnerabilities/detail";
import { ComparatorsListRoute } from "./comparators/list";
import { ComparatorDetailRoute } from "./comparators/detail";
import { ModifiersListRoute } from "./modifiers/list";
import { ModifierDetailRoute } from "./modifiers/detail";
import { WordlistsListRoute } from "./wordlists/list";
import { WordlistDetailRoute } from "./wordlists/detail";
import { SettingsRoute } from "./settings";

// =============================================================================
// Root layout
// =============================================================================
function RootLayout() {
  const [cmdOpen, setCmdOpen] = React.useState(false);

  // Global ⌘K / Ctrl+K listener
  React.useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setCmdOpen((o) => !o);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="flex h-full">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <TopBarProvider openCommand={() => setCmdOpen(true)}>
          <main className="flex-1 overflow-y-auto">
            <Outlet />
          </main>
        </TopBarProvider>
      </div>
      <CommandPalette open={cmdOpen} onOpenChange={setCmdOpen} />
    </div>
  );
}

// =============================================================================
// Route tree
// =============================================================================
export const rootRoute = createRootRoute({
  component: RootLayout,
});

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: DashboardRoute,
});

// ----- Programs -----
const programsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs",
  component: ProgramsListRoute,
});

const programDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId",
  parseParams: (p) => ({ programId: Number(p.programId) }),
  stringifyParams: (p) => ({ programId: String(p.programId) }),
  component: ProgramDetailRoute,
});

// ----- Hosts (under a program) -----
const hostDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId",
  parseParams: (p) => ({ programId: Number(p.programId), hostId: Number(p.hostId) }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
  }),
  component: HostDetailRoute,
});

const hostTenantsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/tenants",
  parseParams: (p) => ({ programId: Number(p.programId), hostId: Number(p.hostId) }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
  }),
  component: HostTenantsRoute,
});

const hostTenantDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/tenants/$tenantId",
  parseParams: (p) => ({
    programId: Number(p.programId),
    hostId: Number(p.hostId),
    tenantId: Number(p.tenantId),
  }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
    tenantId: String(p.tenantId),
  }),
  component: HostTenantDetailRoute,
});

const hostRequestsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/requests",
  parseParams: (p) => ({ programId: Number(p.programId), hostId: Number(p.hostId) }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
  }),
  validateSearch: (s: Record<string, unknown>): {
    page?: number;
    size?: number;
    method?: string;
    contentType?: string;
    source?: string;
    extension?: string;
    path?: string;
    tenantId?: number;
  } => ({
    page: s.page ? Number(s.page) : undefined,
    size: s.size ? Number(s.size) : undefined,
    method: typeof s.method === "string" ? s.method : undefined,
    contentType: typeof s.contentType === "string" ? s.contentType : undefined,
    source: typeof s.source === "string" ? s.source : undefined,
    extension: typeof s.extension === "string" ? s.extension : undefined,
    path: typeof s.path === "string" ? s.path : undefined,
    tenantId: s.tenantId ? Number(s.tenantId) : undefined,
  }),
  component: HostRequestsRoute,
});

const hostRequestDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/requests/$requestId",
  parseParams: (p) => ({
    programId: Number(p.programId),
    hostId: Number(p.hostId),
    requestId: Number(p.requestId),
  }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
    requestId: String(p.requestId),
  }),
  component: HostRequestDetailRoute,
});

const hostEqualitySetsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/equality-sets",
  parseParams: (p) => ({ programId: Number(p.programId), hostId: Number(p.hostId) }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
  }),
  component: HostEqualitySetsRoute,
});

const hostEqualitySetDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/equality-sets/$equalitySetId",
  parseParams: (p) => ({
    programId: Number(p.programId),
    hostId: Number(p.hostId),
    equalitySetId: Number(p.equalitySetId),
  }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
    equalitySetId: String(p.equalitySetId),
  }),
  component: HostEqualitySetDetailRoute,
});

const hostJobsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/jobs",
  parseParams: (p) => ({ programId: Number(p.programId), hostId: Number(p.hostId) }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
  }),
  component: HostJobsRoute,
});

// ----- Run Job (host scope) -----
const runJobRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/programs/$programId/hosts/$hostId/run-job",
  parseParams: (p) => ({ programId: Number(p.programId), hostId: Number(p.hostId) }),
  stringifyParams: (p) => ({
    programId: String(p.programId),
    hostId: String(p.hostId),
  }),
  validateSearch: (s: Record<string, unknown>): {
    requestIds?: string;
    hostIds?: string;
    kind?: string;
  } => ({
    requestIds: typeof s.requestIds === "string" ? s.requestIds : undefined,
    hostIds: typeof s.hostIds === "string" ? s.hostIds : undefined,
    kind: typeof s.kind === "string" ? s.kind : undefined,
  }),
  component: RunJobRoute,
});

// ----- Jobs -----
const jobsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/jobs",
  validateSearch: (s: Record<string, unknown>): {
    page?: number;
    size?: number;
    states?: string;
    scope?: string;
    programId?: number;
    hostId?: number;
    routineId?: number;
    tab?: string;
    createdBy?: string;
    agentContextId?: number;
  } => ({
    page: s.page ? Number(s.page) : undefined,
    size: s.size ? Number(s.size) : undefined,
    states: typeof s.states === "string" ? s.states : undefined,
    scope: typeof s.scope === "string" ? s.scope : undefined,
    programId: s.programId ? Number(s.programId) : undefined,
    hostId: s.hostId ? Number(s.hostId) : undefined,
    routineId: s.routineId ? Number(s.routineId) : undefined,
    tab: typeof s.tab === "string" ? s.tab : undefined,
    createdBy: typeof s.createdBy === "string" ? s.createdBy : undefined,
    agentContextId: s.agentContextId ? Number(s.agentContextId) : undefined,
  }),
  component: JobsListRoute,
});

const jobDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/jobs/$jobId",
  parseParams: (p) => ({ jobId: Number(p.jobId) }),
  stringifyParams: (p) => ({ jobId: String(p.jobId) }),
  component: JobDetailRoute,
});

// ----- Findings -----
const findingsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/findings",
  validateSearch: (s: Record<string, unknown>): FindingsSearch => ({
    page: s.page ? Number(s.page) : undefined,
    size: s.size ? Number(s.size) : undefined,
    hostId: s.hostId ? Number(s.hostId) : undefined,
    requestId: s.requestId ? Number(s.requestId) : undefined,
    jobId: s.jobId ? Number(s.jobId) : undefined,
    status: typeof s.status === "string" ? s.status : undefined,
    verdict: typeof s.verdict === "string" ? s.verdict : undefined,
    createdBy: typeof s.createdBy === "string" ? s.createdBy : undefined,
    create: s.create === true || s.create === "true" ? true : undefined,
  }),
  component: FindingsListRoute,
});

const findingDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/findings/$findingId",
  parseParams: (p) => ({ findingId: Number(p.findingId) }),
  stringifyParams: (p) => ({ findingId: String(p.findingId) }),
  component: FindingDetailRoute,
});

// ----- Agent contexts -----
const agentContextsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/agent-contexts",
  validateSearch: (s: Record<string, unknown>): {
    hostId?: number;
    page?: number;
    size?: number;
  } => ({
    hostId: s.hostId ? Number(s.hostId) : undefined,
    page: s.page ? Number(s.page) : undefined,
    size: s.size ? Number(s.size) : undefined,
  }),
  component: AgentContextsListRoute,
});

const agentContextDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/agent-contexts/$contextId",
  parseParams: (p) => ({ contextId: Number(p.contextId) }),
  stringifyParams: (p) => ({ contextId: String(p.contextId) }),
  component: AgentContextDetailRoute,
});

// ----- Resources -----
const routinesListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/routines",
  component: RoutinesListRoute,
});

const routineDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/routines/$id",
  parseParams: (p) => ({ id: Number(p.id) }),
  stringifyParams: (p) => ({ id: String(p.id) }),
  component: RoutineDetailRoute,
});

const vulnsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/vulnerabilities",
  component: VulnerabilitiesListRoute,
});

const vulnDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/vulnerabilities/$id",
  parseParams: (p) => ({ id: Number(p.id) }),
  stringifyParams: (p) => ({ id: String(p.id) }),
  component: VulnerabilityDetailRoute,
});

const comparatorsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/comparators",
  component: ComparatorsListRoute,
});

const comparatorDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/comparators/$id",
  parseParams: (p) => ({ id: Number(p.id) }),
  stringifyParams: (p) => ({ id: String(p.id) }),
  component: ComparatorDetailRoute,
});

const modifiersListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/modifiers",
  component: ModifiersListRoute,
});

const modifierDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/modifiers/$id",
  parseParams: (p) => ({ id: Number(p.id) }),
  stringifyParams: (p) => ({ id: String(p.id) }),
  component: ModifierDetailRoute,
});

const wordlistsListRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/wordlists",
  component: WordlistsListRoute,
});

const wordlistDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/wordlists/$id",
  parseParams: (p) => ({ id: Number(p.id) }),
  stringifyParams: (p) => ({ id: String(p.id) }),
  component: WordlistDetailRoute,
});

const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/settings",
  component: SettingsRoute,
});

const routeTree = rootRoute.addChildren([
  dashboardRoute,
  programsListRoute,
  programDetailRoute,
  hostDetailRoute,
  hostTenantsRoute,
  hostTenantDetailRoute,
  hostRequestsRoute,
  hostRequestDetailRoute,
  hostEqualitySetsRoute,
  hostEqualitySetDetailRoute,
  hostJobsRoute,
  runJobRoute,
  jobsListRoute,
  jobDetailRoute,
  findingsListRoute,
  findingDetailRoute,
  agentContextsListRoute,
  agentContextDetailRoute,
  routinesListRoute,
  routineDetailRoute,
  vulnsListRoute,
  vulnDetailRoute,
  comparatorsListRoute,
  comparatorDetailRoute,
  modifiersListRoute,
  modifierDetailRoute,
  wordlistsListRoute,
  wordlistDetailRoute,
  settingsRoute,
]);

export const router = createRouter({ routeTree, defaultPreload: "intent" });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
