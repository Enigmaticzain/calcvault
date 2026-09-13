// ChatActivity Integration for Dual Notification System
// Add these to ChatActivity.kt

import com.calcvault.notifications.DualNotificationSystem
import com.calcvault.notifications.NotificationPayload
import com.calcvault.notifications.NotificationMode

// Add to ChatActivity class properties

private lateinit var notificationSystem: DualNotificationSystem

// Add to onCreate() method

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing code ...
    
    notificationSystem = DualNotificationSystem(this)
}

// Hook into networkEngine.onMessageReceived

networkEngine.onMessageReceived = { msg ->
    lifecycleScope.launch {
        if (msg.type == AppendOnlyMessageDB.MSG_THEME_SYNC) {
            handleThemeSync(msg.content)
        } else {
            messageDB.markDelivered(msg.id)
            withContext(Dispatchers.Main) {
                adapter.addMessage(msg)
                scrollBottom()
                animEngine.checkMessageTriggers(msg.content)
                
                // Show notification
                val payload = NotificationPayload(
                    messageId = msg.id,
                    senderId = msg.from,
                    senderName = SessionManager.partnerNickname.ifBlank { msg.from },
                    content = msg.content,
                    timestamp = msg.timestamp,
                    isUrgent = msg.content.contains("[URGENT]"),
                    type = msg.type
                )
                notificationSystem.showNotification(partnerUserId, payload)
                
                if (msg.type == AppendOnlyMessageDB.MSG_MOOD) updatePartnerMood()
            }
        }
    }
}

// Add method to handle notification mode changes

private fun handleNotificationModeChange(newMode: NotificationMode) {
    notificationSystem.setMode(partnerUserId, newMode)
    
    // Notify partner
    lifecycleScope.launch {
        val modeText = when (newMode) {
            NotificationMode.STANDARD -> "enabled Standard Notifications"
            NotificationMode.STEALTH -> "enabled Stealth Notifications"
        }
        val systemMsg = messageDB.sendTextMessage(
            localUserId,
            partnerUserId,
            "[SYSTEM] $modeText"
        )
        networkEngine.sendEncryptedPayload(
            systemMsg.id,
            storageEngine.encrypt(modeText.toByteArray()),
            systemMsg.type
        )
    }
}

// Add method to mark message as urgent

private fun markMessageAsUrgent(messageId: Long) {
    notificationSystem.markAsUrgent(messageId)
}

// Add to settings menu

private fun showNotificationSettings() {
    val intent = Intent(this, NotificationSettingsActivity::class.java).apply {
        putExtra("chatId", partnerUserId)
    }
    startActivity(intent)
}

// Add button to chat UI for urgent marking

private fun addUrgentButton() {
    val btnUrgent = Button(this).apply {
        text = "Mark Urgent"
        setOnClickListener {
            val lastMessage = messages.lastOrNull()
            if (lastMessage != null) {
                markMessageAsUrgent(lastMessage.id)
                Toast.makeText(this@ChatActivity, "Message marked as urgent", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Add to your UI layout
}

// Add to AndroidManifest.xml

<activity
    android:name="com.calcvault.ui.NotificationSettingsActivity"
    android:exported="false"
    android:screenOrientation="portrait" />

// Add permissions to AndroidManifest.xml

<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />

// Example: Send urgent message

suspend fun sendUrgentMessage(text: String) {
    val urgentText = "[URGENT] $text"
    val msg = messageDB.sendTextMessage(localUserId, partnerUserId, urgentText)
    val payload = storageEngine.encrypt(urgentText.toByteArray())
    networkEngine.sendEncryptedPayload(msg.id, payload, msg.type)
    
    withContext(Dispatchers.Main) {
        adapter.addMessage(msg)
        scrollBottom()
    }
}

// Example: Toggle notification mode

fun toggleNotificationMode() {
    val currentConfig = notificationSystem.getConfig(partnerUserId)
    val newMode = if (currentConfig.mode == NotificationMode.STANDARD) {
        NotificationMode.STEALTH
    } else {
        NotificationMode.STANDARD
    }
    handleNotificationModeChange(newMode)
}

// Example: Set private code

fun setPrivateCode(code: String) {
    notificationSystem.setPrivateCode(partnerUserId, code)
    Toast.makeText(this, "Private code set to: $code", Toast.LENGTH_SHORT).show()
}

// Example: Get notification config

fun getNotificationConfig() {
    val config = notificationSystem.getConfig(partnerUserId)
    println("Mode: ${config.mode}")
    println("Private Code: ${config.privateCode}")
    println("Show Code on Lock Screen: ${config.showCodeOnLockScreen}")
    println("Enable Urgent Alerts: ${config.enableUrgentAlerts}")
}

// Example: Clear notifications

fun clearAllNotifications() {
    notificationSystem.clearAllNotifications()
}

// Example: Handle notification tap

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    val messageId = intent?.getLongExtra("messageId", -1L) ?: return
    if (messageId > 0) {
        val payload = notificationSystem.getNotificationContent(messageId)
        if (payload != null) {
            // Show full content
            Toast.makeText(this, "From: ${payload.senderName}\n${payload.content}", Toast.LENGTH_LONG).show()
        }
    }
}
