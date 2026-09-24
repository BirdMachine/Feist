# Feist / Birdie Photo Maid 🦅🧹✨

A local-first Android photo-library organizer with a kestrel-coded maid mascot.

This repository is the development home for **Birdie Photo Maid** (testing version v0.3.2), aimed first at Kestrel — Galaxy S21 Ultra 5G / SM-G998U1.

The [product and character design spec](docs/product-character-design.md) records the broader direction.

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
- Move selected local images to Android Trash by default, or explicitly request permanent deletion after an in-app warning and Android confirmation. Feist blocks removing every copy in any identified exact duplicate group.

## Build

The project uses Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13, compile/target SDK 36, min SDK 29, and Build Tools 35.0.0.

Download the APK from [the latest testing release](https://github.com/BirdMachine/Feist/releases/tag/feist-latest) after the main-branch build finishes. On Android, open the downloaded APK and allow installation from your browser or file manager when prompted. Install the cloud app you want to use first.

### Stable debug signing

As with Keywi, Gradle accepts a stable debug keystore and credentials through environment variables, and CI assigns a monotonically increasing version code. Unlike Keywi's repository-published test key, Feist's key and passwords are held in GitHub Actions secrets, never in the repository. On Mallard, clone this repository and run `bash scripts/setup-signing.sh` after `gh auth login`. The script creates a key under `~/.local/share/feist-signing/` (or `$XDG_DATA_HOME/feist-signing/`), sets four repository secrets using GitHub CLI, and reuses the same key on subsequent runs. Back up **both** the keystore and `credentials.env` securely; losing or replacing them prevents update-in-place. The first stable-signed APK will require uninstalling any earlier runner-signed Feist debug APK. Uninstalling Feist clears its scan state and settings but does not delete photos.

Push builds of `main` require all four signing secrets and will not replace the release APK if any are missing. Pull-request builds without secrets use a disposable key only to check compilation. After setup, dispatch the Android debug APK workflow on `main` to publish a stable-signed APK.

To clean up: scan with full photo access, review an exact group or screenshot source, check specific images, tap **Back up selected with cloud app**, complete the upload in that app, verify the files there, return to Feist, then tap **Move selected to Trash…**. Empty Trash in your gallery to reclaim the space. **Permanently delete selected…** is also available with a separate warning. Rescan afterward. The app currently indexes images only, not videos or other downloads. It displays the first 50 duplicate groups and the first 60 screenshots per source, with a button for more screenshots.

GitHub Actions builds a debug APK on pushes to `main`, pull requests targeting `main`, or manual workflow dispatch. A successful push to `main` updates the downloadable testing release. The workflow artifact is named **Feist-debug-apk** and contains:

- `Feist-BirdiePhotoMaid-v0.3.2-debug.apk`
- `Feist-BirdiePhotoMaid-v0.3.2-debug.apk.sha256`

See `.github/workflows/android-debug-apk.yml` for the CI build and `scripts/download-latest-ci-apk.sh` for the Mallard-side helper that downloads the latest successful APK artifact into `dist/github/`.
