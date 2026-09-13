package com.calcvault.storage.provider

import android.content.Context
import java.io.File

/**
 * StorageManager
 *
 * Global access point for the active StorageProvider.
 */
object StorageManager {

    private var _provider: StorageProvider? = null
    private var _context: Context? = null
    private var _masterKey: ByteArray? = null
    private var _macKey: ByteArray? = null

    val provider: StorageProvider
        get() = _provider ?: throw IllegalStateException("StorageManager not initialized")

    val mode: StorageMode
        get() = provider.mode

    var onModeChanged: ((StorageMode) -> Unit)? = null

    fun init(
        context: Context,
        masterKey: ByteArray,
        macKey: ByteArray? = null,
        usbRoot: File? = null,
        preferUSB: Boolean = false
    ) {
        _context = context.applicationContext
        _masterKey = masterKey.copyOf()
        _macKey = macKey?.copyOf()

        // Simple logic for build resolution: use Local if no USB or not preferred
        _provider = if (preferUSB && usbRoot != null) {
            USBStorageProvider(context, usbRoot, masterKey, macKey ?: ByteArray(32))
        } else {
            LocalStorageProvider(context, masterKey)
        }
        _provider?.open()
        onModeChanged?.invoke(_provider!!.mode)
    }

    /**
     * Switch the active provider to USB mode.
     * Uses cached keys from the initial session.
     */
    fun switchToUSB(usbRoot: File) {
        val context = _context ?: return
        val masterKey = _masterKey ?: return
        val macKey = _macKey ?: ByteArray(32)

        _provider?.close()
        val usbProvider = USBStorageProvider(context, usbRoot, masterKey, macKey)
        if (usbProvider.open()) {
            _provider = usbProvider
            onModeChanged?.invoke(StorageMode.USB)
        }
    }

    fun isReady(): Boolean = _provider != null

    fun close() {
        _provider?.close()
        _masterKey?.fill(0)
        _macKey?.fill(0)
        _masterKey = null
        _macKey = null
    }

    fun destroy(): Boolean {
        val result = _provider?.destroy() ?: false
        if (result) {
            close()
        }
        return result
    }

    // ── Helper proxies for common operations ───────────────────────────────

    fun write(key: String, data: ByteArray): Boolean = provider.writeData(key, data)

    fun read(key: String): ByteArray? = provider.readData(key)

    fun delete(key: String): Boolean = provider.deleteData(key)

    fun list(prefix: String = ""): List<String> = provider.listKeys(prefix)

    fun exists(key: String): Boolean = provider.exists(key)
}
