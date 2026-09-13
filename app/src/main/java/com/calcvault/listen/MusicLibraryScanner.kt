package com.calcvault.listen

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MusicLibraryScanner {

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "3gp", "amr"
    )

    private const val MUSIC_RELATIVE_PATH = "calcvault/storage/media/music"

    fun scan(context: Context): List<LocalMusicTrack> {
        val tracks = mutableListOf<LocalMusicTrack>()
        val seen = mutableSetOf<String>()

        for (root in candidateRoots(context)) {
            if (!root.exists() || !root.isDirectory) continue

            root.walkTopDown()
                .filter { it.isFile && isMusicFile(it) }
                .forEach { file ->
                    val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
                    if (!seen.add(canonical)) return@forEach

                    val durationMs = readDurationMs(file)
                    tracks += LocalMusicTrack(
                        fileName = file.name,
                        displayName = file.nameWithoutExtension,
                        absolutePath = canonical,
                        sizeBytes = file.length(),
                        durationMs = durationMs
                    )
                }
        }

        return tracks.sortedBy { it.displayName.lowercase() }
    }

    fun ensureHash(track: LocalMusicTrack): LocalMusicTrack {
        if (track.sha256.isNotBlank()) return track
        track.sha256 = sha256Hex(File(track.absolutePath))
        return track
    }

    fun fingerprint(track: LocalMusicTrack): TrackFingerprint {
        val hashed = ensureHash(track)
        return TrackFingerprint(
            trackId = hashed.sha256,
            fileName = hashed.fileName,
            sha256 = hashed.sha256,
            sizeBytes = hashed.sizeBytes,
            durationMs = hashed.durationMs,
            path = hashed.absolutePath
        )
    }

    fun findExactMatch(
        remote: TrackFingerprint,
        localTracks: List<LocalMusicTrack>
    ): LocalMusicTrack? {
        val bySizeAndDuration = localTracks.filter {
            it.sizeBytes == remote.sizeBytes &&
                kotlin.math.abs(it.durationMs - remote.durationMs) <= ListenDriftPolicy.DURATION_MATCH_TOLERANCE_MS
        }

        for (candidate in bySizeAndDuration) {
            val hashed = ensureHash(candidate)
            if (hashed.sha256.equals(remote.sha256, ignoreCase = true)) {
                return hashed
            }
        }
        return null
    }

    private fun candidateRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        roots += File("/calcvault/storage/media/music")

        val legacyExternal = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (legacyExternal != null) {
            roots += File(legacyExternal, MUSIC_RELATIVE_PATH)
        }

        context.getExternalFilesDir(null)?.let {
            roots += File(it, MUSIC_RELATIVE_PATH)
        }

        roots += File(context.filesDir, MUSIC_RELATIVE_PATH)

        val normalized = linkedMapOf<String, File>()
        for (file in roots) {
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            normalized[key] = file
        }
        return normalized.values.toList()
    }

    private fun isMusicFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in SUPPORTED_EXTENSIONS
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
