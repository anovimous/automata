import * as React from "react";
import Editor from "@monaco-editor/react";

interface MonacoJsonEditorProps {
  value: string;
  onChange: (next: string) => void;
  height?: string;
  readOnly?: boolean;
}

/**
 * JSON code editor with line numbers and syntax highlighting.
 * Used as the fallback for routines that don't have a spec in routine-specs.json.
 */
export function MonacoJsonEditor({
  value,
  onChange,
  height = "240px",
  readOnly,
}: MonacoJsonEditorProps) {
  const isDark =
    typeof document !== "undefined" &&
    document.documentElement.classList.contains("dark");

  return (
    <div className="rounded-md overflow-hidden border border-border">
      <Editor
        height={height}
        defaultLanguage="json"
        value={value}
        onChange={(v) => onChange(v ?? "")}
        theme={isDark ? "vs-dark" : "light"}
        options={{
          minimap: { enabled: false },
          fontSize: 12,
          fontFamily: "'JetBrains Mono', monospace",
          scrollBeyondLastLine: false,
          lineNumbers: "on",
          tabSize: 2,
          readOnly,
          renderLineHighlight: "all",
          padding: { top: 8, bottom: 8 },
          scrollbar: { vertical: "auto", horizontal: "auto" },
        }}
      />
    </div>
  );
}
