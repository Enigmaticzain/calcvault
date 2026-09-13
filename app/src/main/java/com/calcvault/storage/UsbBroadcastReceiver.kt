package com.calcvault.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import java.io.File

/**
 * UsbBroadcastReceiver
 *
 * Listens for USB OTG attach / detach events.
 *
 * On ATTACH:
 *  - Locates USB mount path
 *  - Notifies USBStorageEngine
 *  - Triggers decrypt + load flow if vault is in background
 *
 * On DETACH:
 *  - Flushes writes
 *  - Locks engine
 *  - Clears session key from RAM
 */
class UsbBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val engine = USBStorageEngine.getInstance(context)

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val usbRoot = findUsbMountPath(context)
                if (usbRoot != null) {
                    engine.onUsbAttached(usbRoot)
                    // Notify any active session that USB is ready
                    UsbEventBus.notifyAttached(usbRoot)
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                engine.onUsbDetached()
                UsbEventBus.notifyDetached()
            }
        }
    }

    /**
     * Locate USB OTG mount path.
     *
     * Android doesn't expose a standard API for this — we check
     * common mount points and find the one that isn't internal storage.
     */
    private fun findUsbMountPath(context: Context): File? {
        // Strategy 1: check external storage volumes
        val externalDirs = context.getExternalFilesDirs(null)
        for (dir in externalDirs) {
            if (dir == null) continue
            val path = dir.absolutePath
            // Internal storage contains "emulated" — USB won't
            if (!path.contains("emulated")) {
                // Walk up to root of this volume
                val volumeRoot = getVolumeRoot(dir)
                if (volumeRoot != null && volumeRoot.exists()) return volumeRoot
            }
        }

        // Strategy 2: check common OTG mount points
        val candidates = listOf(
            "/storage/usb0",
            "/storage/usb1",
            "/mnt/usb_storage",
            "/mnt/media_rw/usb0",
            "/storage/UsbDriveA",
            "/storage/UsbDriveB"
        )
        return candidates.map { File(it) }.firstOrNull { it.exists() && it.isDirectory }
    }

    private fun getVolumeRoot(dir: File): File? {
        // Walk up from app-specific dir (e.g. /storage/XXXX-XXXX/Android/data/...)
        // to get the volume root /storage/XXXX-XXXX
        var current = dir
        repeat(4) {
            current = current.parentFile ?: return null
        }
        return if (current.exists()) current else null
    }
}

/**
 * Simple event bus for USB events.
 * Replace with StateFlow in a full MVVM implementation.
 */
object UsbEventBus {
    private val listeners = mutableListOf<UsbEventListener>()

    interface UsbEventListener {
        fun onUsbAttached(root: File)
        fun onUsbDetached()
    }

    fun register(listener: UsbEventListener) { listeners.add(listener) }
    fun unregister(listener: UsbEventListener) { listeners.remove(listener) }

    fun notifyAttached(root: File) { listeners.forEach { it.onUsbAttached(root) } }
    fun notifyDetached() { listeners.forEach { it.onUsbDetached() } }
}
