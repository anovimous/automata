import * as React from "react";
import { Moon, Sun, Trash2, Info } from "lucide-react";
import { useSetTopBar } from "@/components/layout/top-bar";
import { PageContainer, PageHeader } from "@/components/layout/page";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  applyTheme,
  getStoredTheme,
  setStoredTheme,
  type ThemeMode,
} from "@/lib/utils";
import { clearActivity } from "@/lib/activity";
import { ConfirmDialog } from "@/components/common/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/cn";
import {
  JOB_SCHEDULING_BACKEND_READY,
  MULTI_EQUALITY_SETS_BACKEND_READY,
  WIDE_MULTI_HOSTS_BACKEND_READY,
} from "@/config/features";

export function SettingsRoute() {
  useSetTopBar([{ label: "Settings" }]);
  const { toast } = useToast();

  const [theme, setTheme] = React.useState<ThemeMode>(() => getStoredTheme());
  const [clearOpen, setClearOpen] = React.useState(false);

  function pickTheme(next: ThemeMode) {
    setTheme(next);
    setStoredTheme(next);
    applyTheme(next);
  }

  return (
    <PageContainer>
      <PageHeader title="Settings" description="Local preferences and diagnostics." />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Appearance</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-2">
              <ThemeButton
                active={theme === "light"}
                onClick={() => pickTheme("light")}
                icon={Sun}
                label="Light"
              />
              <ThemeButton
                active={theme === "dark"}
                onClick={() => pickTheme("dark")}
                icon={Moon}
                label="Dark"
              />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Activity</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p className="text-xs text-muted-foreground">
              The dashboard's "Recent activity" panel is computed from a log stored in
              this browser only. Clearing it does not affect any data on the server.
            </p>
            <Button variant="outline" onClick={() => setClearOpen(true)}>
              <Trash2 className="h-3.5 w-3.5" />
              Clear activity log
            </Button>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Feature flags</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground mb-3">
              Some selector types and operations are blocked in the UI because the
              backend support for them is not yet ready. Edit{" "}
              <code className="font-mono">src/config/features.ts</code> to enable these
              once the backend changes are deployed.
            </p>
            <div className="space-y-2 text-sm">
              <FeatureRow
                label="WIDE multi-hosts selector"
                ready={WIDE_MULTI_HOSTS_BACKEND_READY}
              />
              <FeatureRow
                label="Multiple equality sets selector"
                ready={MULTI_EQUALITY_SETS_BACKEND_READY}
              />
              <FeatureRow label="Job scheduling" ready={JOB_SCHEDULING_BACKEND_READY} />
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Info className="h-3.5 w-3.5 text-muted-foreground" />
              About
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground space-y-1.5">
            <div>
              Automata frontend{" "}
              <span className="font-mono text-xs">v0.1</span>
            </div>
            <div>
              Configure routine forms in{" "}
              <code className="font-mono">public/routine-specs.json</code>.
            </div>
            <div>
              Backend API base: <code className="font-mono">/api</code>{" "}
              <span className="text-xs">(proxied to the Spring Boot app)</span>
            </div>
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={clearOpen}
        onOpenChange={setClearOpen}
        title="Clear activity log?"
        description="The Recent activity panel on the dashboard will be reset."
        destructive
        confirmLabel="Clear"
        onConfirm={() => {
          try {
            clearActivity();
            toast({ title: "Activity cleared", variant: "success" });
          } catch {
            toast({ title: "Could not clear", variant: "destructive" });
          }
        }}
      />
    </PageContainer>
  );
}

function ThemeButton({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center gap-2 rounded-md border px-3 py-2 text-sm transition-colors",
        active
          ? "border-foreground/30 bg-foreground/5"
          : "border-border hover:bg-accent/40",
      )}
    >
      <Icon className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

function FeatureRow({ label, ready }: { label: string; ready: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span>{label}</span>
      <span
        className={cn(
          "inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide",
          ready
            ? "border-success/30 bg-success/10 text-success"
            : "border-warning/30 bg-warning/10 text-warning",
        )}
      >
        <span
          className={cn(
            "inline-block h-1.5 w-1.5 rounded-full",
            ready ? "bg-success" : "bg-warning",
          )}
        />
        {ready ? "Backend ready" : "Backend stub"}
      </span>
    </div>
  );
}
