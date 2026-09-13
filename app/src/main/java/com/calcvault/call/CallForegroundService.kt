package com.calcvault.call

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.CVNotificationManager

/**
 * CallForegroundService
 *
 * Required on Android 8+ to keep an active VoIP call alive
 * when the app is moved to the background.
 *
 * Lifecycle:
 *  - Started by CallActivity when call becomes ACTIVE
 *  - Stopped when call ends or vault locks
 *  - Shows ongoing notification (silent, no content)
 *
 * The service holds a reference to CallEngine so audio
 * capture/playback continues uninterrupted.
 *
 * Usage:
 *   // Start
 *   Intent(this, CallForegroundService::class.java).also {
 *       it.action = ACTION_START
 *       startForegroundService(it)
 *   }
 *   // Stop
 *   Intent(this, CallForegroundService::class.java).also {
 *       it.action = ACTION_STOP
 *       startService(it)
 *   }
 */
class CallForegroundService : Service() {

    companion object {
        const val ACTION_START = "START_CALL"
        const val ACTION_STOP = "STOP_CALL"
        const val NOTIF_ID = 2001
    }

    private lateinit var callEngine: CallEngine
    private lateinit var notifManager: CVNotificationManager
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getCallEngine(): CallEngine = callEngine
    }

    override fun onCreate() {
        super.onCreate()
        notifManager = CVNotificationManager(this)

        val storageEngine = USBStorageEngine.getInstance(this)
        val messageDB = AppendOnlyMessageDB(storageEngine)
        callEngine = CallEngine(this, storageEngine, messageDB)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Promote to foreground with an ongoing call notification
                val notification = notifManager.showOngoingCall()
                startForeground(NOTIF_ID, notification)
            }
            ACTION_STOP -> {
                callEngine.endCall()
                notifManager.cancelCall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY // don't restart if killed — security
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        callEngine.endCall()
        notifManager.cancelCall()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App swiped from recents — end call immediately for privacy
        callEngine.endCall()
        stopSelf()
    }
}
