package com.calcvault.call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import java.util.concurrent.CountDownLatch
import com.calcvault.utils.CVNotificationManager

/** Foreground-process/notification lifetime for the CallActivity-owned WebRTC call. */
class CallForegroundService : Service() {
    companion object {
        const val ACTION_START = "START_CALL"
        const val ACTION_STOP = "STOP_CALL"
        const val EXTRA_VIDEO = "call_video"
        const val EXTRA_CALL_ID = "call_id"
        const val ACTION_RESUME_CALL = "com.calcvault.call.RESUME"
        const val ACTION_END_CALL = "com.calcvault.call.END"
        const val NOTIF_ID = 2001

        private val startLock = Any()
        @Volatile private var foregroundActive = false
        private var pendingStart: CountDownLatch? = null

        fun prepareForegroundStart(): CountDownLatch = synchronized(startLock) {
            foregroundActive = false
            CountDownLatch(1).also { pendingStart = it }
        }

        fun reportForegroundStart(active: Boolean) = synchronized(startLock) {
            foregroundActive = active
            pendingStart?.countDown()
            pendingStart = null
        }

        fun isForegroundActive(): Boolean = foregroundActive
    }

    private lateinit var notifManager: CVNotificationManager

    override fun onCreate() {
        super.onCreate()
        notifManager = CVNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> runCatching {
                val isVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                val notification = notifManager.showOngoingCall(
                    callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty(),
                    isVideo = isVideo
                )
                val mediaTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    (if (isVideo) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
                ServiceCompat.startForeground(this, NOTIF_ID, notification, mediaTypes)
            }.onSuccess {
                reportForegroundStart(true)
            }.onFailure {
                // Do not keep a phantom ongoing-call notification if Android rejects
                // the media foreground-service type or notification.
                reportForegroundStart(false)
                stopCallForeground()
            }
            ACTION_STOP -> stopCallForeground()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopCallForeground() {
        reportForegroundStart(false)
        notifManager.cancelCall()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        reportForegroundStart(false)
        notifManager.cancelCall()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopCallForeground()
        super.onTaskRemoved(rootIntent)
    }
}
