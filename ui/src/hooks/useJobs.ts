import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { jobsApi } from "@/api";
import { recordActivity } from "@/lib/activity";
import type { Pageable } from "@/types/pagination";
import type {
  HttpJobCreationRequest,
  HttpJobFilter,
  HttpJobSummary,
} from "@/types/domain";
import { isActiveState } from "@/types/enums";

const POLL_MS = 3000;

export const jobsKeys = {
  all: ["jobs"] as const,
  list: (filter: HttpJobFilter | undefined, pageable: Pageable | undefined) =>
    ["jobs", "list", { filter, pageable }] as const,
  detail: (id: number) => ["jobs", "detail", id] as const,
};

/**
 * Job list with optional polling.
 *
 * Polling kicks in automatically (every 3s) when at least one job in the
 * visible page is in an active (non-terminal) state. Pass `poll: false` to
 * disable polling entirely.
 */
export function useJobs(
  filter?: HttpJobFilter,
  pageable?: Pageable,
  opts?: { poll?: boolean },
) {
  const shouldPoll = opts?.poll !== false;
  return useQuery({
    queryKey: jobsKeys.list(filter, pageable),
    queryFn: () => jobsApi.list(filter, pageable),
    refetchInterval: (q) => {
      if (!shouldPoll) return false;
      const data = q.state.data;
      if (!data) return false;
      const hasActive = (data.content as HttpJobSummary[] | undefined)?.some((j) =>
        isActiveState(j.currentState),
      );
      return hasActive ? POLL_MS : false;
    },
  });
}

export function useJob(id: number | null | undefined) {
  return useQuery({
    enabled: id !== null && id !== undefined,
    queryKey: jobsKeys.detail(id ?? -1),
    queryFn: () => jobsApi.get(id as number),
    refetchInterval: (q) => {
      const data = q.state.data;
      if (!data) return false;
      return isActiveState(data.currentState) ? POLL_MS : false;
    },
  });
}

export function useCreateJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      req,
    }: {
      req: HttpJobCreationRequest;
      programId?: number;
      hostId?: number;
      hostName?: string;
    }) => jobsApi.createReturningId(req),
    onSuccess: (id, vars) => {
      qc.invalidateQueries({ queryKey: jobsKeys.all });
      recordActivity({
        kind: "create-job",
        programId: vars.programId,
        hostId: vars.hostId,
        hostName: vars.hostName,
        meta: { jobId: id ?? undefined, scope: vars.req.httpJobScope },
      });
    },
  });
}

export function useQueueJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; programId?: number; hostId?: number }) =>
      jobsApi.queue(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: jobsKeys.all });
      recordActivity({
        kind: "queue-job",
        programId: vars.programId,
        hostId: vars.hostId,
        meta: { jobId: vars.id },
      });
    },
  });
}

export function usePauseJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; programId?: number; hostId?: number }) =>
      jobsApi.pause(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: jobsKeys.all });
      recordActivity({
        kind: "pause-job",
        programId: vars.programId,
        hostId: vars.hostId,
        meta: { jobId: vars.id },
      });
    },
  });
}

export function useResumeJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; programId?: number; hostId?: number }) =>
      jobsApi.resume(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: jobsKeys.all });
      recordActivity({
        kind: "resume-job",
        programId: vars.programId,
        hostId: vars.hostId,
        meta: { jobId: vars.id },
      });
    },
  });
}

export function useCancelJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; programId?: number; hostId?: number }) =>
      jobsApi.cancel(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: jobsKeys.all });
      recordActivity({
        kind: "cancel-job",
        programId: vars.programId,
        hostId: vars.hostId,
        meta: { jobId: vars.id },
      });
    },
  });
}

export function useDeleteJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: number; programId?: number; hostId?: number }) =>
      jobsApi.delete(id),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: jobsKeys.all });
      recordActivity({
        kind: "delete-job",
        programId: vars.programId,
        hostId: vars.hostId,
        meta: { jobId: vars.id },
      });
    },
  });
}
