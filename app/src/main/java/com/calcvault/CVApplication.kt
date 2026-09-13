package com.calcvault

import android.app.Application
import android.util.Log
import com.calcvault.auth.VaultDestructionBus
import com.calcvault.security.SecurityLayer
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.CVNotificationManager
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.*

/**
 * CVApplication
 *
 * Application entry point.
 */
class CVApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize SQLCipher native libs
        try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        } catch (e: Exception) {
            Log.e("CVApplication", "Failed to load SQLCipher libs", e)
        }

        // 2. Notification channels
        try {
            CVNotificationManager(this).createChannels()
        } catch (e: Exception) {
            Log.e("CVApplication", "Failed to create notification channels", e)
        }

        // 3. Vault destruction bus
        VaultDestructionBus.register {
            appScope.launch {
                val engine = USBStorageEngine.getInstance(this@CVApplication)
                engine.destroyContainer()
                SessionManager.clear()
            }
        }

        // 4. Global Crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Clear all sensitive session state immediately
                SessionManager.clear()
                // Lock storage engine
                USBStorageEngine.getInstance(this).lock()
            } catch (e: Exception) {
                // Swallow — we're in a crash handler
            }
            // Delegate to default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 5. Startup security check
        appScope.launch {
            try {
                val security = SecurityLayer(this@CVApplication)
                val report = security.runFullScan()

                // CRITICAL threats kill immediately
                if (report.isFridaDetected || report.isXposedDetected || report.isSignatureTampered) {
                    security.respondToCriticalThreat()
                }
            } catch (e: Exception) {
                Log.e("CVApplication", "Security scan failed", e)
            }
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
