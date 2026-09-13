package com.calcvault.companion

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.calcvault.utils.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private data class CompanionSession(
    val sessionId: String,
    val cipher: CompanionSessionCrypto.SessionCipher,
    val expiresAt: Long
)

private class InboundChunkCollector(private val tempFile: File) {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val output = FileOutputStream(tempFile)
    var totalBytes: Long = 0L
        private set

    fun append(bytes: ByteArray) {
        output.write(bytes)
        digest.update(bytes)
        totalBytes += bytes.size
    }

    fun finish(): File {
        output.flush()
        output.fd.sync()
        output.close()
        return tempFile
    }

    fun sha256Hex(): String = digest.digest().joinToString("") { "%02x".format(it) }

    fun abort() {
        runCatching { output.close() }
        runCatching { tempFile.delete() }
    }
}

private data class ClientState(
    var session: CompanionSession? = null,
    val restoreCollectors: MutableMap<String, CompanionRestoreCollector> = ConcurrentHashMap(),
    val restoreExpectedSha: MutableMap<String, String?> = ConcurrentHashMap(),
    val uploadCollectors: MutableMap<String, InboundChunkCollector> = ConcurrentHashMap(),
    val uploadMeta: MutableMap<String, Pair<String, String>> = ConcurrentHashMap()
)

class CompanionBridgeServer(
    private val context: Context,
    bindAddress: InetSocketAddress,
    private val pairingState: CompanionPairingState,
    private val backupManager: CompanionBackupManager,
    private val fileVault: CompanionFileVault,
    private val authorizer: CompanionOperationAuthorizer,
    private val onStatus: (String) -> Unit
) : WebSocketServer(bindAddress) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = ConcurrentHashMap<WebSocket, ClientState>()
    private val stagingDir = File(context.cacheDir, "companion_staging")

    override fun onStart() {
        onStatus("bridge_listening")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val resource = handshake.resourceDescriptor ?: ""
        if (!resource.startsWith(CompanionBridgeConstants.BRIDGE_PATH)) {
            conn.close(1008, "invalid_path")
            return
        }

        if (!stagingDir.exists()) stagingDir.mkdirs()
        clients[conn] = ClientState()
        onStatus("client_connected")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val state = clients[conn] ?: return
        runCatching {
            val incoming = JSONObject(message)
            val session = state.session
            if (session == null) {
                handleHandshake(conn, state, incoming)
            } else {
                if (System.currentTimeMillis() > session.expiresAt) {
                    sendSecure(conn, state, JSONObject().put("type", "session_expired"))
                    conn.close(1000, "session_expired")
                    return
                }

                val decrypted = session.cipher.decrypt(incoming)
                handleSecureMessage(conn, state, decrypted)
            }
        }.onFailure { err ->
            Log.w("CompanionBridge", "Message handling failed: ${err.message}")
            val session = state.session
            if (session == null) {
                sendPlain(conn, JSONObject().put("type", "hello_error").put("reason", err.message ?: "bad_request"))
            } else {
                sendSecure(conn, state, JSONObject().put("type", "op_result").put("ok", false).put("error", "bad_request"))
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        clients.remove(conn)?.let { state ->
            state.restoreCollectors.values.forEach { it.abort() }
            state.uploadCollectors.values.forEach { it.abort() }
        }
        onStatus("client_disconnected")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("CompanionBridge", "Bridge error", ex)
    }

    fun shutdown() {
        scope.cancel()
        clients.values.forEach { state ->
            state.restoreCollectors.values.forEach { it.abort() }
            state.uploadCollectors.values.forEach { it.abort() }
        }
        clients.clear()
        runCatching { stop(500) }
    }

    private fun handleHandshake(conn: WebSocket, state: ClientState, hello: JSONObject) {
        if (hello.optString("type") != "hello") {
            throw IllegalArgumentException("expected_hello")
        }

        val pairing = pairingState.current() ?: throw IllegalStateException("pairing_expired")
        val pairingId = hello.optString("pairingId", "")
        val codeHash = hello.optString("codeHash", "")
        val pcPub = hello.optString("pcPub", "")
        val pcNonce = hello.optString("pcNonce", "")

        if (pairingId != pairing.pairingId || codeHash != pairing.codeHashBase64) {
            throw IllegalStateException("pairing_mismatch")
        }

        val phoneEphemeral = CompanionSessionCrypto.generateEphemeralX25519()
        val phoneNonce = CryptoUtils.randomHex(8)
        val deviceId = resolvedDeviceId()
        val proof = CompanionSessionCrypto.computePairingProof(
            pairingCode = pairing.pairingCode,
            sessionId = pairingId,
            pcPub = pcPub,
            phonePub = phoneEphemeral.publicDerBase64,
            pcNonce = pcNonce,
            phoneNonce = phoneNonce,
            deviceId = deviceId
        )

        val sessionKey = CompanionSessionCrypto.deriveSessionKey(
            privateKey = phoneEphemeral.keyPair.private,
            peerPublicDerBase64 = pcPub,
            pairingCode = pairing.pairingCode,
            saltParts = listOf(pairingId, pcNonce, phoneNonce)
        )

        val cipher = CompanionSessionCrypto.SessionCipher(
            sessionId = pairingId,
            sessionKey = sessionKey,
            sendDirection = 2,
            recvDirection = 1
        )

        state.session = CompanionSession(
            sessionId = pairingId,
            cipher = cipher,
            expiresAt = System.currentTimeMillis() + CompanionBridgeConstants.SESSION_TTL_MS
        )

        pairingState.invalidate()

        sendPlain(
            conn,
            JSONObject()
                .put("type", "hello_ack")
                .put("pairingId", pairingId)
                .put("codeHash", codeHash)
                .put("phonePub", phoneEphemeral.publicDerBase64)
                .put("phoneNonce", phoneNonce)
                .put("proof", proof)
                .put(
                    "device",
                    JSONObject()
                        .put("id", deviceId)
                        .put("name", Build.MODEL ?: "CalcVault Android")
                        .put("fingerprint", resolvedFingerprint())
                        .put("model", Build.MODEL ?: "unknown")
                        .put("osVersion", "Android-${Build.VERSION.SDK_INT}")
                )
        )

        onStatus("session_paired")
    }

    private fun handleSecureMessage(conn: WebSocket, state: ClientState, message: JSONObject) {
        when (message.optString("type")) {
            "ping" -> sendSecure(conn, state, JSONObject().put("type", "pong").put("ts", System.currentTimeMillis()))
            "session_ready" -> sendSecure(conn, state, JSONObject().put("type", "pong").put("ts", System.currentTimeMillis()))
            "op_request" -> handleOperationRequest(conn, state, message)
            "op_chunk" -> handleOperationChunk(state, message)
            "op_chunk_end" -> handleOperationChunkEnd(conn, state, message)
        }
    }

    private fun handleOperationRequest(conn: WebSocket, state: ClientState, request: JSONObject) {
        val requestId = request.optString("requestId", "")
        val op = request.optString("op", "")
        val args = request.optJSONObject("args") ?: JSONObject()

        if (requestId.isBlank() || op.isBlank()) {
            throw IllegalArgumentException("invalid_op_request")
        }

        when (val auth = authorizer.authorize(op, requestId)) {
            is AuthorizationResult.Denied -> {
                sendSecure(
                    conn,
                    state,
                    JSONObject()
                        .put("type", "op_authorized")
                        .put("requestId", requestId)
                        .put("approved", false)
                        .put("reason", auth.reason)
                )
                return
            }
            AuthorizationResult.Approved -> {
                sendSecure(
                    conn,
                    state,
                    JSONObject()
                        .put("type", "op_authorized")
                        .put("requestId", requestId)
                        .put("approved", true)
                        .put("token", CryptoUtils.randomHex(12))
                )
            }
        }

        when (op) {
            CompanionBridgeConstants.OP_BACKUP_CREATE -> scope.launch {
                runCatching {
                    sendSecure(
                        conn,
                        state,
                        JSONObject()
                            .put("type", "op_event")
                            .put("requestId", requestId)
                            .put("eventType", "backup_manifest")
                            .put(
                                "data",
                                JSONObject().put("backupVersion", 1)
                            )
                    )

                    val result = backupManager.streamEncryptedBackup { chunk ->
                        sendSecure(
                            conn,
                            state,
                            JSONObject()
                                .put("type", "op_event")
                                .put("requestId", requestId)
                                .put("eventType", "backup_chunk")
                                .put(
                                    "data",
                                    JSONObject().put("chunk", Base64.getEncoder().encodeToString(chunk))
                                )
                        )
                    }

                    sendSecure(
                        conn,
                        state,
                        JSONObject()
                            .put("type", "op_result")
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("result", result)
                    )
                }.onFailure { err ->
                    sendOperationError(conn, state, requestId, err.message ?: "backup_create_failed")
                }
            }

            CompanionBridgeConstants.OP_BACKUP_RESTORE -> {
                val collector = backupManager.createRestoreCollector(requestId, stagingDir)
                state.restoreCollectors[requestId] = collector
                state.restoreExpectedSha[requestId] = args.optString("sha256").takeIf { it.isNotBlank() }
            }

            CompanionBridgeConstants.OP_FILES_LIST -> scope.launch {
                runCatching {
                    val files = fileVault.listFilesJson()
                    sendSecure(
                        conn,
                        state,
                        JSONObject()
                            .put("type", "op_result")
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("result", JSONObject().put("files", files))
                    )
                }.onFailure { err ->
                    sendOperationError(conn, state, requestId, err.message ?: "file_list_failed")
                }
            }

            CompanionBridgeConstants.OP_FILES_UPLOAD -> {
                val temp = File(stagingDir, "upload_${requestId}_${System.currentTimeMillis()}.tmp")
                state.uploadCollectors[requestId] = InboundChunkCollector(temp)
                val fileName = args.optString("fileName", "upload.bin")
                val mimeType = args.optString("mimeType", "application/octet-stream")
                state.uploadMeta[requestId] = fileName to mimeType
            }

            CompanionBridgeConstants.OP_FILES_DOWNLOAD -> scope.launch {
                runCatching {
                    val fileId = args.optString("fileId", "")
                    val mode = args.optString("mode", "encrypted")
                    val result = fileVault.streamFile(fileId, mode) { chunk ->
                        sendSecure(
                            conn,
                            state,
                            JSONObject()
                                .put("type", "op_event")
                                .put("requestId", requestId)
                                .put("eventType", "download_chunk")
                                .put(
                                    "data",
                                    JSONObject().put("chunk", Base64.getEncoder().encodeToString(chunk))
                                )
                        )
                    }

                    sendSecure(
                        conn,
                        state,
                        JSONObject()
                            .put("type", "op_result")
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("result", result)
                    )
                }.onFailure { err ->
                    sendOperationError(conn, state, requestId, err.message ?: "download_failed")
                }
            }

            else -> sendOperationError(conn, state, requestId, "unsupported_operation")
        }
    }

    private fun handleOperationChunk(state: ClientState, message: JSONObject) {
        val requestId = message.optString("requestId", "")
        val chunk = message.optString("chunk", "")
        if (requestId.isBlank() || chunk.isBlank()) {
            throw IllegalArgumentException("invalid_chunk")
        }

        val bytes = Base64.getDecoder().decode(chunk)
        state.restoreCollectors[requestId]?.appendChunk(bytes)
            ?: state.uploadCollectors[requestId]?.append(bytes)
            ?: throw IllegalStateException("collector_missing")
    }

    private fun handleOperationChunkEnd(conn: WebSocket, state: ClientState, message: JSONObject) {
        val requestId = message.optString("requestId", "")
        if (requestId.isBlank()) {
            throw IllegalArgumentException("invalid_chunk_end")
        }

        val restoreCollector = state.restoreCollectors.remove(requestId)
        if (restoreCollector != null) {
            scope.launch {
                runCatching {
                    val expectedSha = state.restoreExpectedSha.remove(requestId)
                    val result = backupManager.finalizeRestore(restoreCollector, expectedSha)
                    sendSecure(
                        conn,
                        state,
                        JSONObject()
                            .put("type", "op_result")
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("result", result)
                    )
                }.onFailure { err ->
                    state.restoreExpectedSha.remove(requestId)
                    restoreCollector.abort()
                    sendOperationError(conn, state, requestId, err.message ?: "restore_failed")
                }
            }
            return
        }

        val uploadCollector = state.uploadCollectors.remove(requestId)
        if (uploadCollector != null) {
            scope.launch {
                runCatching {
                    val plainFile = uploadCollector.finish()
                    val (name, mime) = state.uploadMeta.remove(requestId)
                        ?: ("upload.bin" to "application/octet-stream")
                    val result = fileVault.storePlainFile(plainFile, name, mime)
                    plainFile.delete()

                    sendSecure(
                        conn,
                        state,
                        JSONObject()
                            .put("type", "op_result")
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("result", result)
                    )
                }.onFailure { err ->
                    uploadCollector.abort()
                    sendOperationError(conn, state, requestId, err.message ?: "upload_failed")
                }
            }
            return
        }

        throw IllegalStateException("collector_missing")
    }

    private fun sendOperationError(conn: WebSocket, state: ClientState, requestId: String, error: String) {
        sendSecure(
            conn,
            state,
            JSONObject()
                .put("type", "op_result")
                .put("requestId", requestId)
                .put("ok", false)
                .put("error", error)
        )
    }

    private fun sendPlain(conn: WebSocket, payload: JSONObject) {
        if (conn.isOpen) {
            conn.send(payload.toString())
        }
    }

    private fun sendSecure(conn: WebSocket, state: ClientState, payload: JSONObject) {
        val session = state.session ?: return
        if (!conn.isOpen) return
        val encrypted = session.cipher.encrypt(payload)
        conn.send(encrypted.toString())
    }

    private fun resolvedDeviceId(): String {
        if (com.calcvault.utils.SessionManager.localDeviceId.isNotBlank()) {
            return com.calcvault.utils.SessionManager.localDeviceId
        }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(32)
    }

    private fun resolvedFingerprint(): String {
        val base = "${Build.BRAND}|${Build.DEVICE}|${Build.MODEL}|${Build.FINGERPRINT}"
        return CompanionSessionCrypto.sha256Base64(base)
    }
}
