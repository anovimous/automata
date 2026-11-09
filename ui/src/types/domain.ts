// =============================================================================
// Wire types — mirror the backend's DTO records (see docs/rest-api-review.md).
//
// Dates: `LocalDate` fields arrive as "YYYY-MM-DD", `Instant` fields as ISO-8601.
// Nullable columns arrive as `null`, so optional response fields are `| null`.
// Patch requests: an absent field is left alone; the `setXNull` flags clear a
// nullable field (the backend can't tell "absent" from "null" otherwise).
// =============================================================================

import type {
  AgentContextItemType,
  AllowedTarget,
  BodyStorage,
  ComparatorSchema,
  ConfigFileStatus,
  Duration,
  FailureCategory,
  FindingCreator,
  FindingStatus,
  HostScope,
  HttpJobScope,
  HttpJobState,
  HttpMethod,
  HumanVerdict,
  JobCreator,
  MatchReplaceRule,
  MatchReplaceTargetType,
  ModifierTarget,
  Platform,
  RepresentativeStrategy,
  RequestContentType,
  RequestSource,
  ResponseContentType,
  ResultContentType,
  SelectorType,
  Verbosity,
} from "./enums";

// Re-export enum types so consumers can import everything from one place.
export type {
  AgentContextItemType,
  AllowedTarget,
  BodyStorage,
  ComparatorSchema,
  ConfigFileStatus,
  Duration,
  FailureCategory,
  FindingCreator,
  FindingStatus,
  HostScope,
  HttpJobScope,
  HttpJobState,
  HttpMethod,
  HumanVerdict,
  JobCreator,
  MatchReplaceRule,
  MatchReplaceTargetType,
  ModifierTarget,
  Platform,
  RepresentativeStrategy,
  RequestContentType,
  RequestSource,
  ResponseContentType,
  ResultContentType,
  SelectorType,
  Verbosity,
} from "./enums";

// -----------------------------------------------------------------------------
// Programs
// -----------------------------------------------------------------------------
export interface ProgramDetailed {
  id: number;
  insertionDate?: string | null;
  name: string;
  link?: string | null;
  programRateLimit?: number | null;
  platform?: Platform | null;
  outOfScopeVulnIds?: number[] | null;
}

export interface ProgramSummary {
  id: number;
  insertionDate?: string | null;
  name: string;
  link?: string | null;
  platform?: Platform | null;
}

// POST /api/programs binds the Program entity directly, so the out-of-scope
// vulnerabilities can't be given here; set them with a patch afterwards.
export interface ProgramCreationRequest {
  name: string;
  link?: string;
  programRateLimit?: number;
  platform?: Platform;
}

export interface PatchProgramRequest {
  name?: string;
  link?: string;
  setLinkNull?: boolean;
  programRateLimit?: number;
  platform?: Platform;
  setPlatformNull?: boolean;
  outOfScopeVulnIds?: number[];
}

// -----------------------------------------------------------------------------
// Hosts
// -----------------------------------------------------------------------------
export interface HostDetailed {
  id: number;
  insertionDate?: string | null;
  host: string;
  level?: number | null;
  scope: HostScope;
  outOfScope: boolean;
  hostRateLimit: number;
  shortRateLimit: number;
  longRateLimit: number;
  programId: number;
  description?: string | null;
  technologies?: string[] | null;
  notes?: string | null;
}

export interface HostSummary {
  id: number;
  insertionDate?: string | null;
  host: string;
  level?: number | null;
  scope: HostScope;
  outOfScope: boolean;
}

/** All three limits are required (0 or more, `hostRateLimit >= short + long`). */
export interface HostCreationRequest {
  host: string;
  scope: HostScope;
  hostRateLimit: number;
  shortRateLimit: number;
  longRateLimit: number;
  programId: number;
}

/** The name of a host (and so its level) can't be changed. */
export interface PatchHostRequest {
  scope?: HostScope;
  hostRateLimit?: number;
  shortRateLimit?: number;
  longRateLimit?: number;
  description?: string;
  technologies?: string[];
  notes?: string;
}

// -----------------------------------------------------------------------------
// Tenants & static authentication
// -----------------------------------------------------------------------------
export interface TenantDetailed {
  id: number;
  creationDate?: string | null;
  name: string;
  email?: string | null;
  hostId: number;
}

export interface TenantSummary {
  id: number;
  creationDate?: string | null;
  name: string;
}

export interface TenantCreationRequest {
  name: string;
  email: string;
  hostId: number;
}

export interface TenantPatchRequest {
  name?: string;
  email?: string;
}

export interface AuthNameValuePair {
  key: string;
  value: string;
}

export interface StaticData {
  headers?: AuthNameValuePair[] | null;
  cookies?: AuthNameValuePair[] | null;
  queryParameters?: AuthNameValuePair[] | null;
}

export interface StaticAuthData {
  data?: StaticData | null;
  lifeTimeInSeconds?: string | null;
  populatedAt?: string | null;
}

export interface Authentication {
  id: number;
  creationDate?: string | null;
  authData?: StaticAuthData | null;
  tenantId: number;
}

export interface AuthenticationCreationRequest {
  authData?: StaticAuthData;
}

export interface AuthenticationPatchRequest {
  authData?: StaticAuthData;
}

// -----------------------------------------------------------------------------
// Vulnerabilities (tree: a vulnerability has an optional parent)
// -----------------------------------------------------------------------------
export interface VulnerabilityDetailed {
  id: number;
  name: string;
  parentId?: number | null;
}

export interface VulnerabilitySummary {
  id: number;
  name: string;
}

export interface VulnerabilityCreationRequest {
  name: string;
  parentId?: number;
}

export interface VulnerabilityPatchRequest {
  name?: string;
  parentVulnerabilityId?: number;
  setParentNull?: boolean;
}

// -----------------------------------------------------------------------------
// Routines
// -----------------------------------------------------------------------------
export interface RoutineDetailed {
  id: number;
  creationDate?: string | null;
  key: string;
  description: string;
  /** How an AI agent should use the routine (up to 5000 characters). Only this detail carries it, never the list. */
  usageDescriptionForAgent: string;
  updatedAt?: string | null;
  isAvailableAtConsumer: boolean;
  /** Information for callers: the routine is meant to run with a wordlist (not enforced by the core). */
  wordlistRequired: boolean;
  allowedTargets: AllowedTarget[];
  vulnerabilityId?: number | null;
  /** JSON Schema that `customConfig` of a job is validated against. */
  configSchema?: unknown;
  resultContentType?: ResultContentType | null;
}

export interface RoutineSummary {
  id: number;
  creationDate?: string | null;
  key: string;
  isAvailableAtConsumer: boolean;
  wordlistRequired: boolean;
  allowedTargets: AllowedTarget[];
}

export interface RoutineCreationRequest {
  key: string;
  /** Required by the backend (not blank). */
  description: string;
  /** Required by the backend (not blank, up to 5000 characters). */
  usageDescriptionForAgent: string;
  isAvailableAtConsumer?: boolean;
  wordlistRequired?: boolean;
  /** Required by the backend. */
  allowedTargets: AllowedTarget[];
  vulnerabilityId?: number;
  configSchema?: unknown;
  /** Required by the backend. */
  resultContentType: ResultContentType;
}

export interface RoutinePatchRequest {
  /** Can be changed but not removed (a blank value is refused). */
  description?: string;
  /** Can be changed but not removed (a blank value or more than 5000 characters is refused). */
  usageDescriptionForAgent?: string;
  isAvailableAtConsumer?: boolean;
  wordlistRequired?: boolean;
  allowedTargets?: AllowedTarget[];
  vulnerabilityId?: number;
  setVulnerabilityNull?: boolean;
  configSchema?: unknown;
  setConfigSchemaNull?: boolean;
}

// -----------------------------------------------------------------------------
// Comparators & modifiers
// -----------------------------------------------------------------------------
export interface Comparator {
  id: number;
  creationDate?: string | null;
  name: string;
  schema?: ComparatorSchema | null;
  modifiers: ModifierDetailed[];
}

export interface ComparatorCreationRequest {
  name: string;
  schema?: ComparatorSchema;
  /** At least one, every one existing and available. */
  modifierIds: number[];
}

export interface ComparatorPatchRequest {
  name?: string;
  schema?: ComparatorSchema;
  setSchemaNull?: boolean;
}

export interface ModifierSummary {
  id: number;
  creationDate?: string | null;
  target: ModifierTarget;
}

export interface ModifierDetailed {
  id: number;
  creationDate?: string | null;
  target: ModifierTarget;
  description?: string | null;
  priority?: number | null;
  schema?: ComparatorSchema | null;
  isAvailable?: boolean | null;
}

export interface ModifierCreationRequest {
  target: ModifierTarget;
  description: string;
  priority: number;
  schema?: ComparatorSchema;
  isAvailable: boolean;
}

export interface ModifierPatchRequest {
  description?: string;
  isAvailable?: boolean;
}

// -----------------------------------------------------------------------------
// Wordlists
// -----------------------------------------------------------------------------
/** A wordlist is its file (kept by the core); the file never changes. */
export interface WordlistDetailed {
  id: number;
  name?: string | null;
  description?: string | null;
  numberOfLines: number;
  sizeBytes: number;
  /** SHA-256 of the file, lower case hex. */
  sha256: string;
}

export interface WordlistSummary {
  id: number;
  name?: string | null;
  description?: string | null;
  numberOfLines: number;
  sizeBytes: number;
}

/** The `metadata` part of the multipart upload; `name` falls back to the file name. */
export interface WordlistCreationRequest {
  name?: string;
  description?: string;
  vulnIds?: number[];
}

/** Only these can change; the file and what comes from it can't. `vulnIds` replaces the links. */
export interface WordlistPatchRequest {
  name?: string;
  setNameNull?: boolean;
  description?: string;
  setDescriptionNull?: boolean;
  vulnIds?: number[];
}

// -----------------------------------------------------------------------------
// Requests & responses
// -----------------------------------------------------------------------------
export interface HeaderEntry {
  name: string;
  value: string;
}

export interface BodyMetadata {
  storage: BodyStorage;
  contentType?: string | null;
  mimeFamily?: string | null;
  sizeBytes: number;
}

export interface RequestLine {
  method: HttpMethod;
  computatedPath?: string | null;
  extension?: string | null;
  version?: string | null;
  target?: string | null;
}

export interface RequestSummary {
  requestId: number;
  method: HttpMethod;
  computatedPath?: string | null;
  extension?: string | null;
  version?: string | null;
  numberOfProperties: number;
  contentType?: RequestContentType | null;
  source?: RequestSource | null;
  hostId?: number | null;
  tenantId?: number | null;
  programId?: number | null;
  responseId?: number | null;
  bodyStoredAsFile: boolean;
  bodySizeBytes: number;
}

export interface RequestMetadata {
  requestId: number;
  hostId?: number | null;
  programId?: number | null;
  tenantId?: number | null;
  responseId?: number | null;
  source?: RequestSource | null;
  insertionDate?: string | null;
  line: RequestLine;
  headers: HeaderEntry[];
  body: BodyMetadata;
}

export interface RequestRaw {
  rawHeadBase64: string;
  rawBodyBase64?: string | null;
  bodyStoredAsFile: boolean;
  responseId?: number | null;
}

export interface ResponseSummary {
  responseId: number;
  requestId?: number | null;
  hostId?: number | null;
  statusCode?: number | null;
  contentType?: ResponseContentType | null;
  contentLength?: number | null;
  insertionDate?: string | null;
  bodyStoredAsFile: boolean;
  bodySizeBytes: number;
}

export interface ResponseMetadata {
  responseId: number;
  requestId?: number | null;
  hostId?: number | null;
  statusCode?: number | null;
  insertionDate?: string | null;
  headers: HeaderEntry[];
  body: BodyMetadata;
}

export interface ResponseRaw {
  rawHeadBase64: string;
  rawBodyBase64?: string | null;
  bodyStoredAsFile: boolean;
}

export interface RawRequestAddition {
  requestBase64: string;
  source: RequestSource;
  hostId: number;
  tenantId?: number;
  responseBase64?: string;
}

export interface RawResponseAddition {
  responseBase64: string;
}

export interface RequestFilter {
  hostId?: number;
  programId?: number;
  tenantId?: number;
  source?: RequestSource;
  method?: HttpMethod;
  computatedPath?: string;
  extension?: string;
  contentType?: RequestContentType;
}

export interface ResponseFilter {
  statusCode?: number;
  contentType?: ResponseContentType;
  contentLength?: number;
  hostId?: number;
}

// -----------------------------------------------------------------------------
// Equalization (grouping equivalent requests of a host into equality sets)
// -----------------------------------------------------------------------------
export interface Equalization {
  id: number;
  lastRun?: string | null;
  autoEqualize: boolean;
  comparatorId: number;
}

export interface EqualizationCreationRequest {
  hostId: number;
  comparatorId: number;
  autoEqualize: boolean;
}

export interface EqualitySet {
  id: number;
  alias?: string | null;
  numberOfRequests: number;
  autoApproval: boolean;
  equalizationId?: number | null;
}

export interface EqualitySetPatchRequest {
  alias?: string;
  autoApproval?: boolean;
}

// -----------------------------------------------------------------------------
// Jobs
// -----------------------------------------------------------------------------
export interface MatchAndReplace {
  targetType: MatchReplaceTargetType;
  rule: MatchReplaceRule;
  targetKey?: string | null;
  value?: string | null;
}

export interface TargetSelector {
  selectorType: SelectorType;
  /** Shape depends on `selectorType`; see lib/inferScope.ts. */
  selector: unknown;
}

export interface GenericConfig {
  matchAndReplace?: MatchAndReplace[] | null;
}

export interface HttpJobResponse {
  jobId: number;
  httpJobScope: HttpJobScope;
  currentState: HttpJobState;
  requestedState?: HttpJobState | null;
  verbosity?: Verbosity | null;
  priority?: number | null;
  rate?: number | null;
  targetSelector?: TargetSelector | null;
  genericConfig?: GenericConfig | null;
  customConfig?: unknown;
  program?: { id: number; name?: string | null } | null;
  routine?: { id: number; key?: string | null } | null;
  failureMessage?: string | null;
  /** Set with `failureMessage` when the job ended FAILED or SELFTERMINATED. */
  failureCategory?: FailureCategory | null;
  configFileStatus?: ConfigFileStatus | null;
  configFileFailure?: string | null;
  aiDescription?: string | null;
  aiResult?: string | null;
  createdBy: JobCreator;
  approved: boolean;
  /** The job is in a state it never leaves (computed by the core). */
  terminal: boolean;
  /** A draft that needs someone: an unapproved AI job, or one whose files failed. */
  waitingForAction: boolean;
  agentContextUuid?: string | null;
}

export interface HttpJobSummary {
  id: number;
  creationDate?: string | null;
  scope: HttpJobScope;
  currentState: HttpJobState;
  priority?: number | null;
  rate?: number | null;
  routineId?: number | null;
  routineKey?: string | null;
  createdBy: JobCreator;
  approved: boolean;
  terminal: boolean;
  waitingForAction: boolean;
}

export interface GenericHttpJobDetails {
  verbosity: Verbosity;
  /** 1-5 (4 and up are eager). Not defaulted by the core: always send it. */
  priority: number;
  /** At least 1, and within the limits of the host / program / duration. */
  rate: number;
  routineId: number;
  wordlistsIds?: number[];
  targetSelector: TargetSelector;
  matchAndReplace?: MatchAndReplace[];
  customConfig?: unknown;
}

export interface NarrowHttpJobDetails {
  duration: Duration;
  hostId: number;
  tenantId?: number;
}

export interface WideHttpJobDetails {
  programId: number;
}

export interface HttpJobCreationRequest {
  genericDetails: GenericHttpJobDetails;
  httpJobScope: HttpJobScope;
  narrowJobDetails?: NarrowHttpJobDetails;
  wideJobDetails?: WideHttpJobDetails;
}

export interface HttpJobFilter {
  programId?: number;
  hostId?: number;
  routineId?: number;
  scope?: HttpJobScope;
  currentStates?: HttpJobState[];
  createdBy?: JobCreator;
  agentContextId?: number;
  targetRequestId?: number;
}

// -----------------------------------------------------------------------------
// Findings
// -----------------------------------------------------------------------------
export interface FindingSummary {
  id: number;
  requestId?: number | null;
  hostId?: number | null;
  vulnerabilityId?: number | null;
  severity: number;
  status: FindingStatus;
  confidence: number;
  createdBy: FindingCreator;
  humanVerdict?: HumanVerdict | null;
  creationDate?: string | null;
}

export interface FindingDetailed extends FindingSummary {
  jobId?: number | null;
  evidence?: string | null;
  pocReproductionSteps?: string | null;
  aiDescription?: string | null;
  humanComment?: string | null;
  reviewedAt?: string | null;
}

export interface FindingCreationRequest {
  requestId: number;
  jobId?: number;
  vulnerabilityId: number;
  /** CVSS, 0-10. */
  severity: number;
  status: FindingStatus;
  /** 0-10. */
  confidence: number;
  evidence: string;
  /** Required when `status` is CONFIRMED. */
  pocReproductionSteps?: string;
  createdBy: FindingCreator;
  aiDescription?: string;
}

export interface FindingPatchRequest {
  severity?: number;
  status?: FindingStatus;
  confidence?: number;
  evidence?: string;
  pocReproductionSteps?: string;
  setPocReproductionStepsNull?: boolean;
  aiDescription?: string;
  setAiDescriptionNull?: boolean;
}

export interface FindingReviewRequest {
  verdict: HumanVerdict;
  comment?: string;
}

export interface FindingFilter {
  hostId?: number;
  requestId?: number;
  jobId?: number;
  status?: FindingStatus;
  humanVerdict?: HumanVerdict;
  createdBy?: FindingCreator;
}

// -----------------------------------------------------------------------------
// Agent contexts (the human side: configure what an AI agent may work on)
// -----------------------------------------------------------------------------
export interface AgentContextItem {
  id: number;
  itemType: AgentContextItemType;
  requestId?: number | null;
  equalitySetId?: number | null;
}

export interface AgentContextSummary {
  id: number;
  uuid: string;
  name: string;
  hostId: number;
  representativeStrategy: RepresentativeStrategy;
  itemCount: number;
  creationDate?: string | null;
}

export interface AgentContextDetailed {
  id: number;
  uuid: string;
  name: string;
  description?: string | null;
  hostId: number;
  defaultAutoApproval: boolean;
  representativeStrategy: RepresentativeStrategy;
  allowedRoutineIds: number[];
  tenantIds: number[];
  defaultDuration?: Duration | null;
  defaultVerbosity?: Verbosity | null;
  defaultPriority?: number | null;
  maxRate: number;
  defaultJobRate: number;
  creationDate?: string | null;
  items: AgentContextItem[];
}

export interface AgentContextCreationRequest {
  name: string;
  description?: string;
  hostId: number;
  defaultAutoApproval: boolean;
  representativeStrategy: RepresentativeStrategy;
  allowedRoutineIds?: number[];
  defaultDuration?: Duration;
  defaultVerbosity?: Verbosity;
  defaultPriority?: number;
  maxRate: number;
  defaultJobRate: number;
  tenantIds?: number[];
}

export interface AgentContextPatchRequest {
  name?: string;
  description?: string;
  setDescriptionNull?: boolean;
  defaultAutoApproval?: boolean;
  representativeStrategy?: RepresentativeStrategy;
  allowedRoutineIds?: number[];
  defaultDuration?: Duration;
  setDefaultDurationNull?: boolean;
  defaultVerbosity?: Verbosity;
  setDefaultVerbosityNull?: boolean;
  defaultPriority?: number;
  setDefaultPriorityNull?: boolean;
  maxRate?: number;
  defaultJobRate?: number;
}

export interface AgentContextItemAddRequest {
  itemType: AgentContextItemType;
  requestId?: number;
  equalitySetId?: number;
}

export interface ResolvedTarget {
  requestId: number;
  automataJobEligible: boolean;
  autoApproval: boolean;
  sourceItemType: AgentContextItemType;
  sourceEqualitySetId?: number | null;
}
