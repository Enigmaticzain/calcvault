package com.calcvault.utils

import android.content.Context
import android.os.Process
import com.calcvault.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Emergency security features
 */
object SecurityUtils {

    /**
     * PANIC WIPE: Destroy all data immediately.
     */
    suspend fun panicWipe(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Delete all messages from Room DB
                AppDatabase.getInstance(context).messageDao().deleteAll()

                // 2. Delete the entire database file
                context.deleteDatabase("calcvault_room_db")
                context.deleteDatabase("calcvault_db") // Old naming if exists

                // 3. Clear session and shared preferences
                SessionManager.clear()
                context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()

                // 4. Clear app cache and files
                context.cacheDir.deleteRecursively()
                context.filesDir.deleteRecursively()
                
                // 5. Exit app
                Process.killProcess(Process.myPid())
            } catch (e: Exception) {
                // Emergency exit anyway
                Process.killProcess(Process.myPid())
            }
        }
    }
}
