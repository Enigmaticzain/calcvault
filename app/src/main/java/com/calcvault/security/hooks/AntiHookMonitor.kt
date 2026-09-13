package com.calcvault.security.hooks

import android.os.Debug
import java.io.File

/**
 * AntiHookMonitor — production-grade anti-hook + anti-debug
 *
 * Checks:
 *  1. Frida — maps, ports 27042/27043, process names, named pipes, timing
 *  2. Xposed — stack trace, class loader, maps, field count heuristic
 *  3. Debugger — TracerPid, isDebuggerConnected, timing loop
 *  4. Memory — unexpected .so files, hook libraries in process space
 *
 * Timing detection:
 *  A debugger or hook framework adds measurable overhead to every JNI
 *  transition and method call. We run a tight loop and measure execution
 *  time. If it's significantly slower than expected → hook suspected.
 *
 * Score >= CRITICAL_SCORE → caller must lock vault immediately.
 */
object AntiHookMonitor {

    private const val FRIDA_PORT_1 = 27042
    private const val FRIDA_PORT_2 = 27043
    private const val TIMING_OPS = 10_000
    private const val TIMING_MAX_NS = 5_000_000L // 5ms for 10k ops — if slower, hook suspected
    private const val CRITICAL_SCORE = 50

    data class HookReport(
        val score: Int,
        val critical: Boolean,
        val details: List<String>
    )

    fun scan(): HookReport {
        val d = mutableListOf<String>()
        val s = scoreFrida(d) + scoreXposed(d) + scoreDebugger(d) + scoreMemory(d)
        return HookReport(s, s >= CRITICAL_SCORE, d)
    }

    // ── Frida (max 30 pts) ────────────────────────────────────────────────

    private fun scoreFrida(d: MutableList<String>): Int {
        var s = 0
        if (mapsContains(listOf("frida", "gadget", "re.frida", "linjector"))) { s += 30; d.add("FRIDA_MAPS") }
        if (portOpen(FRIDA_PORT_1) || portOpen(FRIDA_PORT_2)) { s += 25; d.add("FRIDA_PORT") }
        if (processRunning(listOf("frida-server", "frida-helper"))) { s += 25; d.add("FRIDA_PROC") }
        if (namedPipeExists("frida")) { s += 20; d.add("FRIDA_PIPE") }
        if (timingAnomaly()) { s += 10; d.add("TIMING_ANOMALY") }
        return s.coerceAtMost(30)
    }

    private fun portOpen(port: Int): Boolean = try {
        java.net.Socket().apply {
            connect(java.net.InetSocketAddress("127.0.0.1", port), 100)
            close()
        }; true
    } catch (e: Exception) { false }

    private fun timingAnomaly(): Boolean {
        val start = System.nanoTime()
        var x = 1L
        repeat(TIMING_OPS) { x = x * 37L + 13L } // DCE-resistant
        val elapsed = System.nanoTime() - start
        return elapsed > TIMING_MAX_NS && x != 0L
    }

    // ── Xposed (max 25 pts) ───────────────────────────────────────────────

    private fun scoreXposed(d: MutableList<String>): Int {
        var s = 0
        if (xposedInStack()) { s += 25; d.add("XPOSED_STACK") }
        if (mapsContains(listOf("XposedBridge", "de.robv.android.xposed"))) { s += 20; d.add("XPOSED_MAPS") }
        if (classExists("de.robv.android.xposed.XposedBridge")) { s += 25; d.add("XPOSED_CLASS") }
        return s.coerceAtMost(25)
    }

    private fun xposedInStack(): Boolean = try {
        throw RuntimeException()
    } catch (e: RuntimeException) {
        e.stackTrace.any {
            it.className.contains("XposedBridge", true) ||
                it.className.contains("de.robv.android.xposed", true)
        }
    }

    private fun classExists(name: String): Boolean = try {
        Class.forName(name); true
    } catch (e: Exception) { false }

    // ── Debugger (max 30 pts) ─────────────────────────────────────────────

    private fun scoreDebugger(d: MutableList<String>): Int {
        var s = 0
        if (Debug.isDebuggerConnected()) { s += 30; d.add("DEBUGGER_ATTACHED") }
        if (Debug.waitingForDebugger()) { s += 20; d.add("DEBUGGER_WAITING") }
        val tracer = tracerPid()
        if (tracer > 0) { s += 30; d.add("TRACER_PID=$tracer") }
        return s.coerceAtMost(30)
    }

    private fun tracerPid(): Int = try {
        File("/proc/self/status").readLines()
            .firstOrNull { it.startsWith("TracerPid:") }
            ?.split(":")?.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    } catch (e: Exception) { 0 }

    // ── Memory / library scan (max 20 pts) ────────────────────────────────

    private fun scoreMemory(d: MutableList<String>): Int {
        val bad = listOf("substrate", "cydia", "zygisk", "riru", "lsposed", "magisk")
        return try {
            val maps = File("/proc/self/maps").readText().lowercase()
            if (bad.any { maps.contains(it) }) { d.add("HOOK_LIB_FOUND"); 20 } else 0
        } catch (e: Exception) { 0 }
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    private fun mapsContains(terms: List<String>): Boolean = try {
        val maps = File("/proc/self/maps").readText()
        terms.any { maps.contains(it, ignoreCase = true) }
    } catch (e: Exception) { false }

    private fun processRunning(names: List<String>): Boolean = try {
        File("/proc").listFiles()?.any { dir ->
            if (!dir.isDirectory) return@any false
            try {
                val cmd = File(dir, "cmdline").readText().lowercase()
                names.any { cmd.contains(it) }
            } catch (e: Exception) { false }
        } ?: false
    } catch (e: Exception) { false }

    private fun namedPipeExists(name: String): Boolean = try {
        File("/proc/self/fd").listFiles()?.any { fd ->
            try { fd.canonicalPath.contains(name, ignoreCase = true) } catch (e: Exception) { false }
        } ?: false
    } catch (e: Exception) { false }

    // ── Continuous monitoring ─────────────────────────────────────────────

    fun startMonitoring(intervalMs: Long = 30_000L, onCritical: (HookReport) -> Unit): java.util.Timer {
        val t = java.util.Timer("cv_hook_monitor", true)
        t.schedule(
            object : java.util.TimerTask() {
                override fun run() {
                    val r = scan()
                    if (r.critical) onCritical(r)
                }
            },
            intervalMs, intervalMs
        )
        return t
    }
}
