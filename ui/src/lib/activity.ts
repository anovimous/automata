/**
 * Tracks "write activity" against programs and hosts in localStorage.
 *
 * Used by the dashboard's "Recent activity" panel: the most recent program
 * the user has interacted with via a write action, and inside it, the most
 * recent host they interacted with.
 *
 * Read-only views (opening pages) are deliberately NOT tracked, per spec.
 */

const STORAGE_KEY = "automata.activity";
const MAX_EVENTS = 200;

export type ActivityKind =
  | "create-program"
  | "patch-program"
  | "delete-program"
  | "create-host"
  | "patch-host"
  | "delete-host"
  | "create-tenant"
  | "patch-tenant"
  | "delete-tenant"
  | "create-auth"
  | "patch-auth"
  | "delete-auth"
  | "add-request"
  | "delete-request"
  | "associate-response"
  | "equalize"
  | "create-job"
  | "queue-job"
  | "pause-job"
  | "resume-job"
  | "cancel-job"
  | "delete-job"
  | "create-comparator"
  | "patch-comparator"
  | "delete-comparator"
  | "create-modifier"
  | "patch-modifier"
  | "delete-modifier"
  | "create-routine"
  | "patch-routine"
  | "delete-routine"
  | "create-wordlist"
  | "patch-wordlist"
  | "delete-wordlist"
  | "create-vulnerability"
  | "patch-vulnerability"
  | "delete-vulnerability";

export interface ActivityEvent {
  ts: number;
  kind: ActivityKind;
  programId?: number;
  programName?: string;
  hostId?: number;
  hostName?: string;
  meta?: Record<string, string | number | undefined>;
}

function loadAll(): ActivityEvent[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((e) => e && typeof e === "object" && typeof e.ts === "number");
  } catch {
    return [];
  }
}

function saveAll(events: ActivityEvent[]) {
  try {
    const trimmed = events.slice(0, MAX_EVENTS);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
    // Fire a same-window event so dashboards listening on activityChanged refresh.
    window.dispatchEvent(new Event("automata:activity"));
  } catch {
    /* storage full or unavailable — silently drop */
  }
}

export function recordActivity(event: Omit<ActivityEvent, "ts">) {
  const events = loadAll();
  events.unshift({ ...event, ts: Date.now() });
  saveAll(events);
}

export function getRecentEvents(limit = 20): ActivityEvent[] {
  return loadAll().slice(0, limit);
}

export function getMostRecentProgram(): {
  programId: number;
  programName?: string;
  ts: number;
} | null {
  const events = loadAll();
  for (const ev of events) {
    if (ev.programId !== undefined) {
      return { programId: ev.programId, programName: ev.programName, ts: ev.ts };
    }
  }
  return null;
}

export function getMostRecentHostInProgram(programId: number): {
  hostId: number;
  hostName?: string;
  ts: number;
} | null {
  const events = loadAll();
  for (const ev of events) {
    if (ev.programId === programId && ev.hostId !== undefined) {
      return { hostId: ev.hostId, hostName: ev.hostName, ts: ev.ts };
    }
  }
  return null;
}

export function clearActivity() {
  try {
    localStorage.removeItem(STORAGE_KEY);
    window.dispatchEvent(new Event("automata:activity"));
  } catch {
    /* */
  }
}

/**
 * Subscribe to activity changes (cross-tab via `storage` and same-tab via custom event).
 */
export function onActivityChange(handler: () => void): () => void {
  const storageHandler = (e: StorageEvent) => {
    if (e.key === STORAGE_KEY) handler();
  };
  window.addEventListener("storage", storageHandler);
  window.addEventListener("automata:activity", handler);
  return () => {
    window.removeEventListener("storage", storageHandler);
    window.removeEventListener("automata:activity", handler);
  };
}
