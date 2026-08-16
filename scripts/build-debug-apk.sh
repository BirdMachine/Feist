#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
./gradlew --no-daemon assembleDebug
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/BirdiePhotoMaid-v0.2-debug.apk
echo
echo "Built: $ROOT/dist/BirdiePhotoMaid-v0.2-debug.apk"
