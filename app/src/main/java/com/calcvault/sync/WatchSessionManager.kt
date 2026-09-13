package com.calcvault.sync

import android.content.Context
import android.util.Log
import com.calcvault.storage.USBStorageEngine
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * WatchSessionManager - Manages watch session lifecycle
 * * Responsibilities:
 * - Create and manage watch sessions
 * - Store session metadata locally
 * - Validate movie availability before starting
 * - Handle peer-to-peer session coordination
 * - Cleanup on session end
 */
class WatchSessionManager(
    private val context: Context,
    private val storageEngine: USBStorageEngine
) {
    companion object {
        private const val TAG = "WatchSessionManager"
        private const val SESSIONS_FILE = "watch_sessions.json"
        private const val SESSION_TIMEOUT_MS = 3_600_000L // 1 hour timeout
    }

    private val movieValidator = MovieValidator(context, storageEngine)
    private var currentSession: WatchSession? = null
    private var sessionHistoryFile: File? = null

    // Callbacks
    var onSessionCreated: ((WatchSession) -> Unit)? = null
    var onSessionEnded: ((WatchSession) -> Unit)? = null
    var onSessionError: ((String) -> Unit)? = null

    init {
        sessionHistoryFile = File(context.filesDir, SESSIONS_FILE)
    }

    /**
     * Initiate a watch session as HOST
     * * Flow:
     * 1. Validate movie exists on local device
     * 2. Compute movie hash for peer comparison
     * 3. Create session with movie metadata
     * 4. Share session ID to peer
     */
    suspend fun initiateWatchSession(
        moviePath: String,
        peerUserId: String,
        myUserId: String
    ): WatchSession? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initiating watch session for movie: $moviePath")

            // Verify movie exists
            if (!movieValidator.validateMovieFile(moviePath)) {
                val error = "Movie file not validation: $moviePath"
                Log.e(TAG, error)
                onSessionError?.invoke(error)
                return@withContext null
            }

            // Get movie metadata including hash
            val metadata = movieValidator.getMovieMetadata(moviePath)
            if (metadata == null) {
                val error = "Failed to read movie metadata: $moviePath"
                Log.e(TAG, error)
                onSessionError?.invoke(error)
                return@withContext null
            }

            // Create session
            val session = WatchSession(
                movieId = metadata.fileHash,
                movieName = metadata.fileName,
                moviePath = moviePath,
                movieSizeBytes = metadata.fileSizeBytes,
                participants = listOf(myUserId, peerUserId),
                host = myUserId,
                state = PlaybackState.IDLE
            )

            currentSession = session
            saveSessionMetadata(session)
            onSessionCreated?.invoke(session)

            Log.d(TAG, "Watch session created: ${session.sessionId}")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating watch session", e)
            onSessionError?.invoke(e.message ?: "Unknown error")
            null
        }
    }

    /**
     * Join a watch session as FOLLOWER
     * * Flow:
     * 1. Receive session info from peer (host)
     * 2. Validate that we have the same movie locally
     * 3. Verify movie hash matches peer's
     * 4. Join session if validation passes
     */
    suspend fun joinWatchSession(
        sessionId: String,
        peerMovieId: String,
        peerMoviePath: String,
        peerMovieSize: Long,
        peerUserId: String,
        myUserId: String
    ): WatchSession? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting to join watch session: $sessionId")

            // Try to find matching movie locally
            val moviePath = findMatchingMovie(peerMovieId, peerMovieSize)
            if (moviePath == null) {
                val error = "Movie not found locally. Expected hash: $peerMovieId"
                Log.e(TAG, error)
                onSessionError?.invoke(error)
                return@withContext null
            }

            // Double-check hash match
            val localHash = movieValidator.computeMovieHash(moviePath)
            if (localHash != peerMovieId) {
                val error = "Local movie hash mismatch. Local: $localHash, Peer: $peerMovieId"
                Log.e(TAG, error)
                onSessionError?.invoke(error)
                return@withContext null
            }

            // Create follower session matching peer's metadata
            val session = WatchSession(
                sessionId = sessionId,
                movieId = peerMovieId,
                movieName = "", // Will be obtained from peer
                moviePath = moviePath,
                movieSizeBytes = peerMovieSize,
                participants = listOf(myUserId, peerUserId),
                host = peerUserId,
                state = PlaybackState.IDLE
            )

            currentSession = session
            saveSessionMetadata(session)
            onSessionCreated?.invoke(session)

            Log.d(TAG, "Joined watch session: ${session.sessionId}")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Error joining watch session", e)
            onSessionError?.invoke(e.message ?: "Unknown error")
            null
        }
    }

    /**
     * Find a movie locally that matches the peer's movie
     * Searches by hash first, then by size
     */
    private suspend fun findMatchingMovie(
        peerMovieId: String,
        peerMovieSize: Long
    ): String? = withContext(Dispatchers.IO) {
        try {
            val moviesDir = movieValidator.getMoviesDirectory()
            if (!moviesDir.exists()) return@withContext null

            // Scan all movies in storage
            val movies = movieValidator.scanAvailableMovies()

            // Find matching by hash and size
            for (movie in movies) {
                if (movie.fileHash == peerMovieId && movie.fileSizeBytes == peerMovieSize) {
                    Log.d(TAG, "Found matching movie: ${movie.filePath}")
                    return@withContext movie.filePath
                }
            }

            Log.w(TAG, "No matching movie found for hash: $peerMovieId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding matching movie", e)
            null
        }
    }

    /**
     * Update current session state
     */
    fun updateSessionState(newState: PlaybackState, currentTimeMs: Long) {
        currentSession?.let {
            it.state = newState
            it.currentTimeMs = currentTimeMs
            it.lastUpdatedAt = System.currentTimeMillis()
            saveSessionMetadata(it)
        }
    }

    /**
     * Get current active session
     */
    fun getCurrentSession(): WatchSession? = currentSession

    /**
     * End the current watch session
     * Cleanup local metadata
     */
    fun endSession() {
        currentSession?.let {
            Log.d(TAG, "Ending watch session: ${it.sessionId}")
            saveSessionMetadata(it.copy(state = PlaybackState.ENDED))
            onSessionEnded?.invoke(it)
        }
        currentSession = null
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * PERSISTENCE
     * ═══════════════════════════════════════════════════════════════════════════
     */

    /**
     * Save session metadata to local storage
     * For debugging and session history
     */
    private fun saveSessionMetadata(session: WatchSession) {
        try {
            val allSessions = mutableListOf<JSONObject>()

            // Load existing sessions
            if (sessionHistoryFile?.exists() == true) {
                try {
                    val content = sessionHistoryFile!!.readText()
                    val array = org.json.JSONArray(content)
                    for (i in 0 until array.length()) {
                        allSessions.add(array.getJSONObject(i))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading session history", e)
                }
            }

            // Add/update current session
            val sessionJson = JSONObject().apply {
                put("sessionId", session.sessionId)
                put("movieId", session.movieId)
                put("movieName", session.movieName)
                put("moviePath", session.moviePath)
                put("movieSizeBytes", session.movieSizeBytes)
                put("participants", org.json.JSONArray(session.participants))
                put("host", session.host)
                put("state", session.state.name)
                put("currentTimeMs", session.currentTimeMs)
                put("createdAt", session.createdAt)
                put("lastUpdatedAt", session.lastUpdatedAt)
            }

            // Remove old entry if exists
            allSessions.removeIf { it.getString("sessionId") == session.sessionId }

            // Add new entry
            allSessions.add(sessionJson)

            // Write back
            val array = org.json.JSONArray(allSessions)
            sessionHistoryFile?.writeText(array.toString())

            Log.d(TAG, "Session metadata saved: ${session.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving session metadata", e)
        }
    }

    /**
     * Load session history for debugging
     */
    fun getSessionHistory(): List<WatchSession> {
        return try {
            if (sessionHistoryFile?.exists() != true) return emptyList()

            val sessions = mutableListOf<WatchSession>()
            val content = sessionHistoryFile!!.readText()
            val array = org.json.JSONArray(content)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val session = WatchSession(
                    sessionId = obj.getString("sessionId"),
                    movieId = obj.getString("movieId"),
                    movieName = obj.getString("movieName"),
                    moviePath = obj.getString("moviePath"),
                    movieSizeBytes = obj.getLong("movieSizeBytes"),
                    participants = obj.getJSONArray("participants").let {
                        (0 until it.length()).map { idx -> it.getString(idx) }
                    },
                    host = obj.getString("host"),
                    state = PlaybackState.valueOf(obj.getString("state")),
                    currentTimeMs = obj.getLong("currentTimeMs"),
                    createdAt = obj.getLong("createdAt"),
                    lastUpdatedAt = obj.getLong("lastUpdatedAt")
                )
                sessions.add(session)
            }
            sessions
        } catch (e: Exception) {
            Log.e(TAG, "Error loading session history", e)
            emptyList()
        }
    }

    /**
     * Check if session has timed out (no updates for 1 hour)
     */
    fun hasSessionTimedOut(session: WatchSession): Boolean {
        val age = System.currentTimeMillis() - session.lastUpdatedAt
        return age > SESSION_TIMEOUT_MS
    }
}
