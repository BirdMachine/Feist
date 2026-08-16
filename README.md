# Feist / Birdie Photo Maid 🦅🧹✨

A local-first Android photo-library organizer with a kestrel-coded maid mascot.

This repository is the development home for **Birdie Photo Maid** (working project version v0.2), aimed first at Kestrel — Galaxy S21 Ultra 5G / SM-G998U1.

The current build is deliberately read-only: it can inspect, hash, classify, and review media, but cannot delete, move, rename, or upload anything.

## Current features

- Android `MediaStore` image scanning.
- Android 14+ full vs selected-photo access handling.
- Screenshot detection and conservative source-app classification.
- SHA-256 exact duplicate detection.
- Conservative screenshot near-duplicate detection with perceptual dHash.
- Thumbnail review screens for duplicate groups.
- Screenshot browsing grouped by inferred source app.
- Kestrel-maid mascot state framework.
- Four-edge damaged-display safe zones, including Kestrel's default lower 34% dead zone.
- No destructive cleanup actions yet.

## Build

The project uses Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13, compile/target SDK 36, min SDK 29, and Build Tools 35.0.0.

GitHub Actions builds a debug APK on pushes to `main`, pull requests targeting `main`, or manual workflow dispatch. The resulting artifact is named **BirdiePhotoMaid-debug-apk**.

Once the full project import lands, see `.github/workflows/android-debug-apk.yml` for the CI build and `scripts/download-latest-ci-apk.sh` for the Mallard-side artifact fetch helper.
