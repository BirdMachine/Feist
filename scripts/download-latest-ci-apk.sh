#!/usr/bin/env bash
set -euo pipefail

OWNER="${GITHUB_OWNER:-BirdMachine}"
REPO="${GITHUB_REPO:-Feist}"
FULL_REPO="$OWNER/$REPO"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/dist/github"

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "Authenticate first with: gh auth login" >&2
  exit 1
fi

RUN_ID="$(gh run list \
  --repo "$FULL_REPO" \
  --workflow android-debug-apk.yml \
  --branch main \
  --status success \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId // empty')"

if [[ -z "$RUN_ID" ]]; then
  echo "No successful Android debug APK workflow run found yet." >&2
  echo "See: https://github.com/$FULL_REPO/actions" >&2
  exit 1
fi

rm -rf "$DEST"
mkdir -p "$DEST"
gh run download "$RUN_ID" \
  --repo "$FULL_REPO" \
  --name Feist-debug-apk \
  --dir "$DEST"

echo "Downloaded latest CI APK artifact to:"
find "$DEST" -maxdepth 1 -type f -printf '  %p\n'
