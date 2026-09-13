package com.calcvault.memory

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import java.io.File

/**
 * MemoryGuard
 *
 * Enforces RAM-only operation — prevents CalcVault data from
 * leaking into Android's various cache and temp systems.
 *
 * Protects against:
 *  - Android WebView cache (if browser module used)
 *  - Bitmap cache / Glide / Picasso caches
 *  - System temp files created by content providers
 *  - Clipboard leakage
 *  - Android recent apps screenshot capture
 *  - logcat data exposure
 *
 * Also provides the centralized FLAG_SECURE applier — every
 * Activity must call MemoryGuard.secureWindow(this) in onCreate.
 */
object MemoryGuard {

    // ── Window Security ────────────────────────────────────────────────────

    /**
     * Apply FLAG_SECURE to a window.
     * Prevents: screenshots, screen recording, recent apps preview capture.
     * MUST be called in every Activity.onCreate() BEFORE setContentView().
     */
    fun secureWindow(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    /**
     * Blur/hide app preview in recents when focus is lost.
     * Call from Activity.onWindowFocusChanged().
     */
    fun onFocusChanged(activity: Activity, hasFocus: Boolean) {
        if (!hasFocus) {
            // App going to background — ensure FLAG_SECURE is applied
            secureWindow(activity)
        }
    }

    /**
     * Clear the system clipboard when the app loses focus.
     * Prevents sensitive content (if any) from being accessible.
     */
    fun clearClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        // On API 28+ we can clear directly; on older we overwrite with empty
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cm.clearPrimaryClip()
        } else {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
    }

    // ── Cache Nuke ────────────────────────────────────────────────────────

    /**
     * Wipe all app cache directories.
     * Call on lock to ensure no decrypted data lingers.
     */
    fun nukeCaches(context: Context) {
        nukeDir(context.cacheDir)
        nukeDir(context.codeCacheDir)
        context.externalCacheDirs.forEach { nukeDir(it) }

        // Nuke any temp files we may have written
        nukeTempFiles(context)
    }

    /**
     * Delete all files in a directory recursively.
     */
    private fun nukeDir(dir: File?) {
        dir?.walkBottomUp()?.forEach { file ->
            try {
                if (file.isFile) {
                    // Overwrite before delete for sensitive data
                    file.writeBytes(ByteArray(file.length().toInt()))
                    file.delete()
                } else if (file != dir) {
                    file.delete()
                }
            } catch (e: Exception) { /* best effort */ }
        }
    }

    /**
     * Delete CalcVault temp files (partial writes, sync parts, etc.)
     */
    fun nukeTempFiles(context: Context) {
        val patterns = listOf("cv_tmp_", "sync_", "cv_journal_tmp", "cv_index_tmp")
        val dirsToCheck = listOf(
            context.filesDir,
            context.cacheDir,
            context.getExternalFilesDir(null)
        )
        for (dir in dirsToCheck) {
            dir?.listFiles()?.forEach { file ->
                if (patterns.any { file.name.startsWith(it) }) {
                    try { file.delete() } catch (e: Exception) { }
                }
            }
        }
    }

    // ── Secure Byte Array Operations ───────────────────────────────────────

    /**
     * Wipe a byte array with multiple passes.
     * Call immediately after use of any sensitive byte array.
     */
    fun wipe(data: ByteArray?) {
        data ?: return
        data.fill(0)
        data.fill(0xFF.toByte())
        data.fill(0)
    }

    /**
     * Wipe a char array (passphrase input).
     */
    fun wipe(data: CharArray?) {
        data ?: return
        data.fill('\u0000')
        data.fill('\uFFFF')
        data.fill('\u0000')
    }

    // ── WebView Cache Control (Private Browser Module) ────────────────────

    /**
     * Configure a WebView to store nothing — no cache, no cookies, no history.
     */
    fun configurePrivateWebView(webView: android.webkit.WebView) {
        webView.settings.apply {
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            domStorageEnabled = false
            databaseEnabled = false
            saveFormData = false
            savePassword = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(false)
            removeAllCookies(null)
        }
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
    }

    // ── Bitmap Cache Prevention ────────────────────────────────────────────

    /**
     * Load a bitmap without caching — for media viewing.
     * Returns bitmap directly from decrypted bytes in RAM.
     * Do NOT use Glide/Picasso/Coil (they cache to disk).
     */
    fun decodeBitmapNocache(bytes: ByteArray): android.graphics.Bitmap? {
        return try {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
        // Caller is responsible for wiping `bytes` after use
    }

    // ── Log Suppression ───────────────────────────────────────────────────

    /**
     * Verify that logcat is not accessible (rooted devices can read it).
     * Returns true if logcat access appears restricted.
     */
    fun isLogcatRestricted(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1"))
            val output = proc.inputStream.bufferedReader().readText()
            proc.destroy()
            output.isBlank() // blank output = restricted = good
        } catch (e: Exception) {
            true // exception means can't run logcat = restricted = good
        }
    }
}
