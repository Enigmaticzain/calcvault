package com.calcvault.notifications

import android.content.Context
import android.widget.Toast

data class PredefinedSound(
    val id: String,
    val name: String,
    val uri: String? = null
)

data class PredefinedVibration(
    val id: String,
    val name: String,
    val pattern: LongArray
)

object DefaultNotificationSettings {
    val PREDEFINED_SOUNDS = listOf(
        PredefinedSound(id = "default", name = "Default"),
        PredefinedSound(id = "soft_chime", name = "Soft Chime"),
        PredefinedSound(id = "digital_ping", name = "Digital Ping"),
        PredefinedSound(id = "gentle_bell", name = "Gentle Bell")
    )

    val PREDEFINED_VIBRATIONS = listOf(
        PredefinedVibration(id = "default", name = "Default", pattern = longArrayOf(0, 120)),
        PredefinedVibration(id = "short_double", name = "Short Double", pattern = longArrayOf(0, 100, 80, 100)),
        PredefinedVibration(id = "urgent_wave", name = "Urgent Wave", pattern = longArrayOf(0, 250, 120, 250, 120, 350)),
        PredefinedVibration(id = "steady", name = "Steady", pattern = longArrayOf(0, 180, 70, 180, 70, 180))
    )
}

class CustomNotificationManager(private val context: Context) {
    fun testNotification() {
        CVNotificationManager(context).notifyNewMessage()
        Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
    }
}
