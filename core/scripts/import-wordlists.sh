#!/usr/bin/env bash
# Uploads every regular file of a directory (recursively) as a wordlist through POST /api/wordlists.
# Usage: scripts/import-wordlists.sh <directory> [base-url]   (base-url defaults to http://localhost:8080)
# The name of a wordlist is the path of its file relative to the directory. A file that is already a
# wordlist (409) is skipped; any other failure is reported and the script goes on.
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
  metadata="$(printf '{"name":%s}' "$(printf '%s' "$name" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')")"
  status="$(curl -s -o /dev/null -w '%{http_code}' \
    -F "file=@${file}" -F "metadata=${metadata};type=application/json" "${base}/api/wordlists")"
  case "$status" in
    201) created=$((created + 1)); echo "created  $name" ;;
    409) skipped=$((skipped + 1)); echo "skipped  $name (already a wordlist)" ;;
    *) failed=$((failed + 1)); echo "FAILED   $name (HTTP $status)" >&2 ;;
  esac
done < <(find "$dir" -type f -print0 | sort -z)

echo "created: $created, skipped: $skipped, failed: $failed"
[ "$failed" -eq 0 ]
