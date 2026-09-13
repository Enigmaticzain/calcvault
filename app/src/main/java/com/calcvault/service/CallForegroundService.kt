package com.calcvault.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.calcvault.CalcVaultApp
import com.calcvault.call.CallEngine
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.storage.USBStorageEngine
import com.calcvault.ui.call.CallActivity

/**
 * CallForegroundService
 *
 * Keeps the call alive when the screen turns off.
 * Android requires a foreground service for ongoing audio/video.
 *
 * Notification:
 *  - Minimal — shows only "Call in progress"
 *  - No content preview
 *  - Tap → returns to CallActivity
 *  - VISIBILITY_SECRET → hidden on lock screen
 *
 * Lifecycle:
 *  Started: when CallEngine transitions to ACTIVE
 *  Stopped: when call ends or vault locks
 */
class CallForegroundService : Service() {

    companion object {
        const val ACTION_START = "cv.call.START"
        const val ACTION_STOP = "cv.call.STOP"
        const val NOTIF_ID = 1001
    }

    private val binder = CallBinder()
    inner class CallBinder : Binder() {
        fun getService(): CallForegroundService = this@CallForegroundService
    }

    private lateinit var callEngine: CallEngine
    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB

    override fun onCreate() {
        super.onCreate()
        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        callEngine = CallEngine(this, storageEngine, messageDB)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCallForeground()
            ACTION_STOP -> {
                callEngine.endCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY // don't restart automatically
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startCallForeground() {
        val tapIntent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CalcVaultApp.CHANNEL_CALLS)
            .setContentTitle("Call in progress")
            .setContentText("") // No content — privacy
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // hidden on lock screen
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(
                android.R.drawable.ic_delete,
                "End",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, CallForegroundService::class.java).apply { action = ACTION_STOP },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        callEngine.endCall()
    }
}
