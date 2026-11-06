import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "@tanstack/react-router";
import { QueryClientProvider } from "@tanstack/react-query";
import { router } from "@/routes/router";
import { queryClient } from "@/lib/queryClient";
import { ToastProvider } from "@/components/ui/toast";
import { TooltipProvider } from "@/components/ui/primitives";
import { applyTheme, getStoredTheme } from "@/lib/utils";
import { loadRoutineSpecs } from "@/lib/routineSpec";
import "@/styles/globals.css";

// Apply the stored theme synchronously to avoid a flash.
applyTheme(getStoredTheme());

// Warm the routine-specs cache so the first run-job render is instant.
loadRoutineSpecs().catch(() => {
  // Non-fatal — the form will fall back to the Monaco JSON editor.
});

const root = document.getElementById("root");
if (!root) throw new Error("#root not found in index.html");

ReactDOM.createRoot(root).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <TooltipProvider delayDuration={200}>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </TooltipProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
