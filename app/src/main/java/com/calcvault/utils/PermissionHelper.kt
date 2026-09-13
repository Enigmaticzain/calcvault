package com.calcvault.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionHelper
 *
 * Handles all runtime permission requests in one place.
 *
 * Permission groups:
 *  CALL    → RECORD_AUDIO, CAMERA, MODIFY_AUDIO_SETTINGS
 *  BT      → BLUETOOTH_CONNECT, BLUETOOTH_SCAN (Android 12+)
 *  WIFI    → ACCESS_FINE_LOCATION (required for WiFi Direct)
 *  STORAGE → READ_EXTERNAL_STORAGE (for USB on older APIs)
 *
 * Request codes:
 *  100 = CALL
 *  101 = BT
 *  102 = WIFI
 *  103 = STORAGE
 */
object PermissionHelper {

    const val RC_CALL = 100
    const val RC_BT = 101
    const val RC_WIFI = 102
    const val RC_STORAGE = 103

    // ─── Permission Groups ─────────────────────────────────────────────────

    val CALL_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )

    val BT_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val WIFI_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CHANGE_WIFI_STATE
    )

    val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf<String>() // Android 13+ uses scoped storage; USB via SAF
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // ─── Check ─────────────────────────────────────────────────────────────

    fun hasCallPermissions(activity: Activity): Boolean =
        CALL_PERMISSIONS.all { isGranted(activity, it) }

    fun hasBluetoothPermissions(activity: Activity): Boolean =
        BT_PERMISSIONS.all { isGranted(activity, it) }

    fun hasWifiPermissions(activity: Activity): Boolean =
        WIFI_PERMISSIONS.all { isGranted(activity, it) }

    fun hasStoragePermissions(activity: Activity): Boolean =
        STORAGE_PERMISSIONS.all { isGranted(activity, it) }

    // ─── Request ───────────────────────────────────────────────────────────

    fun requestCallPermissions(activity: Activity) =
        ActivityCompat.requestPermissions(activity, CALL_PERMISSIONS, RC_CALL)

    fun requestBluetoothPermissions(activity: Activity) =
        ActivityCompat.requestPermissions(activity, BT_PERMISSIONS, RC_BT)

    fun requestWifiPermissions(activity: Activity) =
        ActivityCompat.requestPermissions(activity, WIFI_PERMISSIONS, RC_WIFI)

    fun requestStoragePermissions(activity: Activity) {
        if (STORAGE_PERMISSIONS.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, STORAGE_PERMISSIONS, RC_STORAGE)
        }
    }

    /**
     * Request all permissions needed to start a call.
     * Returns true immediately if already granted.
     */
    fun ensureCallPermissions(activity: Activity): Boolean {
        return if (hasCallPermissions(activity)) {
            true
        } else { requestCallPermissions(activity); false }
    }

    /**
     * Handle results in Activity.onRequestPermissionsResult.
     *
     * Usage:
     *   override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, results: IntArray) {
     *       PermissionHelper.onResult(rc, results,
     *           onCallGranted   = { startCall() },
     *           onCallDenied    = { showCallDenied() }
     *       )
     *   }
     */
    fun onResult(
        requestCode: Int,
        results: IntArray,
        onCallGranted: () -> Unit = {},
        onCallDenied: () -> Unit = {},
        onBtGranted: () -> Unit = {},
        onBtDenied: () -> Unit = {},
        onWifiGranted: () -> Unit = {},
        onWifiDenied: () -> Unit = {}
    ) {
        val allGranted = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            RC_CALL -> if (allGranted) onCallGranted() else onCallDenied()
            RC_BT -> if (allGranted) onBtGranted() else onBtDenied()
            RC_WIFI -> if (allGranted) onWifiGranted() else onWifiDenied()
        }
    }

    // ─── Private ───────────────────────────────────────────────────────────

    private fun isGranted(activity: Activity, permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
}
