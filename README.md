# Feist / Birdie Photo Maid 🦅🧹✨

A local-first Android photo-library organizer with a kestrel-coded maid mascot.

This repository is the development home for **Birdie Photo Maid** (testing version v0.3), aimed first at Kestrel — Galaxy S21 Ultra 5G / SM-G998U1.

The current build can inspect, share selected images to an installed cloud app, and request local deletion through Android's confirmation dialog. Feist does not verify cloud uploads; check the cloud copy before deletion.

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
- Select individual images in duplicate groups and screenshot source lists.
- Share selected images via Android's chooser to a cloud app such as Drive.
- Delete selected local images after an in-app warning and Android confirmation. Feist blocks deleting every copy in any identified exact duplicate group.

## Build

The project uses Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13, compile/target SDK 36, min SDK 29, and Build Tools 35.0.0.

Download the APK from [the latest testing release](https://github.com/BirdMachine/Feist/releases/tag/feist-latest) after the main-branch build finishes. On Android, open the downloaded APK and allow installation from your browser or file manager when prompted. Install the cloud app you want to use first. Feist's debug APK is a testing build; CI currently creates a fresh debug signing key each run, so updating to a newer APK may require uninstalling the old test build first. Uninstalling clears Feist's scan state and preferences, but does not delete your photos.

To clean up: scan with full photo access, review an exact group or screenshot source, check specific images, tap **Back up selected with cloud app**, complete the upload in that app, verify the files there, return to Feist, then tap **Delete selected locally…**. Rescan afterward. The app currently indexes images only, not videos or other downloads. It displays the first 50 duplicate groups and the first 60 screenshots per source, with a button for more screenshots.

GitHub Actions builds a debug APK on pushes to `main`, pull requests targeting `main`, or manual workflow dispatch. A successful push to `main` updates the downloadable testing release. The workflow artifact is named **Feist-debug-apk** and contains:

- `Feist-BirdiePhotoMaid-v0.3-debug.apk`
- `Feist-BirdiePhotoMaid-v0.3-debug.apk.sha256`

See `.github/workflows/android-debug-apk.yml` for the CI build and `scripts/download-latest-ci-apk.sh` for the Mallard-side helper that downloads the latest successful APK artifact into `dist/github/`.
