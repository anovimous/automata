/**
 * Feature flags.
 *
 * A flag is false while the backend can't do the thing yet; the UI then hides or
 * disables the control instead of letting the user hit a 501. Flip it when the
 * corresponding backend support lands (see docs/rest-api-review.md).
 */

// There is no schedule endpoint; SCHEDULED is a state nothing sets yet (and canceling a
// scheduled job answers 501).
export const JOB_SCHEDULING_BACKEND_READY = false;

// The deletes below answer 501 "not implemented" on the backend.
export const DELETE_PROGRAM_BACKEND_READY = false; // ProgramService.deleteProgram
export const DELETE_HOST_BACKEND_READY = false; // HostService.deleteHost
export const DELETE_REQUEST_BACKEND_READY = false; // RequestService.deleteRequest
export const DELETE_ROUTINE_BACKEND_READY = false; // RoutineService.deleteRoutine
export const DELETE_JOB_BACKEND_READY = false; // HttpJobStateService.deleteJob
export const DELETE_RESPONSE_BACKEND_READY = false; // ResponseService.deleteResponse

export const FEATURES = {
  JOB_SCHEDULING_BACKEND_READY,
  DELETE_PROGRAM_BACKEND_READY,
  DELETE_HOST_BACKEND_READY,
  DELETE_REQUEST_BACKEND_READY,
  DELETE_ROUTINE_BACKEND_READY,
  DELETE_JOB_BACKEND_READY,
  DELETE_RESPONSE_BACKEND_READY,
};
