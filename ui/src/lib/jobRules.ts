// =============================================================================
// Rules the core applies when a job is created (HttpJobCreationService), kept here
// so the forms can say what is wrong before the request is sent. The core stays the
// authority: it answers 400 / 422 with the reason when a rule is broken.
// =============================================================================

import type {
  Duration,
  FailureCategory,
  HostDetailed,
  ProgramDetailed,
  RequestContentType,
} from "@/types/domain";

/** A job always has a rate of at least 1. */
export const MIN_JOB_RATE = 1;
export const MIN_JOB_PRIORITY = 1;
export const MAX_JOB_PRIORITY = 5;
/** Jobs of this priority or more are dispatched before all the others. */
export const EAGER_PRIORITY = 4;

/**
 * A request can be a job target when it can be rebuilt from its detailed components:
 * it has no body at all, or a JSON or FORM body (the only ones with parsed properties).
 * Any other request answers 422.
 */
export function isJobEligibleRequest(
  contentType: RequestContentType | string | null | undefined,
  bodySizeBytes: number | null | undefined,
): boolean {
  if (!bodySizeBytes) return true;
  return contentType === "JSON" || contentType === "FORM";
}

export const NOT_ELIGIBLE_REASON =
  "Only requests without a body, or with a JSON or form body, can be job targets";

export interface RateLimit {
  name: string;
  value: number;
}

/** The limits a narrow job of this duration on the host has to abide by. */
export function narrowRateLimits(
  host: Pick<HostDetailed, "hostRateLimit" | "shortRateLimit" | "longRateLimit">,
  program: Pick<ProgramDetailed, "programRateLimit"> | undefined,
  duration: Duration,
): RateLimit[] {
  const limits: RateLimit[] = [{ name: "host rate limit", value: host.hostRateLimit }];
  if (program && program.programRateLimit != null) {
    limits.push({ name: "program rate limit", value: program.programRateLimit });
  }
  limits.push(
    duration === "SHORT"
      ? { name: "short rate limit of the host", value: host.shortRateLimit }
      : { name: "long rate limit of the host", value: host.longRateLimit },
  );
  return limits;
}

/** A wide job is limited by the rate limit of its program only. */
export function wideRateLimits(
  program: Pick<ProgramDetailed, "programRateLimit"> | undefined,
): RateLimit[] {
  if (!program || program.programRateLimit == null) return [];
  return [{ name: "program rate limit", value: program.programRateLimit }];
}

/** The tightest limit, or null when there is none to check. */
export function tightestLimit(limits: RateLimit[]): RateLimit | null {
  if (limits.length === 0) return null;
  return limits.reduce((min, l) => (l.value < min.value ? l : min));
}

/** What is wrong with a rate typed in a form, or null when it is acceptable. */
export function rateProblem(raw: string, limits: RateLimit[]): string | null {
  if (raw.trim() === "") return "A rate is required.";
  const rate = Number(raw);
  if (!Number.isInteger(rate) || rate < MIN_JOB_RATE) {
    return `The rate must be a whole number of at least ${MIN_JOB_RATE}.`;
  }
  const above = limits.find((l) => rate > l.value);
  if (above) return `The rate is above the ${above.name} (${above.value}).`;
  return null;
}

/** What is wrong with a priority typed in a form, or null when it is acceptable. */
export function priorityProblem(raw: string): string | null {
  if (raw.trim() === "") return "A priority is required.";
  const priority = Number(raw);
  if (!Number.isInteger(priority) || priority < MIN_JOB_PRIORITY || priority > MAX_JOB_PRIORITY) {
    return `The priority is between ${MIN_JOB_PRIORITY} and ${MAX_JOB_PRIORITY}.`;
  }
  return null;
}

/** The same rule on a metadata descriptor (`mimeFamily` is the name of the content type family). */
export function isJobEligibleBody(body: { mimeFamily?: string | null; sizeBytes: number }): boolean {
  return isJobEligibleRequest(body.mimeFamily, body.sizeBytes);
}

/** A host with a rate limit of 0 is closed to jobs. */
export function hostAcceptsJobs(host: Pick<HostDetailed, "hostRateLimit"> | undefined): boolean {
  return !host || host.hostRateLimit >= MIN_JOB_RATE;
}

// -----------------------------------------------------------------------------
// Failure categories
// -----------------------------------------------------------------------------
export const FAILURE_CATEGORY_INFO: Record<FailureCategory, { label: string; hint: string }> = {
  PREPARATION: {
    label: "Preparation",
    hint: "The worker could not set the job up: work directory, files, a wordlist or the routine mapping.",
  },
  POLL: {
    label: "Poll",
    hint: "The first poll of the core failed.",
  },
  INVALID_CONFIG: {
    label: "Invalid config",
    hint: "The job details could not be turned into a command: unknown flag, wordlists the routine does not accept, or a custom config the connector can't read.",
  },
  TOOL_START: {
    label: "Tool start",
    hint: "The tool could not be started.",
  },
  TOOL_EXIT: {
    label: "Tool exit",
    hint: "The tool ran and exited with an error.",
  },
  RESULT: {
    label: "Result",
    hint: "The result is missing or could not be uploaded.",
  },
  UNKNOWN: {
    label: "Unknown",
    hint: "The worker gave no category.",
  },
};

// -----------------------------------------------------------------------------
// Defaults of an agent context (AgentContextService.requireValidDefaults)
// -----------------------------------------------------------------------------

/** What is wrong with the rates and the priority of an agent context form, or null. */
export function contextDefaultsProblem(
  maxRate: string,
  defaultJobRate: string,
  defaultPriority: string,
): string | null {
  if (maxRate.trim() === "" || defaultJobRate.trim() === "") {
    return "The max rate and the default job rate are required.";
  }
  const max = Number(maxRate);
  const def = Number(defaultJobRate);
  if (![max, def].every((n) => Number.isInteger(n) && n >= MIN_JOB_RATE)) {
    return `The rates are whole numbers of at least ${MIN_JOB_RATE}.`;
  }
  if (def > max) return `The default job rate (${def}) can't be above the max rate (${max}).`;
  if (defaultPriority.trim() !== "") return priorityProblem(defaultPriority);
  return null;
}
