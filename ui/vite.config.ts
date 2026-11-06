import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// Frontend talks to /api on its own origin.
// - In production (Docker): nginx proxies /api -> http://app:8080
// - In development (vite dev server): the proxy below forwards /api -> http://localhost:8080
//   (override by setting VITE_DEV_API_TARGET in .env.local)
const DEV_API_TARGET = process.env.VITE_DEV_API_TARGET || "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: DEV_API_TARGET,
        changeOrigin: true,
      },
    },
  },
  build: {
    target: "es2022",
    sourcemap: false,
  },
});
