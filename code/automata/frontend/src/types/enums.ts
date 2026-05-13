import { z } from "zod";

// =============================================================================
// Enums — matched 1:1 with the backend's OpenAPI definition.
// =============================================================================

export const HttpJobScope = z.enum(["NARROW", "WIDE", "GLOBAL"]);
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
  "TERMINATED",
]);
export type HttpJobState = z.infer<typeof HttpJobState>;

export const Verbosity = z.enum(["SUMMARIZED", "NORMAL", "VERBOSE"]);
export type Verbosity = z.infer<typeof Verbosity>;

export const HttpMethod = z.enum(["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"]);
export type HttpMethod = z.infer<typeof HttpMethod>;

export const RequestContentType = z.enum(["JSON", "MULTIPART", "FORM", "UNSUPPORTED"]);
export type RequestContentType = z.infer<typeof RequestContentType>;

export const ResponseContentType = z.enum(["JSON", "JS", "UNSUPPORTED"]);
export type ResponseContentType = z.infer<typeof ResponseContentType>;

export const RequestSource = z.enum(["MANUAL", "WAYBACK"]);
export type RequestSource = z.infer<typeof RequestSource>;

export const HostScope = z.enum(["WILDCARD", "FQDN"]);
export type HostScope = z.infer<typeof HostScope>;

export const Platform = z.enum(["BUGCROWD", "HACKERONE", "INTIGRITI"]);
export type Platform = z.infer<typeof Platform>;

export const ComparatorSchema = z.enum(["REST", "GRAPHQL"]);
export type ComparatorSchema = z.infer<typeof ComparatorSchema>;

export const ModifierTarget = z.enum(["METHOD", "PATH", "QUERYSTRING", "BODY"]);
export type ModifierTarget = z.infer<typeof ModifierTarget>;

export const Overhead = z.enum(["HIGH", "MEDIUM", "LOW"]);
export type Overhead = z.infer<typeof Overhead>;

export const Protocol = z.enum(["NETWORK", "HTTP"]);
export type Protocol = z.infer<typeof Protocol>;

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
  "SINGLE_EQUALITY_SET",
  "MULTIPLE_HOSTS",
  "MULTIPLE_REQUESTS",
  "MULTIPLE_EQUALITY_SETS",
]);
export type SelectorType = z.infer<typeof SelectorType>;

export const Duration = z.enum(["SHORT", "LONG"]);
export type Duration = z.infer<typeof Duration>;

export const MatchReplaceTargetType = z.enum(["HEADER"]);
export type MatchReplaceTargetType = z.infer<typeof MatchReplaceTargetType>;

export const MatchReplaceRule = z.enum(["ADD", "REPLACE", "DELETE"]);
export type MatchReplaceRule = z.infer<typeof MatchReplaceRule>;

// Terminal states — no polling needed, no transitions out
export const TERMINAL_JOB_STATES: HttpJobState[] = [
  "FINISHED",
  "CANCELED",
  "FAILED",
  "TERMINATED",
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
