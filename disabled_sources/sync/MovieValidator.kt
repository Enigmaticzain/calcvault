package com.calcvault.sync

import android.content.Context
import android.media.MediaMetadataRetriever
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

/**
 * MovieValidator - Validates locally stored movies
 * 
 * Responsibilities:
 * - Compute SHA-256 hashes for movie files
 * - Validate file existence and integrity
 * - Extract media metadata (duration, format)
 * - Detect movie mismatches between devices
 */
class MovieValidator(
    private val context: Context,
    private val storageEngine: USBStorageEngine
) {
    companion object {
        const val MOVIE_STORAGE_PATH = "calcvault/storage/media/movies/"
        const val HASH_BUFFER_SIZE = 8192
    }

    /**
     * Compute SHA-256 hash of a movie file
     * Used to uniquely identify movies across devices
     * 
     * @param filePath Full path to movie file
     * @return SHA-256 hash as hex string, or null if file doesn't exist
     */
    suspend fun computeMovieHash(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return@withContext null
            }

            val messageDigest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            var bytesRead: Int

            file.inputStream().buffered().use { stream ->
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    messageDigest.update(buffer, 0, bytesRead)
                }
            }

            messageDigest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Validate that a movie file exists and is accessible
     * 
     * @param filePath Path to check
     * @return true if file exists, is readable, and is a valid video file
     */
    suspend fun validateMovieFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val isValid = file.exists() && 
                         file.canRead() && 
                         file.isFile &&
                         file.length() > 0 &&
                         isVideoFile(file)
            isValid
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract movie metadata using MediaMetadataRetriever
     * 
     * @param filePath Path to movie file
     * @return MovieMetadata with duration and format info
     */
    suspend fun getMovieMetadata(filePath: String): MovieMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return@withContext null
            }

            val hash = computeMovieHash(filePath) ?: return@withContext null
            
            var durationMs = 0L
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = duration?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                // If retriever fails, we can still continue with duration = 0
                e.printStackTrace()
            }

            MovieMetadata(
                filePath = filePath,
                fileHash = hash,
                fileSizeBytes = file.length(),
                durationMs = durationMs,
                fileName = file.name,
                mediaType = getMimeType(filePath)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Compare two movies to check if they're the same
     * Returns true if both file hash AND size match
     */
    suspend fun areMoviesIdentical(
        localPath: String,
        localSize: Long,
        localHash: String,
        remotePath: String,
        remoteSize: Long,
        remoteHash: String
    ): Boolean {
        // First check hash match
        if (localHash != remoteHash) return false
        
        // Then verify size match
        if (localSize != remoteSize) return false
        
        // Verify local file still exists and hasn't been modified
        val verifiedHash = computeMovieHash(localPath)
        return verifiedHash == localHash
    }

    /**
     * Scan movies directory and return list of available movies
     */
    suspend fun scanAvailableMovies(): List<MovieMetadata> = withContext(Dispatchers.IO) {
        try {
            val moviesDir = getMoviesDirectory()
            if (!moviesDir.exists() || !moviesDir.isDirectory) {
                return@withContext emptyList()
            }

            val movies = mutableListOf<MovieMetadata>()
            moviesDir.listFiles()?.forEach { file ->
                if (isVideoFile(file)) {
                    try {
                        val metadata = getMovieMetadata(file.absolutePath)
                        if (metadata != null) {
                            movies.add(metadata)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            movies
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get the standardized movies directory
     */
    fun getMoviesDirectory(): File {
        val baseDir = storageEngine.getBaseStoragePath()
        return File(baseDir, MOVIE_STORAGE_PATH)
    }

    /**
     * Check if file is a recognized video format
     */
    private fun isVideoFile(file: File): Boolean {
        val videoExtensions = setOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".m4v")
        return videoExtensions.any { file.name.lowercase().endsWith(it) }
    }

    /**
     * Determine MIME type based on file extension
     */
    private fun getMimeType(filePath: String): String {
        return when {
            filePath.lowercase().endsWith(".mp4") -> "video/mp4"
            filePath.lowercase().endsWith(".mkv") -> "video/x-matroska"
            filePath.lowercase().endsWith(".webm") -> "video/webm"
            filePath.lowercase().endsWith(".avi") -> "video/x-msvideo"
            filePath.lowercase().endsWith(".mov") -> "video/quicktime"
            else -> "video/*"
        }
    }
}
