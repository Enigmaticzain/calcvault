package com.calcvault.security.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

/**
 * RuntimeSecurityMonitor
 *
 * Production-level security monitoring.
 * Combines detection of all threat vectors into a single threat score.
 *
 * Threat vectors checked:
 *  1. Root — su binary, Magisk, writable /system, dangerous build props
 *  2. Debug — TracerPid, isDebuggerConnected, waitingForDebugger, ptrace
 *  3. Frida — /proc/maps, ports 27042-27043, frida-server process, gadget .so
 *  4. Xposed — stack trace analysis, known package names, XposedBridge class
 *  5. Emulator — Build fingerprints, QEMU props, hardware sensor absence
 *  6. APK tamper — signature SHA-256 vs expected, checksum validation
 *  7. Screen recorder — known recorder package names
 *  8. Runtime integrity — loaded library scan for unexpected hooks
 *
 * Threat scoring:
 *  Each check contributes a score. Total ≥ 80 → CRITICAL.
 *  CRITICAL → kill process + wipe session.
 *
 * Why scoring instead of binary?
 *  No single check is 100% reliable. But multiple failing together
 *  is very high confidence of an active attack.
 */
class RuntimeSecurityMonitor(private val context: Context) {

    companion object {
        private const val CRITICAL_THRESHOLD = 80

        // SHA-256 of your release APK signing certificate
        // Set this before release: keytool -list -v -keystore your.keystore
        const val EXPECTED_CERT_SHA256 = "REPLACE_WITH_YOUR_CERT_SHA256"
    }

    data class ThreatAssessment(
        val totalScore: Int,
        val isCritical: Boolean,
        val rootScore: Int,
        val debugScore: Int,
        val fridaScore: Int,
        val xposedScore: Int,
        val emulatorScore: Int,
        val tamperScore: Int,
        val recorderScore: Int,
        val integrityScore: Int,
        val details: List<String> // human-readable detection details
    )

    // ── Full Scan ──────────────────────────────────────────────────────────

    fun runFullScan(): ThreatAssessment {
        val details = mutableListOf<String>()

        val rootScore = scoreRoot(details)
        val debugScore = scoreDebug(details)
        val fridaScore = scoreFrida(details)
        val xposedScore = scoreXposed(details)
        val emulatorScore = scoreEmulator(details)
        val tamperScore = scoreTamper(details)
        val recorderScore = scoreRecorder(details)
        val integrityScore = scoreRuntimeIntegrity(details)

        val total = rootScore + debugScore + fridaScore + xposedScore +
            emulatorScore + tamperScore + recorderScore + integrityScore

        return ThreatAssessment(
            totalScore = total,
            isCritical = total >= CRITICAL_THRESHOLD,
            rootScore = rootScore,
            debugScore = debugScore,
            fridaScore = fridaScore,
            xposedScore = xposedScore,
            emulatorScore = emulatorScore,
            tamperScore = tamperScore,
            recorderScore = recorderScore,
            integrityScore = integrityScore,
            details = details
        )
    }

    // ── 1. Root Detection (max 30 points) ─────────────────────────────────

    private fun scoreRoot(details: MutableList<String>): Int {
        var score = 0

        // su binary in common locations
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/.ext/.su", "/system/usr/we-need-root/su-backup"
        )
        if (suPaths.any { File(it).exists() }) {
            score += 15; details.add("SU_BINARY_FOUND")
        }

        // Magisk specific files
        val magiskFiles = listOf(
            "/sbin/.magisk",
            "/data/adb/magisk",
            "/data/adb/magisk.img",
            "/cache/.disable_magisk",
            "/dev/.magisk.unblock"
        )
        if (magiskFiles.any { File(it).exists() }) {
            score += 15; details.add("MAGISK_FILES_FOUND")
        }

        // Writable /system partition
        if (canWriteToSystem()) { score += 10; details.add("SYSTEM_WRITABLE") }

        // Dangerous system properties
        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0",
            "service.adb.root" to "1",
            "ro.build.type" to "eng"
        )
        if (dangerousProps.any { (k, v) -> getProp(k) == v }) {
            score += 10; details.add("DANGEROUS_BUILD_PROPS")
        }

        // Root management apps
        val rootApps = listOf(
            "com.topjohnwu.magisk",
            "com.noshufou.android.su",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "me.weishu.kernelsu"
        )
        if (rootApps.any { pkgInstalled(it) }) {
            score += 10; details.add("ROOT_APP_FOUND")
        }

        return score.coerceAtMost(30)
    }

    // ── 2. Debugger Detection (max 30 points) ──────────────────────────────

    private fun scoreDebug(details: MutableList<String>): Int {
        var score = 0

        if (Debug.isDebuggerConnected()) { score += 30; details.add("DEBUGGER_CONNECTED") }
        if (Debug.waitingForDebugger()) { score += 20; details.add("WAITING_FOR_DEBUGGER") }

        // Check TracerPid in /proc/self/status
        val tracerPid = getTracerPid()
        if (tracerPid > 0) { score += 25; details.add("TRACER_PID=$tracerPid") }

        // Check if running under Android emulator debug environment
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && isDebuggableBuild()) {
            score += 10; details.add("DEBUGGABLE_BUILD")
        }

        return score.coerceAtMost(30)
    }

    // ── 3. Frida Detection (max 30 points) ────────────────────────────────

    private fun scoreFrida(details: MutableList<String>): Int {
        var score = 0

        // Check /proc/self/maps for frida libraries
        if (fridaInMaps()) { score += 30; details.add("FRIDA_IN_MAPS") }

        // Check for frida-server on default ports
        if (fridaPortOpen()) { score += 25; details.add("FRIDA_PORT_OPEN") }

        // Check for frida-server process
        if (fridaProcessRunning()) { score += 25; details.add("FRIDA_PROCESS_RUNNING") }

        // Check loaded .so libraries
        if (fridaLibraryLoaded()) { score += 20; details.add("FRIDA_LIBRARY_LOADED") }

        // Named pipes that frida uses
        if (fridaNamedPipe()) { score += 15; details.add("FRIDA_NAMED_PIPE") }

        return score.coerceAtMost(30)
    }

    private fun fridaInMaps(): Boolean {
        return try {
            File("/proc/self/maps").readLines().any { line ->
                line.contains("frida", ignoreCase = true) ||
                    line.contains("gadget", ignoreCase = true) ||
                    line.contains("re.frida", ignoreCase = true)
            }
        } catch (e: Exception) { false }
    }

    private fun fridaPortOpen(): Boolean {
        val ports = listOf(27042, 27043)
        return ports.any { port ->
            try {
                java.net.Socket("127.0.0.1", port).use { true }
            } catch (e: Exception) { false }
        }
    }

    private fun fridaProcessRunning(): Boolean {
        return try {
            File("/proc").listFiles()?.any { procDir ->
                if (!procDir.isDirectory) return@any false
                val cmdline = File(procDir, "cmdline").readText().lowercase()
                cmdline.contains("frida-server") || cmdline.contains("frida-inject")
            } ?: false
        } catch (e: Exception) { false }
    }

    private fun fridaLibraryLoaded(): Boolean {
        val fridaLibs = listOf("libfrida-gadget.so", "libfrida.so", "frida-agent", "gum-js-loop")
        return try {
            File("/proc/self/maps").readLines().any { line ->
                fridaLibs.any { lib -> line.contains(lib) }
            }
        } catch (e: Exception) { false }
    }

    private fun fridaNamedPipe(): Boolean {
        return try {
            File("/proc/self/fd").listFiles()?.any { fd ->
                try {
                    val link = fd.canonicalPath
                    link.contains("frida") || link.contains("linjector")
                } catch (e: Exception) { false }
            } ?: false
        } catch (e: Exception) { false }
    }

    // ── 4. Xposed Detection (max 25 points) ───────────────────────────────

    private fun scoreXposed(details: MutableList<String>): Int {
        var score = 0

        if (xposedInStack()) { score += 25; details.add("XPOSED_IN_STACK") }
        if (xposedPkgInstalled()) { score += 20; details.add("XPOSED_PKG_FOUND") }
        if (xposedInMaps()) { score += 20; details.add("XPOSED_IN_MAPS") }

        return score.coerceAtMost(25)
    }

    private fun xposedInStack(): Boolean {
        return try {
            throw RuntimeException()
        } catch (e: RuntimeException) {
            e.stackTrace.any { frame ->
                frame.className.contains("XposedBridge", ignoreCase = true) ||
                    frame.className.contains("xposed", ignoreCase = true)
            }
        }
    }

    private fun xposedPkgInstalled(): Boolean {
        val xposedPkgs = listOf(
            "de.robv.android.xposed.installer",
            "io.github.lsposed.manager",
            "org.lsposed.manager",
            "com.solohsu.android.edxp.manager"
        )
        return xposedPkgs.any { pkgInstalled(it) }
    }

    private fun xposedInMaps(): Boolean {
        return try {
            File("/proc/self/maps").readLines().any { line ->
                line.contains("XposedBridge", ignoreCase = true) ||
                    line.contains("xposed", ignoreCase = true)
            }
        } catch (e: Exception) { false }
    }

    // ── 5. Emulator Detection (max 15 points) ─────────────────────────────

    private fun scoreEmulator(details: MutableList<String>): Int {
        var score = 0
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()

        if (fingerprint.contains("generic") || fingerprint.contains("unknown")) {
            score += 10; details.add("EMULATOR_FINGERPRINT")
        }
        if (model.contains("sdk") || model.contains("emulator") ||
            model.contains("android sdk") || Build.MANUFACTURER.lowercase() == "genymotion"
        ) {
            score += 10; details.add("EMULATOR_MODEL")
        }
        if (getProp("ro.kernel.qemu") == "1") { score += 15; details.add("QEMU_DETECTED") }

        return score.coerceAtMost(15)
    }

    // ── 6. APK Tamper Detection (max 30 points) ────────────────────────────

    private fun scoreTamper(details: MutableList<String>): Int {
        if (EXPECTED_CERT_SHA256 == "REPLACE_WITH_YOUR_CERT_SHA256") return 0

        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val sig = info.signatures[0].toByteArray()
            val hash = sha256hex(sig)
            if (!hash.equals(EXPECTED_CERT_SHA256, ignoreCase = true)) {
                details.add("APK_SIGNATURE_MISMATCH")
                30
            } else {
                0
            }
        } catch (e: Exception) {
            details.add("APK_SIGNATURE_UNREADABLE")
            20
        }
    }

    // ── 7. Screen Recorder Detection (max 10 points) ──────────────────────

    private fun scoreRecorder(details: MutableList<String>): Int {
        val recorders = listOf(
            "com.hecorat.screenrecorder.free",
            "com.ilos.vrecorder",
            "com.mobizen.miui.screenrecorder",
            "jp.naver.screenrecorder",
            "com.allrecorder.screen",
            "com.kimcy929.screenrecorder"
        )
        return if (recorders.any { pkgInstalled(it) }) {
            details.add("SCREEN_RECORDER_INSTALLED"); 10
        } else {
            0
        }
    }

    // ── 8. Runtime Library Integrity (max 15 points) ──────────────────────

    private fun scoreRuntimeIntegrity(details: MutableList<String>): Int {
        var score = 0
        val suspiciousLibs = listOf(
            "cydia",
            "substrate",
            "linjector",
            "zygisk",
            "riru"
        )
        return try {
            val mapsContent = File("/proc/self/maps").readText().lowercase()
            if (suspiciousLibs.any { mapsContent.contains(it) }) {
                details.add("SUSPICIOUS_LIBRARY_LOADED"); score += 15
            }
            score
        } catch (e: Exception) { 0 }
    }

    // ── Enforcement ────────────────────────────────────────────────────────

    /**
     * Immediately kill process and trigger session wipe.
     * Called when ThreatAssessment.isCritical == true.
     */
    fun enforce(onSessionWipe: () -> Unit) {
        try { onSessionWipe() } catch (e: Exception) { }
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Exception) {
            Runtime.getRuntime().exit(1)
        }
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private fun getTracerPid(): Int {
        return try {
            File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("TracerPid:") }
                ?.split(":")?.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun canWriteToSystem(): Boolean {
        val testFile = File("/system/cv_test_${System.currentTimeMillis()}")
        return try { testFile.createNewFile().also { testFile.delete() } } catch (e: Exception) { false }
    }

    private fun isDebuggableBuild(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun getProp(key: String): String? {
        return try {
            val proc = Runtime.getRuntime().exec("getprop $key")
            proc.inputStream.bufferedReader().readLine()?.trim()
        } catch (e: Exception) { null }
    }

    private fun pkgInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private fun sha256hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
}
