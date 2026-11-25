package com.phonevps.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python
import java.io.File

class PhoneVPSService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                try {
                    if (Python.isStarted()) Python.getInstance().getModule("phonevps").callAttr("stop_bot")
                } catch (_: Exception) { }
                stopAllServers()
                running = false
                lastError = ""
                removeForeground()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val port = getConfiguredPort(this)
                val engine = getConfiguredEngine(this)
                activePort = port
                activeEngine = engine
                val notification = buildNotification(port, engine)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                running = false
                lastError = ""
                try {
                    val projectsDir = File(filesDir, "projects").apply { mkdirs() }
                    val result = if (engine == ENGINE_RUST && RustBridge.available) {
                        stopPythonServer()
                        RustBridge.nativeStartServer(projectsDir.absolutePath, port)
                    } else {
                        if (RustBridge.available) RustBridge.nativeStopServer()
                        if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
                        Python.getInstance().getModule("phonevps")
                            .callAttr("start_server", filesDir.absolutePath, port).toString()
                    }
                    if (result == "started" || result == "already running") {
                        running = true
                    } else {
                        throw IllegalStateException("Server did not start ($engine): $result")
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    running = false
                    removeForeground()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun stopAllServers() {
        if (RustBridge.available) {
            try { RustBridge.nativeStopServer() } catch (_: Exception) { }
        }
        stopPythonServer()
    }

    private fun stopPythonServer() {
        try {
            if (Python.isStarted()) Python.getInstance().getModule("phonevps").callAttr("stop_server")
        } catch (_: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PhoneVPS hosting", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(port: Int, engine: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = if (engine == ENGINE_RUST) "Rust" else "Python"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("PhoneVPS server is running")
            .setContentText("Hosting on port $port ($label engine)")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try {
            if (Python.isStarted()) Python.getInstance().getModule("phonevps").callAttr("stop_bot")
        } catch (_: Exception) { }
        stopAllServers()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.phonevps.app.START_HOSTING"
        const val ACTION_STOP = "com.phonevps.app.STOP_HOSTING"
        const val ENGINE_RUST = "rust"
        const val ENGINE_PYTHON = "python"
        const val DEFAULT_PORT = 8080
        private const val PORT_FILE = "server_port.txt"
        private const val ENGINE_FILE = "server_engine.txt"
        private const val CHANNEL_ID = "phonevps_hosting"
        private const val NOTIFICATION_ID = 4401
        @Volatile var activePort: Int = DEFAULT_PORT
        @Volatile var activeEngine: String = ENGINE_RUST
        @Volatile var running: Boolean = false
        @Volatile var lastError: String = ""

        fun getConfiguredPort(context: Context): Int = try {
            val file = File(context.filesDir, PORT_FILE)
            val parsed = if (file.isFile) file.readText().trim().toIntOrNull() else null
            if (parsed != null && parsed in 1024..65535) parsed else DEFAULT_PORT
        } catch (_: Exception) {
            DEFAULT_PORT
        }

        fun setConfiguredPort(context: Context, port: Int) {
            require(port in 1024..65535) { "Port must be between 1024 and 65535" }
            File(context.filesDir, PORT_FILE).writeText(port.toString())
        }

        fun getConfiguredEngine(context: Context): String = try {
            val file = File(context.filesDir, ENGINE_FILE)
            val value = if (file.isFile) file.readText().trim().lowercase() else ""
            when (value) {
                ENGINE_RUST -> if (RustBridge.available) ENGINE_RUST else ENGINE_PYTHON
                ENGINE_PYTHON -> ENGINE_PYTHON
                else -> if (RustBridge.available) ENGINE_RUST else ENGINE_PYTHON
            }
        } catch (_: Exception) {
            if (RustBridge.available) ENGINE_RUST else ENGINE_PYTHON
        }

        fun setConfiguredEngine(context: Context, engine: String) {
            val normalized = engine.trim().lowercase()
            require(normalized == ENGINE_RUST || normalized == ENGINE_PYTHON) { "Engine must be 'rust' or 'python'" }
            if (normalized == ENGINE_RUST && !RustBridge.available) {
                error("Rust native library is unavailable on this device")
            }
            File(context.filesDir, ENGINE_FILE).writeText(normalized)
        }
    }
}
