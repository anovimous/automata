import { z } from "zod";

// =============================================================================
// Enums — kept 1:1 with the backend's Java enums (see docs/rest-api-review.md).
// =============================================================================

export const HttpJobScope = z.enum(["NARROW", "WIDE"]);
export type HttpJobScope = z.infer<typeof HttpJobScope>;

export const HttpJobState = z.enum([
  "DRAFT",
  "TOQUEUE",
  "SCHEDULED",
  "QUEUED",
  "RUNNING",
  "PAUSED",
  "CANCELED",
  "FINISHED",
  "FAILED",
  "SELFTERMINATED",
  "LOSTCONTACT",
]);
export type HttpJobState = z.infer<typeof HttpJobState>;

export const JobCreator = z.enum(["USER", "AI"]);
export type JobCreator = z.infer<typeof JobCreator>;

export const ConfigFileStatus = z.enum(["PREPARING", "READY", "FAILED"]);
export type ConfigFileStatus = z.infer<typeof ConfigFileStatus>;

// What kind of failure a worker reported with a FAILED or SELFTERMINATED job.
export const FailureCategory = z.enum([
  "PREPARATION",
  "POLL",
  "INVALID_CONFIG",
  "TOOL_START",
  "TOOL_EXIT",
  "RESULT",
  "UNKNOWN",
]);
export type FailureCategory = z.infer<typeof FailureCategory>;

export const Verbosity = z.enum(["SUMMARIZED", "NORMAL", "VERBOSE"]);
export type Verbosity = z.infer<typeof Verbosity>;

export const HttpMethod = z.enum(["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"]);
export type HttpMethod = z.infer<typeof HttpMethod>;

export const RequestContentType = z.enum([
  "JSON",
  "FORM",
  "MULTIPART",
  "XML",
  "HTML",
  "JS",
  "CSS",
  "TEXT",
  "IMAGE",
  "AUDIO",
  "VIDEO",
  "PDF",
  "ARCHIVE",
  "OCTET_STREAM",
  "OTHER",
]);
export type RequestContentType = z.infer<typeof RequestContentType>;

// Response filter content types (the backend's ResponseContentType enum).
export const ResponseContentType = z.enum([
  "JSON",
  "XML",
  "HTML",
  "JS",
  "CSS",
  "TEXT",
  "IMAGE",
  "AUDIO",
  "VIDEO",
  "PDF",
  "ARCHIVE",
  "OCTET_STREAM",
  "OTHER",
]);
export type ResponseContentType = z.infer<typeof ResponseContentType>;

export const RequestSource = z.enum(["MANUAL", "BURP", "WAYBACK", "AI"]);
export type RequestSource = z.infer<typeof RequestSource>;

// Sources a user can pick when adding a request by hand.
export const MANUAL_REQUEST_SOURCES: RequestSource[] = ["MANUAL", "WAYBACK"];

export const BodyStorage = z.enum(["NONE", "INLINE", "FILE"]);
export type BodyStorage = z.infer<typeof BodyStorage>;

export const HostScope = z.enum(["WILDCARD", "FQDN"]);
export type HostScope = z.infer<typeof HostScope>;

export const Platform = z.enum(["BUGCROWD", "HACKERONE", "INTIGRITI"]);
export type Platform = z.infer<typeof Platform>;

export const ComparatorSchema = z.enum(["REST", "GRAPHQL"]);
export type ComparatorSchema = z.infer<typeof ComparatorSchema>;

// The backend calls this HashTarget; the UI keeps calling it a modifier target.
export const ModifierTarget = z.enum(["METHOD", "PATH", "QUERYSTRING", "BODY"]);
export type ModifierTarget = z.infer<typeof ModifierTarget>;

export const Overhead = z.enum(["HIGH", "MEDIUM", "LOW"]);
export type Overhead = z.infer<typeof Overhead>;

export const Protocol = z.enum(["NETWORK", "HTTP"]);
export type Protocol = z.infer<typeof Protocol>;

export const ResultContentType = z.enum(["JSON", "TEXT"]);
export type ResultContentType = z.infer<typeof ResultContentType>;

export const AllowedTarget = z.enum([
  "SINGLE_HOST",
  "MULTILPLE_HOSTS", // sic - matches backend typo
  "SINGLE_REQUEST",
  "MULTIPLE_REQUESTS",
]);
export type AllowedTarget = z.infer<typeof AllowedTarget>;

export const SelectorType = z.enum([
  "SINGLE_HOST",
  "SINGLE_REQUEST",
  "MULTIPLE_HOSTS",
  "MULTIPLE_REQUESTS",
]);
export type SelectorType = z.infer<typeof SelectorType>;

export const Duration = z.enum(["SHORT", "LONG"]);
export type Duration = z.infer<typeof Duration>;

export const MatchReplaceTargetType = z.enum(["HEADER"]);
export type MatchReplaceTargetType = z.infer<typeof MatchReplaceTargetType>;

export const MatchReplaceRule = z.enum(["ADD", "REPLACE", "DELETE"]);
export type MatchReplaceRule = z.infer<typeof MatchReplaceRule>;

export const FindingStatus = z.enum(["SUGGESTED", "CONFIRMED"]);
export type FindingStatus = z.infer<typeof FindingStatus>;

export const FindingCreator = z.enum(["AI", "USER"]);
export type FindingCreator = z.infer<typeof FindingCreator>;

export const HumanVerdict = z.enum(["TRUE_POSITIVE", "FALSE_POSITIVE"]);
export type HumanVerdict = z.infer<typeof HumanVerdict>;

export const AgentContextItemType = z.enum(["REQUEST", "EQUALITY_SET"]);
export type AgentContextItemType = z.infer<typeof AgentContextItemType>;

export const RepresentativeStrategy = z.enum(["LATEST", "AI_JUDGEMENT"]);
export type RepresentativeStrategy = z.infer<typeof RepresentativeStrategy>;

// -----------------------------------------------------------------------------
// Job state helpers (the core owns the state machine — JobStateTransitions)
// -----------------------------------------------------------------------------

// No transitions out of these, no polling needed.
export const TERMINAL_JOB_STATES: HttpJobState[] = [
  "FINISHED",
  "CANCELED",
  "FAILED",
  "SELFTERMINATED",
  "LOSTCONTACT",
];

export const ACTIVE_JOB_STATES: HttpJobState[] = [
  "TOQUEUE",
  "SCHEDULED",
  "QUEUED",
  "RUNNING",
  "PAUSED",
];

export function isTerminalState(s: HttpJobState): boolean {
  return TERMINAL_JOB_STATES.includes(s);
}

export function isActiveState(s: HttpJobState): boolean {
  return ACTIVE_JOB_STATES.includes(s);
}
