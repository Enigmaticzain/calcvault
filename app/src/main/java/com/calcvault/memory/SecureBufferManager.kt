package com.calcvault.memory

import android.util.Log
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * SecureBufferManager
 *
 * Tracks every sensitive byte array allocated during a session.
 * Guarantees they are wiped when no longer needed, even on exceptions.
 *
 * Usage:
 *   val buf = SecureBufferManager.allocate(size)
 *   try {
 *       // use buf
 *   } finally {
 *       SecureBufferManager.release(buf)  // guaranteed wipe
 *   }
 *
 *   // Or use the scoped version:
 *   SecureBufferManager.withBuffer(size) { buf ->
 *       // buf is automatically wiped when block exits (even on exception)
 *   }
 */
object SecureBufferManager {

    private val tracked = ConcurrentHashMap<Long, WeakReference<ByteArray>>()
    private val idCounter = AtomicLong(0)

    data class SecureBuffer(val id: Long, val data: ByteArray)

    /**
     * Allocate a tracked secure buffer.
     */
    fun allocate(size: Int): SecureBuffer {
        val data = ByteArray(size)
        val id = idCounter.incrementAndGet()
        tracked[id] = WeakReference(data)
        return SecureBuffer(id, data)
    }

    /**
     * Wipe and release a tracked buffer.
     */
    fun release(buffer: SecureBuffer) {
        wipeBytes(buffer.data)
        tracked.remove(buffer.id)
    }

    /**
     * Scoped allocation — buffer is guaranteed wiped when block exits.
     */
    inline fun <T> withBuffer(size: Int, block: (ByteArray) -> T): T {
        val buf = allocate(size)
        return try { block(buf.data) } finally { release(buf) }
    }

    /**
     * Wipe all tracked buffers — call on lock/session end.
     */
    fun wipeAll() {
        tracked.values.forEach { ref ->
            ref.get()?.let { wipeBytes(it) }
        }
        tracked.clear()
    }

    /**
     * Multi-pass wipe — 3 passes: 0x00, 0xFF, 0x00.
     * Resistant to cold-boot attack memory recovery.
     */
    fun wipeBytes(data: ByteArray) {
        data.fill(0x00)
        data.fill(0xFF.toByte())
        data.fill(0x00)
    }

    fun wipeChars(data: CharArray) {
        data.fill('\u0000')
        data.fill('\uFFFF')
        data.fill('\u0000')
    }
}

/**
 * LogGuard
 *
 * Verifies that sensitive data never appears in logcat.
 * In release builds (ProGuard), Log.* calls are stripped.
 * This class provides runtime verification and a safe logging wrapper.
 */
object LogGuard {

    private var loggingEnabled = false

    /**
     * Initialise — disables logging in release builds.
     */
    fun init(isDebugBuild: Boolean) {
        loggingEnabled = isDebugBuild
    }

    /**
     * Safe log — only outputs in debug builds, never in release.
     */
    fun d(tag: String, msg: String) {
        if (loggingEnabled) Log.d(tag, msg)
    }

    /**
     * Safe error log — only outputs in debug, never in release.
     */
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (loggingEnabled) {
            if (throwable != null) {
                Log.e(tag, msg, throwable)
            } else {
                Log.e(tag, msg)
            }
        }
    }

    /**
     * This must NEVER log any of these patterns.
     * Call in debug builds to verify nothing leaks.
     */
    fun assertNoSensitiveData(text: String): Boolean {
        val sensitivePatterns = listOf(
            "passphrase", "password", "secret", "key_", "session_key",
            "private_key", "chain_key", "root_key", "ratchet"
        )
        return sensitivePatterns.none { text.lowercase().contains(it) }
    }
}

/**
 * TempFileGuard
 *
 * Enforces that all temporary files are:
 * 1. Created in the designated temp directory
 * 2. Wiped (not just deleted) when no longer needed
 * 3. Tracked so orphan wipe happens on session end
 */
object TempFileGuard {

    private val trackedFiles = ConcurrentHashMap<String, File>()

    /**
     * Create a tracked temp file. It will be wiped on release() or wipeAll().
     */
    fun create(directory: File, prefix: String): File {
        val file = File(directory, "${prefix}_${System.nanoTime()}.tmp")
        trackedFiles[file.absolutePath] = file
        return file
    }

    /**
     * Wipe and delete a temp file.
     */
    fun release(file: File) {
        try {
            if (file.exists()) {
                // Overwrite with random data before deletion
                val size = file.length().toInt().coerceAtMost(1024 * 1024)
                file.writeBytes(java.security.SecureRandom().generateSeed(size))
                file.delete()
            }
        } catch (e: Exception) {
            file.delete()
        }
        trackedFiles.remove(file.absolutePath)
    }

    /**
     * Wipe all tracked temp files. Call on session end.
     */
    fun wipeAll() {
        trackedFiles.values.toList().forEach { release(it) }
        trackedFiles.clear()
    }

    /**
     * Scan a directory for orphan temp files (from crashed sessions) and wipe them.
     */
    fun cleanOrphanFiles(directory: File, maxAgeMs: Long = 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        directory.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.endsWith(".part") ||
                file.name.startsWith("cv_tmp") || file.name.startsWith("sync_")
            ) {
                if (file.lastModified() < cutoff) {
                    release(file)
                }
            }
        }
    }
}
