package com.calcvault.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.calcvault.CalcVaultApp
import com.calcvault.R
import com.calcvault.data.db.AppDatabase
import com.calcvault.server.EmbeddedServer
import kotlinx.coroutines.*

class ServerForegroundService : Service() {

    private var server: EmbeddedServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val TAG = "ServerService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        var isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                acquireWakeLock()
                startServer()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CalcVaultApp.CHANNEL_SERVER)
            .setContentTitle("Calculator")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(1, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CalcVault::ServerWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun startServer() {
        val messageDao = AppDatabase.getInstance(this).messageDao()

        server = EmbeddedServer(messageDao, port = 8443).apply {
            onMessageReceived = { _ ->
                serviceScope.launch {
                    showMessageNotification()
                }
            }
            start()
        }

        isRunning = true
        Log.d(TAG, "Embedded server started successfully")

        serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000)
                ensureServerRunning()
            }
        }
    }

    private fun ensureServerRunning() {
        if (server?.isRunning() != true) {
            Log.w(TAG, "Server died! Restarting...")
            startServer()
        }
    }

    private fun showMessageNotification() {
        val notification = NotificationCompat.Builder(this, CalcVaultApp.CHANNEL_MESSAGES)
            .setContentTitle("New update available")
            .setContentText("Tap to view")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        wakeLock?.release()
        serviceScope.cancel()
        isRunning = false
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
