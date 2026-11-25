# PhoneVPS

![Build PhoneVPS APK](https://github.com/ovipaul998-svg/PhoneVPS/actions/workflows/build-apk.yml/badge.svg)

Standalone Android server dashboard. No Termux and no root required.

## Download
Download the latest signed release APK directly from [GitHub Releases](https://github.com/ovipaul998-svg/PhoneVPS/releases) (`PhoneVPS-v2.0-release.apk`), or download `PhoneVPS-APKs` from the latest [GitHub Actions](https://github.com/ovipaul998-svg/PhoneVPS/actions) run.

## Current build (v2.0)
- Python 3.11 embedded in the APK using Chaquopy (`17.2.0`).
- Bundled Python packages: `discord.py 2.5.2`, `aiohttp 3.10.10`, `flask 3.0.3`, `requests 2.32.3`, `python-dotenv 1.0.1`, `beautifulsoup4 4.12.3`.
- Built-in Python console with terminal command history (↑/↓).
- Managed background Python / Discord bot runner with private token storage outside `/projects` (`DISCORD_TOKEN`) and optional crash auto-restart (5s backoff, up to 3 retries).
- Configurable HTTP server port (`1024–65535`, default `8080`) with Android foreground service notification tap-to-open (`targetSdk 35`) and custom app icon.
- Shared `/projects` workspace with file editor and native device file upload.
- Dark and light theme toggle.

## Build
GitHub Actions builds both signed release and debug APKs on every push to `main` or version tag (`v*`), uploads them as `PhoneVPS-APKs`, and publishes them to GitHub Releases.

## Runtime notes
This version embeds Python. Node.js runtime support is a separate integration phase; it is not included yet. Full Ubuntu is not included.

Android foreground services improve background reliability but do not guarantee 24/7 uptime. Android battery restrictions, force-stop, thermal limits and low-memory conditions can still stop the app.

## Technology
- Android Kotlin (`compileSdk 35`, `targetSdk 35`)
- Chaquopy 17.2.0 / Python 3.11
- WebView dashboard
- Android foreground service (`specialUse`)
