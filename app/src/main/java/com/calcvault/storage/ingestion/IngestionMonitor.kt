package com.calcvault.storage.ingestion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class IngestionMonitor(
    private val context: Context,
    private val engine: BulkDataIngestionEngine
) {
    companion object {
        private const val TAG = "IngestionMonitor"
        private const val IMPORT_DIR = "import"
        private const val POLL_INTERVAL_MS = 5000L
    }

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedFiles = mutableSetOf<String>()

    var onIngestionComplete: (() -> Unit)? = null
    var onIngestionError: ((String) -> Unit)? = null

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch {
            while (isActive) {
                try {
                    checkImportFolder()
                    delay(POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitor loop", e)
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    suspend fun triggerImport(): Boolean {
        val importDir = getImportDirectory()
        if (!importDir.exists() || !importDir.isDirectory) {
            Log.w(TAG, "Import directory does not exist")
            return false
        }

        val files = importDir.listFiles() ?: return true
        if (files.isEmpty()) return true

        val success = engine.ingestDirectory(importDir)
        if (success) {
            processedFiles.clear()
            onIngestionComplete?.invoke()
        }
        return success
    }

    private suspend fun checkImportFolder() {
        val importDir = getImportDirectory()
        if (!importDir.exists()) return

        val files = importDir.listFiles() ?: return
        val newFiles = files.filter { it.absolutePath !in processedFiles }

        if (newFiles.isNotEmpty()) {
            Log.d(TAG, "Found ${newFiles.size} new files to ingest")
            newFiles.forEach { processedFiles.add(it.absolutePath) }

            val success = engine.ingestDirectory(importDir)
            if (success) {
                onIngestionComplete?.invoke()
            } else {
                onIngestionError?.invoke("Ingestion failed")
            }
        }
    }

    fun getImportDirectory(): File {
        val dir = File(context.filesDir, IMPORT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getImportDirectoryPath(): String = getImportDirectory().absolutePath

    fun clearImportFolder() {
        val importDir = getImportDirectory()
        importDir.listFiles()?.forEach { it.delete() }
        processedFiles.clear()
    }

    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }
}
