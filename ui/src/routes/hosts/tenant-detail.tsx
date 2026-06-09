import * as React from "react";
import { useNavigate, useParams } from "@tanstack/react-router";
import { Plus, Save, Trash2, Code, KeyRound } from "lucide-react";
import { useHost } from "@/hooks/useHosts";
import { useProgram } from "@/hooks/usePrograms";
import {
  useTenant,
  useDeleteTenant,
  useAuth,
  useCreateAuth,
  usePatchAuth,
  useDeleteAuth,
  usePatchTenant,
} from "@/hooks/useTenants";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Label, Textarea } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/primitives";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/fetcher";
import type { AuthNameValuePair } from "@/types/domain";

export function HostTenantDetailRoute() {
  const { programId, hostId, tenantId } = useParams({ strict: false }) as {
    programId: number;
    hostId: number;
    tenantId: number;
  };
  const navigate = useNavigate();
  const { toast } = useToast();

  const programQ = useProgram(programId);
  const hostQ = useHost(hostId);
  const tenantQ = useTenant(tenantId);
  const authQ = useAuth(tenantId);
  const patchTenant = usePatchTenant();
  const deleteTenant = useDeleteTenant();
  const createAuth = useCreateAuth();
  const patchAuth = usePatchAuth();
  const deleteAuth = useDeleteAuth();

  const [deleteTenantOpen, setDeleteTenantOpen] = React.useState(false);
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");

  React.useEffect(() => {
    if (tenantQ.data) {
      setName(tenantQ.data.name);
      setEmail(tenantQ.data.email ?? "");
    }
  }, [tenantQ.data]);

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
        label: "Tenants",
        to: `/programs/${programId}/hosts/${hostId}/tenants` as string,
      },
      { label: tenantQ.data?.name ?? `#${tenantId}` },
    ],
    null,
    [programQ.data?.name, hostQ.data?.host, tenantQ.data?.name, programId, hostId, tenantId],
  );

  async function saveTenant() {
    try {
      await patchTenant.mutateAsync({
        id: tenantId,
        req: {
          name: name.trim() || undefined,
          email: email.trim() || undefined,
        },
        hostId,
        programId,
      });
      toast({ title: "Tenant updated", variant: "success" });
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Update failed";
      toast({ title: "Update failed", description: msg, variant: "destructive" });
    }
  }

  const hasAuth = !!authQ.data;
  const initialKind: "static" | "dynamic" =
    authQ.data?.code?.code ? "dynamic" : "static";

  return (
    <PageContainer>
      <PageHeader
        title={tenantQ.data?.name ?? `Tenant #${tenantId}`}
        description={
          <span className="font-mono text-xs text-muted-foreground">
            on {hostQ.data?.host ?? `host #${hostId}`}
          </span>
        }
        actions={
          <Button
            variant="outline"
            size="icon"
            onClick={() => setDeleteTenantOpen(true)}
            aria-label="Delete tenant"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Identity</CardTitle>
            <Button onClick={saveTenant} disabled={patchTenant.isPending} size="sm">
              <Save className="h-3.5 w-3.5" />
              Save
            </Button>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="t-name">Name</Label>
              <Input id="t-name" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="t-email">Email</Label>
              <Input
                id="t-email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="—"
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <KeyRound className="h-3.5 w-3.5 text-muted-foreground" />
              <CardTitle>Authentication</CardTitle>
              {hasAuth ? (
                <Badge variant="success">Configured</Badge>
              ) : (
                <Badge variant="muted">Not configured</Badge>
              )}
            </div>
            {hasAuth && (
              <Button
                variant="ghost"
                size="sm"
                className="text-destructive"
                onClick={async () => {
                  try {
                    await deleteAuth.mutateAsync({ tenantId, hostId, programId });
                    toast({ title: "Auth removed", variant: "success" });
                  } catch (e) {
                    const msg = e instanceof ApiError ? e.message : "Delete failed";
                    toast({ title: "Delete failed", description: msg, variant: "destructive" });
                  }
                }}
              >
                <Trash2 className="h-3.5 w-3.5" />
                Remove
              </Button>
            )}
          </CardHeader>
          <CardContent>
            <AuthEditor
              tenantId={tenantId}
              hostId={hostId}
              programId={programId}
              hasAuth={hasAuth}
              initialKind={initialKind}
              initialStaticData={authQ.data?.authData?.data ?? null}
              initialLifetime={authQ.data?.authData?.lifeTimeInSeconds ?? ""}
              initialCode={authQ.data?.code?.code ?? ""}
              onSaved={() => authQ.refetch()}
              onCreate={(req) => createAuth.mutateAsync({ tenantId, req, hostId, programId })}
              onPatch={(req) => patchAuth.mutateAsync({ tenantId, req, hostId, programId })}
            />
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={deleteTenantOpen}
        onOpenChange={setDeleteTenantOpen}
        title="Delete tenant?"
        destructive
        confirmLabel="Delete"
        onConfirm={async () => {
          try {
            await deleteTenant.mutateAsync({ id: tenantId, hostId, programId });
            toast({ title: "Tenant deleted", variant: "success" });
            navigate({
              to: "/programs/$programId/hosts/$hostId/tenants",
              params: { programId, hostId },
            });
          } catch (e) {
            const msg = e instanceof ApiError ? e.message : "Delete failed";
            toast({ title: "Delete failed", description: msg, variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}

// =============================================================================
// AuthEditor
// =============================================================================
function AuthEditor({
  tenantId,
  hasAuth,
  initialKind,
  initialStaticData,
  initialLifetime,
  initialCode,
  onCreate,
  onPatch,
  onSaved,
}: {
  tenantId: number;
  hostId: number;
  programId: number;
  hasAuth: boolean;
  initialKind: "static" | "dynamic";
  initialStaticData: { headers?: AuthNameValuePair[] | null; cookies?: AuthNameValuePair[] | null; queryParameters?: AuthNameValuePair[] | null } | null;
  initialLifetime: string;
  initialCode: string;
  onCreate: (req: { authData?: { data?: typeof initialStaticData; lifeTimeInSeconds?: string }; code?: { code: string } }) => Promise<unknown>;
  onPatch: (req: { authData?: { data?: typeof initialStaticData; lifeTimeInSeconds?: string }; code?: { code: string } }) => Promise<unknown>;
  onSaved: () => void;
}) {
  const [kind, setKind] = React.useState<"static" | "dynamic">(initialKind);
  React.useEffect(() => setKind(initialKind), [initialKind]);

  const [headers, setHeaders] = React.useState<AuthNameValuePair[]>(
    initialStaticData?.headers ?? [],
  );
  const [cookies, setCookies] = React.useState<AuthNameValuePair[]>(
    initialStaticData?.cookies ?? [],
  );
  const [queryParameters, setQueryParameters] = React.useState<AuthNameValuePair[]>(
    initialStaticData?.queryParameters ?? [],
  );
  const [lifetime, setLifetime] = React.useState(initialLifetime ?? "");
  const [code, setCode] = React.useState(initialCode ?? "");
  const { toast } = useToast();

  React.useEffect(() => {
    setHeaders(initialStaticData?.headers ?? []);
    setCookies(initialStaticData?.cookies ?? []);
    setQueryParameters(initialStaticData?.queryParameters ?? []);
    setLifetime(initialLifetime ?? "");
    setCode(initialCode ?? "");
  }, [initialStaticData, initialLifetime, initialCode]);

  async function save() {
    try {
      const payload =
        kind === "static"
          ? {
              authData: {
                data: { headers, cookies, queryParameters },
                lifeTimeInSeconds: lifetime || undefined,
              },
            }
          : { code: { code } };
      if (hasAuth) {
        await onPatch(payload);
        toast({ title: "Auth updated", variant: "success" });
      } else {
        await onCreate(payload);
        toast({ title: "Auth created", variant: "success" });
      }
      onSaved();
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Save failed";
      toast({ title: "Save failed", description: msg, variant: "destructive" });
    }
  }

  return (
    <div className="space-y-3">
      <Tabs value={kind} onValueChange={(v) => setKind(v as "static" | "dynamic")}>
        <TabsList>
          <TabsTrigger value="static">
            <KeyRound className="h-3 w-3" />
            Static
          </TabsTrigger>
          <TabsTrigger value="dynamic">
            <Code className="h-3 w-3" />
            Dynamic
          </TabsTrigger>
        </TabsList>
        <TabsContent value="static">
          <div className="space-y-3">
            <KvList title="Headers" items={headers} onChange={setHeaders} />
            <KvList title="Cookies" items={cookies} onChange={setCookies} />
            <KvList title="Query params" items={queryParameters} onChange={setQueryParameters} />
            <div className="space-y-1.5">
              <Label>Lifetime (seconds)</Label>
              <Input
                value={lifetime}
                onChange={(e) => setLifetime(e.target.value)}
                placeholder="—"
              />
            </div>
          </div>
        </TabsContent>
        <TabsContent value="dynamic">
          <div className="space-y-1.5">
            <Label>Auth code</Label>
            <Textarea
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="// JS/Groovy snippet executed to populate auth data"
              className="min-h-[160px] text-[12px]"
            />
          </div>
        </TabsContent>
      </Tabs>
      <div className="flex justify-end">
        <Button onClick={save} size="sm">
          <Save className="h-3.5 w-3.5" />
          {hasAuth ? "Update" : "Create"}
        </Button>
      </div>
      <div className="text-[11px] text-muted-foreground">Tenant #{tenantId}</div>
    </div>
  );
}

function KvList({
  title,
  items,
  onChange,
}: {
  title: string;
  items: AuthNameValuePair[];
  onChange: (next: AuthNameValuePair[]) => void;
}) {
  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <Label>{title}</Label>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onChange([...items, { key: "", value: "" }])}
          className="text-xs"
        >
          <Plus className="h-3 w-3" />
          Add
        </Button>
      </div>
      <div className="space-y-1.5">
        {items.length === 0 && (
          <div className="text-xs text-muted-foreground">None</div>
        )}
        {items.map((item, i) => (
          <div key={i} className="flex items-center gap-2">
            <Input
              value={item.key}
              onChange={(e) => {
                const next = [...items];
                next[i] = { ...next[i], key: e.target.value };
                onChange(next);
              }}
              placeholder="key"
              className="font-mono"
            />
            <Input
              value={item.value}
              onChange={(e) => {
                const next = [...items];
                next[i] = { ...next[i], value: e.target.value };
                onChange(next);
              }}
              placeholder="value"
              className="font-mono"
            />
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => onChange(items.filter((_, ix) => ix !== i))}
              aria-label="Remove"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
