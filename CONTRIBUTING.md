# Contributing to JM Downloader

Thanks for helping improve the project.

## Before opening an issue

- Search existing issues first.
- Make sure you are using the latest APK from [Releases](https://github.com/vvcxxc/jmDownload/releases/latest).
- For a download failure, include the app version, Android version, device model, JM ID, and the relevant log copied from **Settings → Logs**.
- Do not upload explicit screenshots. Crop or redact sensitive content.

## Development

Requirements:

- JDK 17+
- Android SDK 35
- Windows, macOS or Linux

Build and test:

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

Release build:

```bat
gradlew.bat :app:assembleRelease
```

## Pull requests

- Keep changes focused; avoid unrelated formatting-only edits.
- Follow the existing Kotlin/Compose style and naming conventions.
- Add or update unit tests when changing parsing, grouping, file naming or task-state logic.
- Describe the user-visible change and how you tested it.
- Do not commit generated APKs, build directories, logs, keystores or local SDK paths.

## Code layout

```text
app/src/main/java/com/jmdl/
├── ui/        Compose screens and UI components
├── data/      Task model, repository and settings
├── download/  Foreground download service and storage helpers
├── engine/    jmcomic library wrapper
├── core/      ID parsing, page ordering and filename rules
└── util/      Logging and cover cache
```

## Testing checklist

- `:app:testDebugUnitTest` passes.
- `:app:assembleDebug` passes.
- Multi-chapter albums: chapter selection, per-chapter ZIPs, expand/collapse.
- Search: title/author and JM ID modes, cover thumbnails, multi-select.
- Reader: paged/webtoon modes, resume position, save image.
- Background: manual crop, fill/fit modes, rotation/tablet taskbar insets.

