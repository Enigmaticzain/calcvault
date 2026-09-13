package com.calcvault.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.calcvault.ui.calculator.CalculatorActivity

/**
 * CVNotificationManager
 *
 * Handles all notifications for CalcVault with high privacy standards.
 * Merged from v3 and v4 improvements.
 */
@SuppressLint("MissingPermission")
class CVNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CALLS = "cv_calls"
        const val CHANNEL_SILENT = "cv_silent"

        const val ID_INCOMING_CALL = 1001
        const val ID_ONGOING_CALL = 1002
        const val ID_MESSAGE_DOT = 1003
        const val ID_USB_STATUS = 1004
        const val ID_SYNC_DONE = 1005
        const val ID_MISSED_CALL = 1006
    }

    private val notifManager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(false)
            enableLights(true)
            enableVibration(true)
            // Use setLockscreenVisibility for compatibility
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        }

        val silentChannel = NotificationChannel(
            CHANNEL_SILENT,
            "Background",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(listOf(callChannel, silentChannel))
    }

    // ─── Calls ────────────────────────────────────────────────────────────

    fun showIncomingCall(
        onAnswerIntent: PendingIntent? = null,
        onDeclineIntent: PendingIntent? = null
    ) {
        if (!canPostNotifications()) return
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CalculatorActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming call")
            .setContentText("•")
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)

        if (onAnswerIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_call, "Answer", onAnswerIntent)
        }
        if (onDeclineIntent != null) {
            builder.addAction(android.R.drawable.ic_delete, "Decline", onDeclineIntent)
        }

        notifManager.notify(ID_INCOMING_CALL, builder.build())
    }

    fun showOngoingCall(): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CalculatorActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Call in progress")
            .setContentText("•")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        if (canPostNotifications()) {
            notifManager.notify(ID_ONGOING_CALL, notification)
        }
        return notification
    }

    fun notifyMissedCall() {
        if (!canPostNotifications()) return
        val notif = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Missed call")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .build()
        notifManager.notify(ID_MISSED_CALL, notif)
    }

    fun cancelCall() {
        notifManager.cancel(ID_INCOMING_CALL)
        notifManager.cancel(ID_ONGOING_CALL)
    }

    // ─── Messages & Status ────────────────────────────────────────────────

    fun showMessageDot() {
        if (!canPostNotifications()) return
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CalculatorActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("•")
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        notifManager.notify(ID_MESSAGE_DOT, notif)
    }

    fun showUsbStatus(connected: Boolean) {
        if (!canPostNotifications()) return
        val title = if (connected) "Storage connected" else "Storage disconnected"
        val notif = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setTimeoutAfter(3000)
            .build()
        notifManager.notify(ID_USB_STATUS, notif)
    }

    fun cancelAll() = notifManager.cancelAll()

    private fun canPostNotifications(): Boolean {
        if (!notifManager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
