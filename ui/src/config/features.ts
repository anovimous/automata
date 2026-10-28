/**
 * Feature flags.
 *
 * A flag is false while the backend can't do the thing yet; the UI then hides or
 * disables the control instead of letting the user hit a 500/501. Flip it when the
 * corresponding backend support lands (see docs/rest-api-review.md).
 */

// MultipleHostsSelector / MultipleRequestsSelector answer 501 in TargetSelectionService,
// so WIDE jobs (multi-host) and multi-request NARROW jobs can't be created yet.
export const WIDE_MULTI_HOSTS_BACKEND_READY = false;
export const MULTI_REQUESTS_BACKEND_READY = false;

// There is no schedule endpoint; SCHEDULED is a state nothing sets yet.
export const JOB_SCHEDULING_BACKEND_READY = false;

// Delete endpoints that still throw "not implemented" on the backend.
export const DELETE_PROGRAM_BACKEND_READY = false; // ProgramService.deleteProgram
export const DELETE_HOST_BACKEND_READY = false; // HostService.deleteHost
export const DELETE_REQUEST_BACKEND_READY = false; // RequestService.deleteRequest
export const DELETE_ROUTINE_BACKEND_READY = false; // RoutineService.deleteRoutine
export const DELETE_JOB_BACKEND_READY = false; // HttpJobStateService.deleteJob (501)
export const DELETE_RESPONSE_BACKEND_READY = false; // ResponseService.deleteResponse is a no-op

// PATCH /api/programs/{id} ignores programRateLimit (the code is commented out).
export const PROGRAM_RATE_LIMIT_PATCH_BACKEND_READY = false;

// PATCH /api/hosts/{id} validates the rate limits but never stores them.
export const HOST_RATE_LIMIT_PATCH_BACKEND_READY = false;

// DELETE /api/tenants/{id}/authentication declares an unbound @PathVariable and fails.
export const DELETE_TENANT_AUTH_BACKEND_READY = false;

export const FEATURES = {
  WIDE_MULTI_HOSTS_BACKEND_READY,
  MULTI_REQUESTS_BACKEND_READY,
  JOB_SCHEDULING_BACKEND_READY,
  DELETE_PROGRAM_BACKEND_READY,
  DELETE_HOST_BACKEND_READY,
  DELETE_REQUEST_BACKEND_READY,
  DELETE_ROUTINE_BACKEND_READY,
  DELETE_JOB_BACKEND_READY,
  DELETE_RESPONSE_BACKEND_READY,
  PROGRAM_RATE_LIMIT_PATCH_BACKEND_READY,
  HOST_RATE_LIMIT_PATCH_BACKEND_READY,
  DELETE_TENANT_AUTH_BACKEND_READY,
};
