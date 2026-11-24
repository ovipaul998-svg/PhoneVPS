# PhoneVPS

PhoneVPS is an Android dashboard that can send commands to a local Termux installation.

## Current functionality
- Android WebView dashboard
- Termux RUN_COMMAND bridge
- Runtime installation command for Python and Node.js
- Start/stop a sample Python HTTP server
- Send custom commands to Termux in background mode

## Requirements
- Android device
- Termux installed and opened at least once
- Termux setting `allow-external-apps = true`
- Grant PhoneVPS the Android permission **Run commands in Termux environment**

## Termux setup
In Termux, run:

```sh
mkdir -p ~/.termux
echo 'allow-external-apps = true' >> ~/.termux/termux.properties
```

Restart Termux after changing the setting. If the property already exists, edit it rather than adding duplicate entries.

Then open Android Settings → Apps → PhoneVPS → Permissions → Additional permissions and allow **Run commands in Termux environment**.

For better background reliability, disable battery optimization for Termux and use `termux-wake-lock`. For boot startup, install Termux:Boot, open it once, and configure a script under `~/.termux/boot/`.

## Important limitations
PhoneVPS does not create a real virtual machine. It uses Termux's Android Linux userland. Android can still stop background tasks due to battery management, memory pressure, thermal limits, force-stop, or network changes. This is not guaranteed 24/7 VPS hosting.

The custom command runner sends commands to Termux in background mode. View command output in Termux or redirect it to log files.
