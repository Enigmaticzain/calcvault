package com.calcvault

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.calcvault.auth.VaultDestructionBus
import com.calcvault.security.SecurityLayer
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.*

class CalcVaultApp : Application() {

    companion object {
        const val CHANNEL_SILENT = "silent_channel"
        const val CHANNEL_CALLS = "call_channel"
    }

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize SQLCipher native libs (defer to background to avoid main thread blocking)
        appScope.launch {
            try {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(this@CalcVaultApp)
            } catch (e: Exception) {
                Log.e("CalcVaultApp", "Failed to load SQLCipher libs", e)
            }
        }

        // 2. Storage Manager and Channels
        try {
            // StorageManager.init(this) // Removed call that was missing masterKey
            createNotificationChannels()
        } catch (e: Exception) {
            Log.e("CalcVaultApp", "Failed to create channels", e)
        }

        // 3. Initialize permanent pair manager (must happen before any activity accesses it)
        com.calcvault.sync.PermanentPairManager.init(this)

        // 4. Vault destruction bus
        VaultDestructionBus.register {
            appScope.launch {
                val engine = USBStorageEngine.getInstance(this@CalcVaultApp)
                engine.destroyContainer()
                SessionManager.clear()
            }
        }

        // 5. Global Crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                SessionManager.clear()
                USBStorageEngine.getInstance(this).lock()
            } catch (e: Exception) {
                // Swallow
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 6. Startup security check
        appScope.launch {
            try {
                val security = SecurityLayer(this@CalcVaultApp)
                val report = security.runFullScan()
                if (report.isFridaDetected || report.isXposedDetected || report.isSignatureTampered) {
                    security.respondToCriticalThreat()
                }
            } catch (e: Exception) {
                Log.e("CalcVaultApp", "Security scan failed", e)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val silentChannel = NotificationChannel(
                CHANNEL_SILENT,
                "Silent Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent notifications for app events"
                setShowBadge(false)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for incoming calls"
                enableLights(true)
                setShowBadge(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(silentChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            if (SessionManager.isVaultOpen) {
                USBStorageEngine.getInstance(this).lock()
                SessionManager.clear()
            }
        } catch (e: Exception) {}
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_COMPLETE && SessionManager.isVaultOpen) {
                USBStorageEngine.getInstance(this).lock()
                SessionManager.clear()
            }
        } catch (e: Exception) {}
    }
}
