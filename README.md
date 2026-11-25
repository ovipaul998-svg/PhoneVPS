# PhoneVPS

Standalone Android server dashboard. No Termux and no root required.

## Current build (v1.8)
- Python 3.11 embedded in the APK using Chaquopy.
- Built-in Python console.
- Managed background Python / Discord bot runner (`discord.py 2.5.2`, `aiohttp 3.10.10`).
- Android foreground service with persistent notification while hosting.
- Local Python HTTP server on 127.0.0.1:8080.
- Shared `/projects` workspace used by the file manager, terminal, web server, and bot runner.

## Build
GitHub Actions builds a debug APK on every push to main. Download the `PhoneVPS-debug-apk` artifact from the successful workflow run.

## Runtime notes
This version embeds Python. Node.js runtime support is a separate integration phase; it is not included yet. Full Ubuntu is not included.

Android foreground services improve background reliability but do not guarantee 24/7 uptime. Android battery restrictions, force-stop, thermal limits and low-memory conditions can still stop the app.

## Technology
- Android Kotlin
- Chaquopy 17.2.0 / Python 3.11
- WebView dashboard
- Android foreground service
