#!/usr/bin/env bash
# Uploads every regular file of a directory (recursively) as a wordlist through POST /api/wordlists.
# Usage: scripts/import-wordlists.sh <directory> [base-url]   (base-url defaults to http://localhost:8080)
# The name of a wordlist is the path of its file relative to the directory. Its descriptions, which the
# core requires, are read from the sidecar file <file>.meta.json:
#   {"smallDescription": "...", "payloadContentDescription": "..."}
# (sidecar files are not uploaded). A file that is already a wordlist (409) is skipped; any other
# failure (a missing sidecar is a 400 of the core) is reported with the core's message and the script
# goes on.
set -u

dir="${1:?usage: $0 <directory> [base-url]}"
base="${2:-http://localhost:8080}"

if [ ! -d "$dir" ]; then
  echo "not a directory: $dir" >&2
  exit 2
fi

dir="$(cd "$dir" && pwd)"
created=0
skipped=0
failed=0

while IFS= read -r -d '' file; do
  name="${file#"$dir"/}"
  metadata="$(python3 - "$name" "${file}.meta.json" <<'PY'
import json, os, sys
name, sidecar = sys.argv[1], sys.argv[2]
meta = {}
if os.path.isfile(sidecar):
    with open(sidecar, encoding="utf-8") as f:
        meta = json.load(f)
print(json.dumps({
    "name": name,
    "smallDescription": meta.get("smallDescription"),
    "payloadContentDescription": meta.get("payloadContentDescription"),
}))
PY
)" || { failed=$((failed + 1)); echo "FAILED   $name (unreadable ${name}.meta.json)" >&2; continue; }
  response="$(mktemp)"
  status="$(curl -s -o "$response" -w '%{http_code}' \
    -F "file=@${file}" -F "metadata=${metadata};type=application/json" "${base}/api/wordlists")"
  case "$status" in
    201) created=$((created + 1)); echo "created  $name" ;;
    409) skipped=$((skipped + 1)); echo "skipped  $name (already a wordlist)" ;;
    *) failed=$((failed + 1)); echo "FAILED   $name (HTTP $status: $(head -c 300 "$response"))" >&2 ;;
  esac
  rm -f "$response"
done < <(find "$dir" -type f ! -name '*.meta.json' -print0 | sort -z)

echo "created: $created, skipped: $skipped, failed: $failed"
[ "$failed" -eq 0 ]
