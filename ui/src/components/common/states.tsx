import { Inbox, AlertCircle, Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

export function EmptyState({
  title,
  description,
  action,
  icon: Icon = Inbox,
  className,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  icon?: React.ComponentType<{ className?: string }>;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center justify-center py-16 px-4 text-center", className)}>
      <div className="rounded-full border border-border bg-muted/30 p-3 mb-3">
        <Icon className="h-5 w-5 text-muted-foreground" />
      </div>
      <div className="text-sm font-medium">{title}</div>
      {description && <div className="text-xs text-muted-foreground mt-1 max-w-sm">{description}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({
  title = "Something went wrong",
  description,
  action,
  className,
}: {
  title?: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center justify-center py-16 px-4 text-center", className)}>
      <div className="rounded-full border border-destructive/30 bg-destructive/10 p-3 mb-3">
        <AlertCircle className="h-5 w-5 text-destructive" />
      </div>
      <div className="text-sm font-medium">{title}</div>
      {description && (
        <div className="text-xs text-muted-foreground mt-1 max-w-md break-words">{description}</div>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Loader({ className, label }: { className?: string; label?: string }) {
  return (
    <div className={cn("flex items-center justify-center gap-2 py-8 text-xs text-muted-foreground", className)}>
      <Loader2 className="h-3.5 w-3.5 animate-spin" />
      {label && <span>{label}</span>}
    </div>
  );
}

export function InlineSpinner({ className }: { className?: string }) {
  return <Loader2 className={cn("h-3 w-3 animate-spin", className)} />;
}
