# PhoneVPS

![Build PhoneVPS APK](https://github.com/ovipaul998-svg/PhoneVPS/actions/workflows/build-apk.yml/badge.svg)

Standalone Android server dashboard. No Termux and no root required.

## Current build (v1.9)
- Python 3.11 embedded in the APK using Chaquopy.
- Built-in Python console with terminal command history (↑/↓).
- Managed background Python / Discord bot runner (`discord.py 2.5.2`, `aiohttp 3.10.10`).
- Private Discord token storage outside `/projects` (injected as `DISCORD_TOKEN`) and optional crash auto-restart (5s backoff, up to 3 retries).
- Configurable HTTP server port (`1024–65535`, default `8080`) with Android foreground service notification tap-to-open (`targetSdk 35`).
- Shared `/projects` workspace with file editor and native device file upload.
- Dark and light theme toggle.

## Build
GitHub Actions builds a debug APK on every push to main. Download the `PhoneVPS-debug-apk` artifact from the successful workflow run.

## Runtime notes
This version embeds Python. Node.js runtime support is a separate integration phase; it is not included yet. Full Ubuntu is not included.

Android foreground services improve background reliability but do not guarantee 24/7 uptime. Android battery restrictions, force-stop, thermal limits and low-memory conditions can still stop the app.

## Technology
- Android Kotlin (`compileSdk 35`, `targetSdk 35`)
- Chaquopy 17.2.0 / Python 3.11
- WebView dashboard
- Android foreground service (`specialUse`)
