/**
 * Feature flags.
 *
 * Flip these when the corresponding backend support lands.
 */

// The backend has MultipleHostsSelector stubbed out as // TODO.
// While false, WIDE-job creation via multi-host selection shows a banner
// and submission is blocked.
export const WIDE_MULTI_HOSTS_BACKEND_READY = false;

// Same for MultipleEqualitySetsSelector.
export const MULTI_EQUALITY_SETS_BACKEND_READY = false;

// Job scheduling endpoint is commented out in the backend controller.
// When it lands, flip this to enable a "schedule for later" mode in the
// job creation form.
export const JOB_SCHEDULING_BACKEND_READY = false;

export const FEATURES = {
  WIDE_MULTI_HOSTS_BACKEND_READY,
  MULTI_EQUALITY_SETS_BACKEND_READY,
  JOB_SCHEDULING_BACKEND_READY,
};
