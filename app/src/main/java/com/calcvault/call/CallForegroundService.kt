package com.calcvault.call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.calcvault.utils.CVNotificationManager

/** Foreground-process/notification lifetime for the CallActivity-owned WebRTC call. */
class CallForegroundService : Service() {
    companion object {
        const val ACTION_START = "START_CALL"
        const val ACTION_STOP = "STOP_CALL"
        const val EXTRA_VIDEO = "call_video"
        const val NOTIF_ID = 2001
    }

    private lateinit var notifManager: CVNotificationManager

    override fun onCreate() {
        super.onCreate()
        notifManager = CVNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = notifManager.showOngoingCall()
                val mediaTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    (if (intent.getBooleanExtra(EXTRA_VIDEO, false)) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        0
                    })
                ServiceCompat.startForeground(this, NOTIF_ID, notification, mediaTypes)
            }
            ACTION_STOP -> stopCallForeground()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopCallForeground() {
        notifManager.cancelCall()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        notifManager.cancelCall()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopCallForeground()
        super.onTaskRemoved(rootIntent)
    }
}
