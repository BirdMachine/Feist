#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/build-debug-apk.sh"
if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not on PATH. In Android Studio: Tools > SDK Manager > SDK Tools > Android SDK Platform-Tools." >&2
  exit 2
fi
adb devices
adb install -r "$ROOT/dist/BirdiePhotoMaid-v0.2-debug.apk"
