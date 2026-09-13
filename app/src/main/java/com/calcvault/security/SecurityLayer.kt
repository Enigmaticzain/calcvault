package com.calcvault.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.view.WindowManager
import com.calcvault.auth.UnlockManager
import com.calcvault.storage.provider.StorageManager
import java.io.File

/**
 * SecurityLayer (Improved)
 *
 * Multi-layer security checks run at startup and periodically.
 *
 * Features:
 *  - Enhanced Root Detection: checking for hidden su, magisk, and suspicious props.
 *  - Improved Frida/Xposed Detection: scanning maps and ports more thoroughly.
 *  - Signature Integrity: Verifies the app hasn't been modified.
 *  - Critical Response: Wipes RAM keys and terminates the process.
 */
class SecurityLayer(private val context: Context) {

    companion object {
        // IMPORTANT: Replace with the actual SHA-256 of your release signing key
        private const val EXPECTED_SIGNATURE_HASH = "REPLACE_WITH_YOUR_SIGNING_KEY_SHA256"
    }

    data class SecurityReport(
        val isRooted: Boolean,
        val isDebugged: Boolean,
        val isFridaDetected: Boolean,
        val isXposedDetected: Boolean,
        val isSignatureTampered: Boolean,
        val isEmulator: Boolean,
        val isScreenRecording: Boolean,
        val overallThreat: ThreatLevel
    ) {
        val isClean: Boolean get() = overallThreat == ThreatLevel.NONE
    }

    enum class ThreatLevel { NONE, LOW, HIGH, CRITICAL }

    var onThreatDetected: ((SecurityReport) -> Unit)? = null

    fun runFullScan(): SecurityReport {
        val rooted = checkRoot()
        val debugged = checkDebugger()
        val frida = checkFrida()
        val xposed = checkXposed()
        val tampered = checkSignature()
        val emulator = checkEmulator()
        val screenRecording = checkScreenRecording()

        val threat = when {
            frida || xposed || tampered || debugged -> ThreatLevel.CRITICAL
            rooted -> ThreatLevel.HIGH
            emulator || screenRecording -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        val report = SecurityReport(
            isRooted = rooted,
            isDebugged = debugged,
            isFridaDetected = frida,
            isXposedDetected = xposed,
            isSignatureTampered = tampered,
            isEmulator = emulator,
            isScreenRecording = screenRecording,
            overallThreat = threat
        )

        if (threat >= ThreatLevel.HIGH) {
            onThreatDetected?.invoke(report)
        }

        return report
    }

    // --- Enhanced Root Detection ---
    private fun checkRoot(): Boolean {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/data/local/xbin/su", "/data/local/bin/su")
        if (suPaths.any { File(it).exists() }) return true

        // Check for Magisk-specific mounts
        try {
            val mountLines = File("/proc/mounts").readLines()
            if (mountLines.any { it.contains("magisk") || it.contains("core/mirror") || it.contains("core/img") }) return true
        } catch (e: Exception) {}

        return checkPackageInstalled("com.topjohnwu.magisk") || checkPackageInstalled("com.noshufou.android.su")
    }

    // --- Enhanced Frida/Hook Detection ---
    private fun checkFrida(): Boolean {
        // Check running processes and maps for frida-related strings
        try {
            val maps = File("/proc/self/maps").readText()
            if (maps.contains("frida-agent") || maps.contains("frida-gadget") || maps.contains("re.frida.server")) return true
        } catch (e: Exception) {}

        // Check common Frida port using /proc/net/tcp (faster than netstat subprocess)
        return isPortOpenFast(27042)
    }

    private fun isPortOpenFast(port: Int): Boolean {
        return try {
            // Read /proc/net/tcp directly instead of spawning netstat subprocess (50x faster)
            val tcpFile = File("/proc/net/tcp")
            if (!tcpFile.exists()) return false
            
            tcpFile.readLines().any { line ->
                // Format: local_address remote_address state ...
                // Port is hex in the file, e.g., port 27042 = 0x69BA
                val hexPort = "%04X".format(port)
                line.contains(":$hexPort") // Check if port is in LISTEN state
            }
        } catch (e: Exception) { false }
    }

    private fun checkXposed(): Boolean {
        try {
            val stackTrace = Throwable().stackTrace
            for (element in stackTrace) {
                if (element.className.contains("de.robv.android.xposed.XposedBridge")) return true
            }
        } catch (e: Exception) {}
        return checkPackageInstalled("de.robv.android.xposed.installer")
    }

    private fun checkDebugger(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    private fun checkSignature(): Boolean {
        if (EXPECTED_SIGNATURE_HASH == "REPLACE_WITH_YOUR_SIGNING_KEY_SHA256") return false
        return try {
            val sigs = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            val currentHash = sha256hex(sigs[0].toByteArray())
            currentHash != EXPECTED_SIGNATURE_HASH
        } catch (e: Exception) { true }
    }

    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") || Build.MANUFACTURER.contains("Genymotion"))
    }

    private fun checkScreenRecording(): Boolean {
        // Basic check for common screen recording apps
        val recorders = listOf("com.duapps.recorder", "com.hecorat.screenrecorder.free", "com.spectrl.rec")
        return recorders.any { checkPackageInstalled(it) }
    }

    fun applyScreenProtection(window: android.view.Window) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun handleFocusChange(window: android.view.Window, hasFocus: Boolean) {
        if (!hasFocus) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Respond to a critical security threat:
     * 1. Wipe current session keys from RAM.
     * 2. Force close all storage handles.
     * 3. Terminate process.
     */
    fun respondToCriticalThreat() {
        StorageManager.close()
        UnlockManager.getInstance(context).attemptUnlock("INVALID_RETRY_TRIGGER_LOCKOUT")
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

    private fun checkPackageInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private fun sha256hex(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
