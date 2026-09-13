package com.calcvault.cli

import android.content.Context
import com.calcvault.storage.ThumbnailGenerator
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.ingestion.*
import kotlinx.coroutines.runBlocking

class IngestionCLI(private val context: Context) {

    fun handleCommand(args: Array<String>): String {
        return when {
            args.contains("--ingest") -> handleIngest(args)
            args.contains("--list-vault") -> handleListVault()
            args.contains("--vault-size") -> handleVaultSize()
            args.contains("--clear-import") -> handleClearImport()
            else -> "Unknown command. Use: --ingest, --list-vault, --vault-size, --clear-import"
        }
    }

    private fun handleIngest(args: Array<String>): String {
        return runBlocking {
            try {
                val index = VaultFileIndex(context)
                val storageEngine = USBStorageEngine.getInstance(context)
                val thumbnailGen = ThumbnailGenerator(context)
                val engine = BulkDataIngestionEngine(context, storageEngine, index, thumbnailGen)
                val monitor = IngestionMonitor(context, engine)

                val importDir = monitor.getImportDirectory()
                if (!importDir.exists() || importDir.listFiles()?.isEmpty() != false) {
                    return@runBlocking "No files in import folder: ${importDir.absolutePath}"
                }

                var processedCount = 0
                var totalSize = 0L

                engine.onProgress = { progress ->
                    println("Progress: ${progress.processedFiles}/${progress.totalFiles} - ${progress.currentFile}")
                }

                engine.onError = { file, error ->
                    println("Error ingesting $file: ${error.message}")
                }

                val success = monitor.triggerImport()
                if (success) {
                    val files = index.getAllFiles()
                    processedCount = files.size
                    totalSize = index.getTotalSize()
                    "Ingestion complete: $processedCount files, ${formatBytes(totalSize)}"
                } else {
                    "Ingestion failed"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun handleListVault(): String {
        return try {
            val index = VaultFileIndex(context)
            val files = index.getAllFiles()
            if (files.isEmpty()) {
                "Vault is empty"
            } else {
                val sb = StringBuilder("Vault Files:\n")
                files.forEach { file ->
                    sb.append("  ${file.name} (${file.type}) - ${formatBytes(file.size)}\n")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun handleVaultSize(): String {
        return try {
            val index = VaultFileIndex(context)
            val totalSize = index.getTotalSize()
            val fileCount = index.getAllFiles().size
            "Vault: $fileCount files, ${formatBytes(totalSize)}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun handleClearImport(): String {
        return try {
            val monitor = IngestionMonitor(
                context,
                BulkDataIngestionEngine(
                    context,
                    USBStorageEngine.getInstance(context),
                    VaultFileIndex(context),
                    ThumbnailGenerator(context)
                )
            )
            monitor.clearImportFolder()
            "Import folder cleared"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
