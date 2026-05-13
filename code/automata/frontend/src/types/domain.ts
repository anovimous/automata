import { z } from "zod";
import {
  AllowedTarget,
  ComparatorSchema,
  Duration,
  HostScope,
  HttpJobScope,
  HttpJobState,
  HttpMethod,
  MatchReplaceRule,
  MatchReplaceTargetType,
  ModifierTarget,
  Overhead,
  Platform,
  Protocol,
  RequestContentType,
  RequestSource,
  ResponseContentType,
  SelectorType,
  Verbosity,
} from "./enums";

// Re-export enum types (the schemas live in ./enums alongside types of the
// same name; consumers that don't import from there directly use these).
export type {
  AllowedTarget,
  ComparatorSchema,
  Duration,
  HostScope,
  HttpJobScope,
  HttpJobState,
  HttpMethod,
  MatchReplaceRule,
  MatchReplaceTargetType,
  ModifierTarget,
  Overhead,
  Platform,
  Protocol,
  RequestContentType,
  RequestSource,
  ResponseContentType,
  SelectorType,
  Verbosity,
} from "./enums";

// -----------------------------------------------------------------------------
// Programs
// -----------------------------------------------------------------------------
export const ProgramDetailed = z.object({
  id: z.number(),
  insertionDate: z.string().optional(),
  name: z.string(),
  link: z.string().optional().nullable(),
  programRateLimit: z.number().optional().nullable(),
  platform: Platform.optional().nullable(),
  outOfScopeVulnIds: z.array(z.number()).optional().nullable(),
});
export type ProgramDetailed = z.infer<typeof ProgramDetailed>;

export const ProgramSummary = z.object({
  id: z.number(),
  insertionDate: z.string().optional(),
  name: z.string(),
  link: z.string().optional().nullable(),
  programRateLimit: z.number().optional().nullable(),
  platform: Platform.optional().nullable(),
});
export type ProgramSummary = z.infer<typeof ProgramSummary>;

// -----------------------------------------------------------------------------
// Hosts
// -----------------------------------------------------------------------------
export const HostDetailed = z.object({
  id: z.number(),
  insertionDate: z.string().optional(),
  host: z.string(),
  level: z.number().optional().nullable(),
  scope: HostScope,
  outOfScope: z.boolean().optional().nullable(),
  hostRateLimit: z.number().optional().nullable(),
  shortRateLimit: z.number().optional().nullable(),
  longRateLimit: z.number().optional().nullable(),
  programId: z.number(),
});
export type HostDetailed = z.infer<typeof HostDetailed>;

export const HostSummary = z.object({
  id: z.number(),
  host: z.string(),
  scope: HostScope.optional(),
  outOfScope: z.boolean().optional().nullable(),
  programId: z.number().optional(),
});
export type HostSummary = z.infer<typeof HostSummary>;

// -----------------------------------------------------------------------------
// Tenants & authentication
// -----------------------------------------------------------------------------
export const TenantDetailed = z.object({
  id: z.number(),
  creationDate: z.string().optional(),
  name: z.string(),
  email: z.string().optional().nullable(),
  hostId: z.number(),
});
export type TenantDetailed = z.infer<typeof TenantDetailed>;

export const TenantSummary = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string().optional().nullable(),
});
export type TenantSummary = z.infer<typeof TenantSummary>;

export const AuthNameValuePair = z.object({
  key: z.string(),
  value: z.string(),
});
export type AuthNameValuePair = z.infer<typeof AuthNameValuePair>;

export const StaticData = z.object({
  headers: z.array(AuthNameValuePair).optional().nullable(),
  cookies: z.array(AuthNameValuePair).optional().nullable(),
  queryParameters: z.array(AuthNameValuePair).optional().nullable(),
});
export type StaticData = z.infer<typeof StaticData>;

export const StaticAuthData = z.object({
  data: StaticData.optional().nullable(),
  lifeTimeInSeconds: z.string().optional().nullable(),
  populatedAt: z.string().optional().nullable(),
});
export type StaticAuthData = z.infer<typeof StaticAuthData>;

export const DynamicCode = z.object({
  code: z.string(),
});
export type DynamicCode = z.infer<typeof DynamicCode>;

export const Authentication = z.object({
  id: z.number(),
  creationDate: z.string().optional(),
  authData: StaticAuthData.optional().nullable(),
  isDynamicPopulationAvailable: z.boolean().optional().nullable(),
  code: DynamicCode.optional().nullable(),
  tenantId: z.number(),
});
export type Authentication = z.infer<typeof Authentication>;

// -----------------------------------------------------------------------------
// Vulnerabilities (recursive parent)
// -----------------------------------------------------------------------------
export const VulnerabilityDetailed = z.object({
  id: z.number(),
  name: z.string(),
  parentId: z.number().optional().nullable(),
});
export type VulnerabilityDetailed = z.infer<typeof VulnerabilityDetailed>;

export const VulnerabilitySummary = z.object({
  id: z.number(),
  name: z.string(),
  parentId: z.number().optional().nullable(),
});
export type VulnerabilitySummary = z.infer<typeof VulnerabilitySummary>;

// -----------------------------------------------------------------------------
// Routines
// -----------------------------------------------------------------------------
export const RoutineDetailed = z.object({
  id: z.number(),
  creationDate: z.string().optional(),
  key: z.string(),
  description: z.string().optional().nullable(),
  updatedAt: z.string().optional().nullable(),
  isAvailableAtConsumer: z.boolean().optional().nullable(),
  overhead: Overhead.optional().nullable(),
  protocol: Protocol.optional().nullable(),
  allowedTargets: z.array(AllowedTarget).optional().nullable(),
  vulnerabilityId: z.number().optional().nullable(),
});
export type RoutineDetailed = z.infer<typeof RoutineDetailed>;

export const RoutineSummary = z.object({
  id: z.number(),
  key: z.string(),
  description: z.string().optional().nullable(),
  isAvailableAtConsumer: z.boolean().optional().nullable(),
  overhead: Overhead.optional().nullable(),
  protocol: Protocol.optional().nullable(),
  allowedTargets: z.array(AllowedTarget).optional().nullable(),
  vulnerabilityId: z.number().optional().nullable(),
});
export type RoutineSummary = z.infer<typeof RoutineSummary>;

// -----------------------------------------------------------------------------
// Comparators & modifiers
// -----------------------------------------------------------------------------
export const Modifier = z.object({
  id: z.number(),
  creationDate: z.string().optional(),
  target: ModifierTarget,
  description: z.string().optional().nullable(),
  priority: z.number().optional().nullable(),
  schema: ComparatorSchema.optional().nullable(),
  available: z.boolean().optional().nullable(),
});
export type Modifier = z.infer<typeof Modifier>;

export const Comparator = z.object({
  id: z.number(),
  creationDate: z.string().optional(),
  name: z.string(),
  schema: ComparatorSchema.optional().nullable(),
  modifiers: z.array(Modifier).optional().nullable(),
  program: z
    .object({
      id: z.number(),
      name: z.string().optional(),
    })
    .optional()
    .nullable(),
  host: z
    .object({
      id: z.number(),
      host: z.string().optional(),
    })
    .optional()
    .nullable(),
});
export type Comparator = z.infer<typeof Comparator>;

// -----------------------------------------------------------------------------
// Wordlists
// -----------------------------------------------------------------------------
export const WordlistDetailed = z.object({
  id: z.number(),
  name: z.string(),
  path: z.string(),
  numberOfLines: z.number().optional().nullable(),
  vulnerabilityId: z.number().optional().nullable(),
});
export type WordlistDetailed = z.infer<typeof WordlistDetailed>;

export const WordlistSummary = z.object({
  id: z.number(),
  name: z.string(),
  path: z.string().optional().nullable(),
  numberOfLines: z.number().optional().nullable(),
  vulnerabilityId: z.number().optional().nullable(),
});
export type WordlistSummary = z.infer<typeof WordlistSummary>;

// -----------------------------------------------------------------------------
// Requests & responses
// -----------------------------------------------------------------------------
export const RequestResponse = z.object({
  requestId: z.number(),
  method: HttpMethod,
  computatedPath: z.string().optional().nullable(),
  extension: z.string().optional().nullable(),
  version: z.string().optional().nullable(),
  numberOfProperties: z.number(),
  contentType: RequestContentType.optional().nullable(),
  source: RequestSource.optional().nullable(),
  hostId: z.number().optional().nullable(),
  tenantId: z.number().optional().nullable(),
  programId: z.number().optional().nullable(),
  responseId: z.number().optional().nullable(),
});
export type RequestResponse = z.infer<typeof RequestResponse>;

export const ResponseDto = z.object({
  id: z.number(),
  statusCode: z.number().optional().nullable(),
  contentType: ResponseContentType.optional().nullable(),
  contentLength: z.number().optional().nullable(),
  hostId: z.number().optional().nullable(),
});
export type ResponseDto = z.infer<typeof ResponseDto>;

export const RawResponseDto = z.object({
  raw: z.string().optional().nullable(),
  responseBase64: z.string().optional().nullable(),
});
export type RawResponseDto = z.infer<typeof RawResponseDto>;

// -----------------------------------------------------------------------------
// Jobs
// -----------------------------------------------------------------------------
export const MatchAndReplace = z.object({
  targetType: MatchReplaceTargetType,
  rule: MatchReplaceRule,
  targetKey: z.string().optional().nullable(),
  value: z.string().optional().nullable(),
});
export type MatchAndReplace = z.infer<typeof MatchAndReplace>;

export const TargetSelector = z.object({
  selectorType: SelectorType,
  selector: z.any(), // type-narrowed per selectorType — handled in inferScope.ts
});
export type TargetSelector = z.infer<typeof TargetSelector>;

export const GenericConfig = z.object({
  matchAndReplace: z.array(MatchAndReplace).optional().nullable(),
});
export type GenericConfig = z.infer<typeof GenericConfig>;

export const HttpJobResponse = z.object({
  jobId: z.number(),
  httpJobScope: HttpJobScope,
  currentState: HttpJobState,
  requestedState: HttpJobState,
  verbosity: Verbosity.optional().nullable(),
  priority: z.number().optional().nullable(),
  rate: z.number().optional().nullable(),
  targetSelector: TargetSelector.optional().nullable(),
  genericConfig: GenericConfig.optional().nullable(),
  customConfig: z.any().optional().nullable(),
  program: z
    .object({
      id: z.number(),
      name: z.string().optional(),
    })
    .optional()
    .nullable(),
  routine: z
    .object({
      id: z.number(),
      key: z.string().optional(),
    })
    .optional()
    .nullable(),
});
export type HttpJobResponse = z.infer<typeof HttpJobResponse>;

export const HttpJobSummary = z.object({
  id: z.number(),
  scope: HttpJobScope,
  currentState: HttpJobState,
  creationDate: z.string().optional().nullable(),
  priority: z.number().optional().nullable(),
  rate: z.number().optional().nullable(),
  routineId: z.number().optional().nullable(),
  routineKey: z.string().optional().nullable(),
});
export type HttpJobSummary = z.infer<typeof HttpJobSummary>;

// -----------------------------------------------------------------------------
// Creation / patch request payloads
// -----------------------------------------------------------------------------
export interface ProgramCreationRequest {
  name: string;
  link?: string;
  programRateLimit?: number;
  platform?: z.infer<typeof Platform>;
  outOfScopeVulnIds?: number[];
}

export interface PatchProgramRequest {
  name?: string;
  link?: string;
  programRateLimit?: number;
  platform?: z.infer<typeof Platform>;
  outOfScopeVulnIds?: number[];
}

export interface HostCreationRequest {
  host: string;
  scope: z.infer<typeof HostScope>;
  hostRateLimit?: number;
  shortRateLimit?: number;
  longRateLimit?: number;
  programId: number;
}

export interface PatchHostRequest {
  host?: string;
  scope?: z.infer<typeof HostScope>;
  hostRateLimit?: number;
  shortRateLimit?: number;
  longRateLimit?: number;
  outOfScope?: boolean;
}

export interface TenantCreationRequest {
  name: string;
  email?: string;
  hostId: number;
}

export interface TenantPatchRequest {
  name?: string;
  email?: string;
}

export interface AuthenticationCreationRequest {
  authData?: StaticAuthData;
  code?: DynamicCode;
}

export interface AuthenticationPatchRequest {
  authData?: StaticAuthData;
  code?: DynamicCode;
}

export interface VulnerabilityCreationRequest {
  name: string;
  parentId?: number;
}

export interface VulnerabilityPatchRequest {
  name?: string;
  parentId?: number;
}

export interface RoutineCreationRequest {
  key: string;
  description?: string;
  isAvailableAtConsumer?: boolean;
  overhead?: z.infer<typeof Overhead>;
  protocol?: z.infer<typeof Protocol>;
  allowedTargets?: z.infer<typeof AllowedTarget>[];
  vulnerabilityId?: number;
}

export interface RoutinePatchRequest {
  description?: string;
  isAvailableAtConsumer?: boolean;
  overhead?: z.infer<typeof Overhead>;
  protocol?: z.infer<typeof Protocol>;
  allowedTargets?: z.infer<typeof AllowedTarget>[];
  vulnerabilityId?: number;
}

export interface ComparatorCreationRequest {
  name: string;
  schema?: z.infer<typeof ComparatorSchema>;
  programId?: number;
  hostId?: number;
  modifierIds?: number[];
}

export interface ComparatorPatchRequest {
  name?: string;
  schema?: z.infer<typeof ComparatorSchema>;
  setSchemaNull?: boolean;
  programId?: number;
  hostId?: number;
}

export interface ModifierCreationRequest {
  target: z.infer<typeof ModifierTarget>;
  description?: string;
  priority?: number;
  schema?: z.infer<typeof ComparatorSchema>;
  available?: boolean;
}

export interface ModifierPatchRequest {
  description?: string;
  isAvailable?: boolean;
}

export interface WordlistCreationRequest {
  name: string;
  path: string;
  vulnerabilityId?: number;
}

export interface WordlistPatchRequest {
  name?: string;
  path?: string;
  vulnerabilityId?: number;
}

export interface RawRequestAddition {
  requestBase64: string;
  source: z.infer<typeof RequestSource>;
  hostId: number;
  tenantId?: number;
  responseBase64?: string;
}

export interface RawResponseAddition {
  responseBase64: string;
}

export interface RequestsEqualization {
  hostId: number;
  requestsIds: number[];
  comparatorId: number;
}

export interface GenericHttpJobDetails {
  verbosity?: z.infer<typeof Verbosity>;
  priority?: number;
  rate?: number;
  routineId?: number;
  wordlistsIds?: number[];
  targetSelector?: TargetSelector;
  matchAndReplace?: MatchAndReplace[];
  customConfig?: unknown;
}

export interface NarrowHttpJobDetails {
  duration?: z.infer<typeof Duration>;
  hostId?: number;
  tenantId?: number;
}

export interface WideHttpJobDetails {
  programId?: number;
}

export interface GlobalHttpJobDetails {
  // empty per OpenAPI
}

export interface HttpJobCreationRequest {
  genericDetails: GenericHttpJobDetails;
  httpJobScope: z.infer<typeof HttpJobScope>;
  narrowJobDetails?: NarrowHttpJobDetails;
  wideJobDetails?: WideHttpJobDetails;
  globalJobDetails?: GlobalHttpJobDetails;
}

// -----------------------------------------------------------------------------
// Filters
// -----------------------------------------------------------------------------
export interface HttpJobFilter {
  programId?: number;
  hostId?: number;
  routineId?: number;
  scope?: z.infer<typeof HttpJobScope>;
  currentStates?: z.infer<typeof HttpJobState>[];
}

export interface RequestFilter {
  hostId?: number;
  programId?: number;
  tenantId?: number;
  source?: z.infer<typeof RequestSource>;
  method?: z.infer<typeof HttpMethod>;
  computatedPath?: string;
  extension?: string;
  contentType?: z.infer<typeof RequestContentType>;
}

export interface ResponseFilter {
  statusCode?: number;
  contentType?: z.infer<typeof ResponseContentType>;
  contentLength?: number;
  hostId?: number;
}
