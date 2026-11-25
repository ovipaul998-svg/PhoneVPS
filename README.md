# PhoneVPS

![Build PhoneVPS APK](https://github.com/ovipaul998-svg/PhoneVPS/actions/workflows/build-apk.yml/badge.svg)

Standalone Android server dashboard powered by **Rust Native Core** + **Python 3.11**. No Termux and no root required.

## Download
Download the latest signed release APK or standalone Rust CLI binary from [GitHub Releases](https://github.com/ovipaul998-svg/PhoneVPS/releases):
- `PhoneVPS-v2.2-release.apk` (Android APK with redesigned v2.2 UI, Rust Native Core, and Python 3.11)
- `PhoneVPS-v2.2-debug.apk` (Debug APK)
- `phonevps-cli-linux-x86_64` (Standalone pure-Rust HTTP server + shell CLI binary)

## Current build (v2.2)
- **Redesigned Mobile Console UI**: Sticky glassmorphic top bar, segmented navigation, terminal window with quick-command chips, dark/light theme variables, and color-coded status banners.
- **Selectable Runtime Engines**: Switch between **Rust Native Core** (`libphonevps_core.so` via JNI/`cargo-ndk`) and **Python 3.11** (Chaquopy `17.2.0`) directly in the dashboard.
- **Rust Native Core (`rust/`)**: Fast multi-threaded HTTP server and native shell commands (`pwd`, `ls`, `cd`, `mkdir`, `touch`, `cat`, `rm`, `echo`), plus a standalone `phonevps-cli` binary.
- **Python 3.11 & Bot Runner**: Bundled `discord.py 2.5.2`, `aiohttp 3.10.10`, `flask 3.0.3`, `requests 2.32.3`, `python-dotenv 1.0.1`, and `beautifulsoup4 4.12.3`.
- **Private Discord Token Storage & Auto-Restart**: Token saved outside `/projects` (injected as `DISCORD_TOKEN`) with optional crash auto-restart (5s backoff, up to 3 retries).
- **Configurable Port & Notifications**: Port `1024–65535` (default `8080`), Android foreground service (`specialUse`, `targetSdk 35`) with tap-to-open notification, and custom launcher icon.
- **Workspace & Terminal**: Shared `/projects` directory, native device file upload, and ↑/↓ terminal command history.

## Build
GitHub Actions runs `cargo test`, cross-compiles `rust/` with `cargo-ndk` for `arm64-v8a` and `armeabi-v7a`, builds `phonevps-cli` and both signed release and debug APKs, and publishes them to GitHub Releases.

## Runtime notes
Node.js runtime support and full Ubuntu (`apt`) are not included. Android foreground services improve background reliability but do not guarantee 24/7 uptime.

## Technology
- Rust (`phonevps_core` via `jni` + `cargo-ndk`)
- Android Kotlin (`compileSdk 35`, `targetSdk 35`)
- Chaquopy 17.2.0 / Python 3.11
- WebView dashboard
- Android foreground service (`specialUse`)
