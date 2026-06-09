import { useNavigate, useParams } from "@tanstack/react-router";
import { Play, ArrowLeft } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export function HostEqualitySetDetailRoute() {
  const { programId, hostId, equalitySetId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
    equalitySetId: number;
  };
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
      {
        label: "Equality sets",
        to: `/programs/${programId}/hosts/${hostId}/equality-sets` as string,
      },
      { label: `#${equalitySetId}` },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId, equalitySetId],
  );

  return (
    <PageContainer>
      <Button
        variant="ghost"
        size="sm"
        className="mb-3 -ml-2"
        onClick={() =>
          navigate({
            to: "/programs/$programId/hosts/$hostId/equality-sets",
            params: { programId, hostId },
          })
        }
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </Button>

      <PageHeader
        title={`Equality set #${equalitySetId}`}
        actions={
          <Button
            onClick={() =>
              navigate({
                to: "/programs/$programId/hosts/$hostId/run-job",
                params: { programId, hostId },
                search: {
                  kind: "single-equality-set",
                  equalitySetIds: String(equalitySetId),
                },
              })
            }
          >
            <Play className="h-3.5 w-3.5" />
            Run job
          </Button>
        }
      />

      <Card>
        <CardContent className="p-6 text-sm text-muted-foreground">
          The backend doesn't currently expose a public endpoint to list the members of an
          equality set. Use the Run job button above to target this set with a routine.
        </CardContent>
      </Card>
    </PageContainer>
  );
}
