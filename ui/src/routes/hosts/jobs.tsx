import { useNavigate, useParams, useSearch } from "@tanstack/react-router";
import { Play } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Button } from "@/components/ui/button";
import { JobsListContent } from "@/routes/jobs/list";

export function HostJobsRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };
  const search = useSearch({ strict: false }) as Record<string, unknown>;
  const navigate = useNavigate();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);

  useSetTopBar(
    [
      { label: "Programs", to: "/programs" },
      {
        label: programQ.data?.name ?? `#${programId}`,
        to: `/programs/${programId}` as string,
      },
      {
        label: hostQ.data?.host ?? `Host #${hostId}`,
        to: `/programs/${programId}/hosts/${hostId}` as string,
      },
      { label: "Jobs" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  return (
    <PageContainer>
      <PageHeader
        title="Jobs"
        description={
          <span className="font-mono text-xs text-muted-foreground">
            on {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
        actions={
          <Button
            onClick={() =>
              navigate({
                to: "/programs/$programId/hosts/$hostId/run-job",
                params: { programId, hostId },
                search: { kind: "single-host" },
              })
            }
          >
            <Play className="h-3.5 w-3.5" />
            Run job on host
          </Button>
        }
      />

      <JobsListContent
        forceFilter={{ hostId, programId }}
        hideFilters
        searchState={search}
        onSearchChange={(s) =>
          navigate({
            to: "/programs/$programId/hosts/$hostId/jobs",
            params: { programId, hostId },
            search: s,
          })
        }
      />
    </PageContainer>
  );
}
