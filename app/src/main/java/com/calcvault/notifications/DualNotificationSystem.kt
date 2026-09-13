package com.calcvault.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.chat.ChatActivity
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Dual Notification System
 * * Provides Standard and Stealth modes for privacy-preserving notifications.
 * Stealth mode hides message content while allowing partner recognition via private cues.
 */

enum class NotificationMode {
    STANDARD, // Full message preview
    STEALTH // Content-hidden with private cue
}

data class NotificationConfig(
    val mode: NotificationMode = NotificationMode.STANDARD,
    val privateCode: String = "", // User-defined code (e.g., "7226")
    val showCodeOnLockScreen: Boolean = true,
    val enableUrgentAlerts: Boolean = true,
    val urgentVibrationPattern: LongArray = longArrayOf(0, 500, 200, 500),
    val urgentSoundUri: String? = null
)

data class NotificationPayload(
    val messageId: Long,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isUrgent: Boolean = false,
    val type: Int = 10 // MSG_TEXT
)

class DualNotificationSystem(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val configs = ConcurrentHashMap<String, NotificationConfig>()
    private val notificationHistory = ConcurrentHashMap<Long, NotificationPayload>()

    companion object {
        private const val CHANNEL_STANDARD = "calcvault_standard"
        private const val CHANNEL_STEALTH = "calcvault_stealth"
        private const val CHANNEL_URGENT = "calcvault_urgent"
        private const val CONFIG_KEY_PREFIX = "notif/config/"
        private const val NOTIFICATION_ID_BASE = 10000
        private val STEALTH_GENERIC_TEXTS = arrayOf(
            "New update",
            "You have a notification",
            "New message",
            "Attention required"
        )
    }

    init {
        createNotificationChannels()
        loadConfigs()
    }

    // ── Notification Channels ──────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Standard Channel
            val standardChannel = NotificationChannel(
                CHANNEL_STANDARD,
                "Standard Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Regular message notifications"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(standardChannel)

            // Stealth Channel
            val stealthChannel = NotificationChannel(
                CHANNEL_STEALTH,
                "Stealth Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Privacy-preserving notifications"
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(stealthChannel)

            // Urgent Channel
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT,
                "Urgent Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent stealth notifications"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(urgentChannel)
        }
    }

    // ── Configuration Management ───────────────────────────────────────────

    fun setConfig(chatId: String, config: NotificationConfig) {
        configs[chatId] = config
        saveConfig(chatId, config)
    }

    fun getConfig(chatId: String): NotificationConfig {
        return configs[chatId] ?: NotificationConfig()
    }

    fun setMode(chatId: String, mode: NotificationMode) {
        val config = getConfig(chatId)
        setConfig(chatId, config.copy(mode = mode))
    }

    fun setPrivateCode(chatId: String, code: String) {
        val config = getConfig(chatId)
        setConfig(chatId, config.copy(privateCode = code))
    }

    fun toggleShowCodeOnLockScreen(chatId: String) {
        val config = getConfig(chatId)
        setConfig(chatId, config.copy(showCodeOnLockScreen = !config.showCodeOnLockScreen))
    }

    // ── Notification Display ───────────────────────────────────────────────

    fun showNotification(chatId: String, payload: NotificationPayload) {
        if (!canPostNotifications()) return
        val config = getConfig(chatId)
        notificationHistory[payload.messageId] = payload

        when (config.mode) {
            NotificationMode.STANDARD -> showStandardNotification(chatId, payload)
            NotificationMode.STEALTH -> showStealthNotification(chatId, payload, config)
        }
    }

    private fun showStandardNotification(chatId: String, payload: NotificationPayload) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("messageId", payload.messageId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STANDARD)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(payload.senderName)
            .setContentText(payload.content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notifySafely(NOTIFICATION_ID_BASE + payload.messageId.toInt(), notification)
    }

    private fun showStealthNotification(
        chatId: String,
        payload: NotificationPayload,
        config: NotificationConfig
    ) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("messageId", payload.messageId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val genericText = if (payload.isUrgent && config.enableUrgentAlerts) {
            "Attention required"
        } else {
            STEALTH_GENERIC_TEXTS.random()
        }

        val displayText = if (config.showCodeOnLockScreen && config.privateCode.isNotEmpty()) {
            "$genericText • ${config.privateCode}"
        } else {
            genericText
        }

        val channel = if (payload.isUrgent && config.enableUrgentAlerts) CHANNEL_URGENT else CHANNEL_STEALTH

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Calcvault")
            .setContentText(displayText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(
                if (payload.isUrgent) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_LOW
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notifySafely(NOTIFICATION_ID_BASE + payload.messageId.toInt(), notification)

        // Apply vibration for urgent
        if (payload.isUrgent && config.enableUrgentAlerts) {
            triggerUrgentVibration(config)
        }
    }

    // ── Urgent Alerts ──────────────────────────────────────────────────────

    private fun triggerUrgentVibration(config: NotificationConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(config.urgentVibrationPattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(config.urgentVibrationPattern, -1)
        }
    }

    fun markAsUrgent(messageId: Long): Boolean {
        val payload = notificationHistory[messageId] ?: return false
        notificationHistory[messageId] = payload.copy(isUrgent = true)
        return true
    }

    // ── Notification Content Retrieval ─────────────────────────────────────

    fun getNotificationContent(messageId: Long): NotificationPayload? {
        return notificationHistory[messageId]
    }

    fun clearNotification(messageId: Long) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + messageId.toInt())
        notificationHistory.remove(messageId)
    }

    fun clearAllNotifications() {
        notificationManager.cancelAll()
        notificationHistory.clear()
    }

    // ── Mode Change Notification ───────────────────────────────────────────

    fun notifyModeChange(chatId: String, newMode: NotificationMode, senderName: String) {
        val modeText = when (newMode) {
            NotificationMode.STANDARD -> "enabled Standard Notifications"
            NotificationMode.STEALTH -> "enabled Stealth Notifications"
        }

        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STANDARD)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Calcvault")
            .setContentText("$senderName $modeText")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notifySafely(chatId.hashCode(), notification)
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private fun saveConfig(chatId: String, config: NotificationConfig) {
        val json = JSONObject().apply {
            put("mode", config.mode.name)
            put("privateCode", config.privateCode)
            put("showCodeOnLockScreen", config.showCodeOnLockScreen)
            put("enableUrgentAlerts", config.enableUrgentAlerts)
        }
        StorageManager.write(CONFIG_KEY_PREFIX + chatId, json.toString().toByteArray())
    }

    private fun loadConfigs() {
        // Load all saved configs from storage
        val allKeys = StorageManager.list(CONFIG_KEY_PREFIX)
        allKeys.forEach { key ->
            val chatId = key.removePrefix(CONFIG_KEY_PREFIX)
            val data = StorageManager.read(key)?.let { String(it) }
                ?.let { JSONObject(it) } ?: return@forEach

            val config = NotificationConfig(
                mode = try {
                    NotificationMode.valueOf(data.getString("mode"))
                } catch (e: Exception) {
                    NotificationMode.STANDARD
                },
                privateCode = data.optString("privateCode", ""),
                showCodeOnLockScreen = data.optBoolean("showCodeOnLockScreen", true),
                enableUrgentAlerts = data.optBoolean("enableUrgentAlerts", true)
            )
            configs[chatId] = config
        }
    }

    fun getAllConfigs(): Map<String, NotificationConfig> {
        return configs.toMap()
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        if (!canPostNotifications()) return
        notificationManager.notify(id, notification)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
