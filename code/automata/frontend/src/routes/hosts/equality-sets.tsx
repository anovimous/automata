import { useParams } from "@tanstack/react-router";
import { GitMerge, Shuffle } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/common/states";

export function HostEqualitySetsRoute() {
  const { programId, hostId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
  };

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
      { label: "Equality sets" },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, programId, hostId],
  );

  return (
    <PageContainer>
      <PageHeader
        title="Equality sets"
        description={
          <span className="font-mono text-xs text-muted-foreground">
            on {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
      />

      <Card>
        <CardContent className="p-0">
          <EmptyState
            icon={GitMerge}
            title="Equality sets are produced by equalizing requests"
            description="Select a set of requests under this host, run an equalize operation with a comparator, and the resulting groups can be used as job targets."
            action={
              <Link
                to="/programs/$programId/hosts/$hostId/equalize"
                params={{ programId, hostId }}
              >
                <Button>
                  <Shuffle className="h-3.5 w-3.5" />
                  Open equalize
                </Button>
              </Link>
            }
          />
        </CardContent>
      </Card>
    </PageContainer>
  );
}
