package com.calcvault.sync

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * ApkUpdateManager
 *
 * Handles the over-the-air APK update flow between Zain and Sanu.
 * When Zain installs a new version, he can share the APK via the
 * existing sync channel. Sanu's app detects the version mismatch
 * and shows an "Update Available" button.
 *
 * Since this is a 2-person private app, the update flow is:
 * 1. Zain builds/installs a new APK on his phone.
 * 2. Zain presses "Push Update to Sanu" in Settings.
 * 3. The APK is shared via the existing sync channel (Bluetooth/signaling).
 * 4. Sanu's app shows a banner "Update Available" with a button.
 * 5. Sanu taps "Update" → installs the APK.
 */
object ApkUpdateManager {

    private const val APK_FILENAME = "calcvault_update.apk"

    /**
     * Get the current app version code.
     */
    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) { 1 }
    }

    /**
     * Get the current app version name.
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }

    /**
     * Check if the partner has a newer version than us.
     */
    fun checkForUpdate(context: Context): Boolean {
        val myVersion = getCurrentVersionCode(context)
        val partnerVersion = PermanentPairManager.getPartnerVersionCode()
        return partnerVersion > myVersion
    }

    /**
     * Called by Zain to share the current APK with Sanu.
     * This uses Android's built-in share to send the APK file
     * (via Bluetooth, NFC, nearby share, or any file transfer app).
     */
    fun shareApkWithPartner(activity: Activity) {
        try {
            val apkPath = activity.applicationInfo.sourceDir
            val apkFile = File(apkPath)
            
            // Copy to cache so we can share it
            val cacheDir = File(activity.cacheDir, "updates")
            cacheDir.mkdirs()
            val shareable = File(cacheDir, APK_FILENAME)
            apkFile.copyTo(shareable, overwrite = true)

            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                shareable
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "CalcVault Update v${getCurrentVersionName(activity)}")
            }
            activity.startActivity(Intent.createChooser(shareIntent, "Send Update to Partner"))
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to share APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Install an APK file that was received from the partner.
     */
    fun installApk(activity: Activity, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to install: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Install from a URL (e.g., downloaded from signaling server).
     */
    fun downloadAndInstall(activity: Activity, url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("CalcVault Update")
                .setDescription("Downloading latest version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILENAME)
                .setMimeType("application/vnd.android.package-archive")

            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            // Listen for download complete
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            APK_FILENAME
                        )
                        if (file.exists()) {
                            installApk(activity, file)
                        }
                        PermanentPairManager.clearUpdateFlag()
                        activity.unregisterReceiver(this)
                    }
                }
            }
            activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        } catch (e: Exception) {
            Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
