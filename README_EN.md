<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="112" alt="JM Downloader">
</p>

<h1 align="center">JM Downloader</h1>

<p align="center">
  <strong>JMComic / 18comic manga downloader for Android</strong><br>
  Paste an album ID → download in background → save as ZIP → read offline in the built-in viewer.
</p>

<p align="center">
  <a href="https://github.com/vvcxxc/jmDownload/releases/latest"><img alt="Download APK" src="https://img.shields.io/github/v/release/vvcxxc/jmDownload?label=Download%20APK&logo=android&logoColor=white"></a>
  <a href="https://github.com/vvcxxc/jmDownload/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/vvcxxc/jmDownload/total?label=downloads"></a>
  <a href="https://github.com/vvcxxc/jmDownload/stargazers"><img alt="Stars" src="https://img.shields.io/github/stars/vvcxxc/jmDownload?style=social"></a>
  <a href="https://github.com/vvcxxc/jmDownload/actions/workflows/android-release.yml"><img alt="Build" src="https://github.com/vvcxxc/jmDownload/actions/workflows/android-release.yml/badge.svg"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/vvcxxc/jmDownload/releases/latest"><b>⬇️ Download APK</b></a>
  &nbsp;·&nbsp;
  <a href="README.md">中文 README</a>
  &nbsp;·&nbsp;
  <a href="CONTRIBUTING.md">Contributing</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/vvcxxc/jmDownload/issues">Issues</a>
</p>

> This is an **Android app**, not a QQ bot. The `main` branch contains the Android downloader; the original QQ bot code is kept on the `qqbot` branch.

## ✨ Features

- **Album ID input**: accepts plain IDs, `JM350234`, links, comma/newline-separated lists, and text containing IDs.
- **Background downloads**: foreground service + progress notification; downloads continue while the app is in the background.
- **ZIP packaging**: each album/chapter is saved as a ZIP file to a folder you choose.
- **Multi-chapter albums**: choose exactly which chapters to download; group chapters in the task list with expand/collapse and an internal scroll area.
- **Search**: search by title/author or JM ID, with cover thumbnails, multi-select and batch download; queued/downloading results show a Cancel button to withdraw unfinished chapters.
- **Task management**: All / Unread / Downloaded / Downloading / Failed tabs, retry, pause/resume, batch delete and move.
- **Offline reader**: paged and webtoon modes, thumbnails, page slider, jump-to-page, long-press to save an image, resume reading position.
- **Local import**: import existing ZIP files from device storage.
- **Custom UI**: theme color, background image with manual crop/position selection, and logs.

## 📥 Install

1. Open [Releases](https://github.com/vvcxxc/jmDownload/releases/latest).
2. Download `JMComic-<version>.apk`.
3. Allow installation from unknown sources and install.

Android 8.0+ is supported. The default save folder on Android 10+ is `Downloads/JMComic`; Android 9 and below require choosing a writable folder in Settings.

## 🚀 Quick start

1. Paste an album ID such as `350234` or `JM350234`.
2. Tap **Add**.
3. For a multi-chapter album, select the chapters in the confirmation dialog.
4. Wait for the downloads to finish, then open a completed task in the built-in reader, or take the ZIP from the save folder.

## 🧱 Build

Requirements:

- JDK 17+
- Android SDK 35
- Use the bundled `gradlew.bat` / `gradlew`

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
gradlew.bat :app:testDebugUnitTest
```

Outputs:

```text
app\build\outputs\apk\debug\app-debug.apk
app\build\outputs\apk\release\app-release.apk
```

## 🛠️ Tech stack

- Kotlin + Jetpack Compose + Material 3
- `minSdk 26`, `compileSdk/targetSdk 35`
- [jmcomic-android-support](https://central.sonatype.com/artifact/io.github.jukomu/jmcomic-android-support)
- Coil, Foreground Service, SharedPreferences, `org.json`
- R8 + resource shrinking

## ⚠️ Disclaimer

This project is for personal study, backup and reading of content you are legally allowed to access. Follow the laws and platform rules in your region. Do not redistribute infringing material. You are responsible for how you use this project.

## 🔎 Keywords

JMComic downloader, 18comic downloader, Android manga downloader, doujin downloader, multi-chapter downloader, offline comic reader, Kotlin, Jetpack Compose.

⭐ If this project helps you, please give it a **Star** so more people can find it.
