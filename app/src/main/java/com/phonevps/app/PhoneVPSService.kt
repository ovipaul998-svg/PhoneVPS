package com.phonevps.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.Python

class PhoneVPSService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPythonServer()
                running = false
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                try {
                    if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
                    Python.getInstance().getModule("phonevps")
                        .callAttr("start_server", filesDir.absolutePath, SERVER_PORT)
                    running = true
                } catch (e: Exception) {
                    running = false
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        return START_STICKY
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

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("PhoneVPS server is running")
            .setContentText("Local Python hosting on port $SERVER_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopPythonServer()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.phonevps.app.START_HOSTING"
        const val ACTION_STOP = "com.phonevps.app.STOP_HOSTING"
        const val SERVER_PORT = 8080
        private const val CHANNEL_ID = "phonevps_hosting"
        private const val NOTIFICATION_ID = 4401
        @Volatile var running: Boolean = false
    }
}
