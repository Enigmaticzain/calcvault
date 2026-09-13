package com.calcvault.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.calcvault.CalcVaultApp
import com.calcvault.ui.calculator.CalculatorActivity

/**
 * CVNotificationManager
 *
 * All notifications sent by CalcVault go through here.
 *
 * Privacy rules enforced:
 *  - VISIBILITY_SECRET on all notifications (hidden on lock screen)
 *  - No message content ever shown in notification body
 *  - Silent by default (no sound, no vibration)
 *  - No badge count on app icon
 *  - Tap → opens Calculator (not vault directly)
 *
 * Notification types:
 *  - Incoming message (silent, no content)
 *  - Incoming call (high priority — shows heads-up)
 *  - Missed call
 *  - Sync complete
 *  - USB events
 */
class CVNotificationManager(private val context: Context) {

    companion object {
        private const val ID_MESSAGE     = 2001
        private const val ID_CALL        = 2002
        private const val ID_MISSED_CALL = 2003
        private const val ID_SYNC        = 2004
        private const val ID_USB         = 2005
    }

    private val nm = NotificationManagerCompat.from(context)

    // ─── Incoming Message ─────────────────────────────────────────────────────

    /**
     * Show a silent notification — no text, no sender, just a dot.
     * Tapping opens the Calculator (user then unlocks normally).
     */
    fun notifyNewMessage() {
        if (!nm.areNotificationsEnabled()) return

        val notif = build(CalcVaultApp.CHANNEL_SILENT)
            .setContentTitle("•")                     // Minimal — no sender name
            .setContentText("")                        // No message preview ever
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .build()

        nm.notify(ID_MESSAGE, notif)
    }

    // ─── Incoming Call ────────────────────────────────────────────────────────

    /**
     * High-priority notification for incoming call.
     * Shows a heads-up with Answer / Decline actions.
     * Content hidden on lock screen.
     */
    fun notifyIncomingCall(
        onAnswerIntent : PendingIntent,
        onDeclineIntent: PendingIntent
    ) {
        if (!nm.areNotificationsEnabled()) return

        val notif = build(CalcVaultApp.CHANNEL_CALLS)
            .setContentTitle("Incoming call")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(false)
            .addAction(android.R.drawable.ic_menu_call,    "Answer",  onAnswerIntent)
            .addAction(android.R.drawable.ic_delete,       "Decline", onDeclineIntent)
            .build()

        nm.notify(ID_CALL, notif)
    }

    fun dismissCallNotification() {
        nm.cancel(ID_CALL)
    }

    // ─── Missed Call ──────────────────────────────────────────────────────────

    fun notifyMissedCall() {
        val notif = build(CalcVaultApp.CHANNEL_SILENT)
            .setContentTitle("Missed call")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .build()

        nm.notify(ID_MISSED_CALL, notif)
    }

    // ─── Sync Complete ────────────────────────────────────────────────────────

    fun notifySyncComplete(sent: Int, received: Int) {
        val notif = build(CalcVaultApp.CHANNEL_SILENT)
            .setContentTitle("Sync complete")
            .setContentText("")                        // Don't reveal record counts
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .build()

        nm.notify(ID_SYNC, notif)
    }

    // ─── USB Events ───────────────────────────────────────────────────────────

    fun notifyUsbAttached() {
        val notif = build(CalcVaultApp.CHANNEL_SILENT)
            .setContentTitle("Storage connected")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setTimeoutAfter(3_000)                   // auto-dismiss after 3s
            .build()

        nm.notify(ID_USB, notif)
    }

    fun notifyUsbRemoved() {
        val notif = build(CalcVaultApp.CHANNEL_SILENT)
            .setContentTitle("Storage disconnected — vault locked")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .build()

        nm.notify(ID_USB, notif)
    }

    // ─── Dismiss All ──────────────────────────────────────────────────────────

    fun dismissAll() {
        nm.cancelAll()
    }

    // ─── Builder Helper ───────────────────────────────────────────────────────

    private fun build(channel: String): NotificationCompat.Builder {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, CalculatorActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channel)
            .setContentIntent(tapIntent)
            .setNumber(0)                              // No badge count
            .setOnlyAlertOnce(true)
    }
}
