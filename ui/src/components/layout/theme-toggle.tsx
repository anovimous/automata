import * as React from "react";
import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { applyTheme, getStoredTheme, setStoredTheme, type ThemeMode } from "@/lib/utils";

export function ThemeToggle() {
  const [mode, setMode] = React.useState<ThemeMode>(() => getStoredTheme());

  React.useEffect(() => {
    applyTheme(mode);
  }, [mode]);

  function toggle() {
    const next: ThemeMode = mode === "dark" ? "light" : "dark";
    setMode(next);
    setStoredTheme(next);
  }

  return (
    <Button
      variant="ghost"
      size="icon-sm"
      onClick={toggle}
      aria-label={`Switch to ${mode === "dark" ? "light" : "dark"} theme`}
    >
      {mode === "dark" ? <Sun className="h-3.5 w-3.5" /> : <Moon className="h-3.5 w-3.5" />}
    </Button>
  );
}
