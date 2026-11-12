package com.calcvault.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Base64
import com.calcvault.BuildConfig
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.network.privacy.PrivacyControlStore
import com.calcvault.network.privacy.PrivacyFeature
import com.calcvault.network.privacy.PrivacyMode
import com.calcvault.network.privacy.PrivacyTransportGate
import com.calcvault.network.privacy.SafetyContactStore
import com.calcvault.storage.USBStorageEngine
import com.calcvault.utils.PairedUserIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

class NetworkMessageEngine(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") messageDB: AppendOnlyMessageDB,
    @Suppress("UNUSED_PARAMETER") storageEngine: USBStorageEngine
) {
    private val safetyContacts = SafetyContactStore(context)
    private val privacyControls = PrivacyControlStore(context)
    @Volatile
    private var initializedFeatures: Set<PrivacyFeature> = emptySet()
    @Volatile
    private var initializedWithOneTimeWatchConsent = false
    @Volatile
    private var initializedWithOneTimeListenConsent = false
    enum class TransportMode { P2P_HOST, P2P_SRFLX, RELAY, OFFLINE }
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    data class Candidate(
        val ip: String,
        val port: Int,
        val type: TransportMode,
        val priority: Int
    )

    data class UpdateInfo(val code: Int, val name: String, val url: String)

    private data class RelayEnvelope(
        val messageId: Long,
        val senderId: String,
        val receiverId: String,
        val type: Int,
        val timestamp: Long,
        val payload: ByteArray,
        val chunkIndex: Int = 0,
        val chunkCount: Int = 1,
        val mimeType: String = "",
        val fileName: String = "",
        val thumbHash: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("mid", messageId)
            put("from", senderId)
            put("to", receiverId)
            put("type", type)
            put("ts", timestamp)
            put("chunkIndex", chunkIndex)
            put("chunkCount", chunkCount)
            put("payload", Base64.encodeToString(payload, Base64.NO_WRAP))
            if (mimeType.isNotBlank()) put("mimeType", mimeType)
            if (fileName.isNotBlank()) put("fileName", fileName)
            if (thumbHash.isNotBlank()) put("thumbHash", thumbHash)
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
        val json: JSONObject?
    ) {
        val isSuccess: Boolean
            get() = code in 200..299
    }

    private data class PollResult(
        val reachedServer: Boolean,
        val messages: List<JSONObject>
    )

    private data class PresenceSnapshot(
        val reachedServer: Boolean,
        val isOnline: Boolean
    )

    companion object {
        private const val HTTP_TIMEOUT_MS = 8_000
        private const val REGISTER_INTERVAL_MS = 45_000L
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
        private const val POLL_DELAY_IDLE_MS = 1_200L
        private const val POLL_DELAY_BUSY_MS = 150L
        private const val MAX_CHUNK_BYTES = 192 * 1024
        private const val MAX_FAILURES_BEFORE_DISCONNECT = 3
        private const val MAX_POLL_BATCH = 32
        private const val PARTNER_STALE_MS = 75_000L
        private const val OUTBOUND_QUEUE_CAPACITY = 128
        private const val OUTBOUND_RETRY_DELAY_MS = 750L
        private const val MAX_POLL_BACKOFF_MS = 6_000L
    }

    private var engineScope = newEngineScope()
    private val outboundQueue = Channel<RelayEnvelope>(OUTBOUND_QUEUE_CAPACITY)

    private var localUserId = ""
    private var partnerUserId = ""
    private var endpoints = SignalingEndpointResolver.resolve("")
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var consecutiveFailures = 0
    private var lastServerContactAt = 0L
    private var lastPartnerSeenAt = 0L
    private var registrationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var signalPollJob: Job? = null
    private var relayPollJob: Job? = null
    private var outboundProcessorJob: Job? = null

    var currentTransport = TransportMode.OFFLINE
        private set
    var connectionState = ConnectionState.DISCONNECTED
        private set
    var partnerIpAddress: String? = null
        private set

    var onMessageReceived: ((MessageRecord) -> Unit)? = null
    var onPresenceUpdate: ((String, String) -> Unit)? = null
    var onConnectionStateChanged: ((ConnectionState, TransportMode) -> Unit)? = null
    var onUpdateAvailable: ((UpdateInfo) -> Unit)? = null
    var onControlSignalReceived: ((JSONObject) -> Unit)? = null
    var onPrivacyRouteUnavailable: ((String) -> Unit)? = null
    @Volatile
    var privacyRouteUnavailableReason: String? = null
        private set

    /**
     * Start the signaling loops only for a named feature with a currently configured,
     * consented route. A remembered preference is not accepted as proof that a route
     * exists; Watch Together additionally requires this invocation's one-time consent.
     */
    fun initialize(
        localId: String,
        partnerId: String,
        discovery: String,
        relay: String? = null,
        feature: PrivacyFeature? = null,
        oneTimeWatchConsent: Boolean = false,
        additionalFeatures: Set<PrivacyFeature> = emptySet(),
        oneTimeListenConsent: Boolean = false
    ) {
        restartEngineScopeIfNeeded()
        stopLoops()
        privacyRouteUnavailableReason = null
        localUserId = PairedUserIdentity.stableId(localId) ?: localId.trim().ifBlank { PairedUserIdentity.USER_A }
        partnerUserId = PairedUserIdentity.stableId(partnerId) ?: partnerId.trim().ifBlank { PairedUserIdentity.USER_B }
        endpoints = SignalingEndpointResolver.resolve(
            relay?.takeIf { it.isNotBlank() } ?: discovery
        )
        val requestedFeatures = (additionalFeatures + listOfNotNull(feature)).toSet()
        initializedFeatures = requestedFeatures
        initializedWithOneTimeWatchConsent = oneTimeWatchConsent &&
            PrivacyFeature.WATCH_TOGETHER in requestedFeatures
        initializedWithOneTimeListenConsent = oneTimeListenConsent &&
            PrivacyFeature.LISTEN_TOGETHER in requestedFeatures

        val blockedFeature = requestedFeatures.firstOrNull { requested ->
            val mode = privacyControls.mode(requested)
            val routeAvailable = controlRouteAvailable(
                feature = requested,
                mode = mode,
                oneTimeWatchConsent = initializedWithOneTimeWatchConsent &&
                    requested == PrivacyFeature.WATCH_TOGETHER,
                oneTimeListenConsent = initializedWithOneTimeListenConsent &&
                    requested == PrivacyFeature.LISTEN_TOGETHER
            )
            !PrivacyTransportGate.control(
                feature = requested,
                mode = mode,
                selectedRouteAvailable = routeAvailable,
                oneTimeWatchConsent = initializedWithOneTimeWatchConsent &&
                    requested == PrivacyFeature.WATCH_TOGETHER,
                oneTimeListenConsent = initializedWithOneTimeListenConsent &&
                    requested == PrivacyFeature.LISTEN_TOGETHER
            ).allowed
        }
        val gate = if (requestedFeatures.isEmpty()) {
            PrivacyTransportGate.control(
                PrivacyFeature.MESSAGING,
                PrivacyMode.MAX_PRIVACY,
                selectedRouteAvailable = false
            )
        } else if (blockedFeature == null) {
            null
        } else {
            val blockedMode = privacyControls.mode(blockedFeature)
            PrivacyTransportGate.control(
                feature = blockedFeature,
                mode = blockedMode,
                selectedRouteAvailable = controlRouteAvailable(
                    feature = blockedFeature,
                    mode = blockedMode,
                    oneTimeWatchConsent = initializedWithOneTimeWatchConsent &&
                        blockedFeature == PrivacyFeature.WATCH_TOGETHER,
                    oneTimeListenConsent = initializedWithOneTimeListenConsent &&
                        blockedFeature == PrivacyFeature.LISTEN_TOGETHER
                ),
                oneTimeWatchConsent = initializedWithOneTimeWatchConsent &&
                    blockedFeature == PrivacyFeature.WATCH_TOGETHER,
                oneTimeListenConsent = initializedWithOneTimeListenConsent &&
                    blockedFeature == PrivacyFeature.LISTEN_TOGETHER
            )
        }
        if (gate?.allowed == false) {
            initializedFeatures = emptySet()
            initializedWithOneTimeWatchConsent = false
            initializedWithOneTimeListenConsent = false
            privacyRouteUnavailableReason = gate.explanation
            onPrivacyRouteUnavailable?.invoke(gate.explanation)
            disconnect()
            return
        }

        consecutiveFailures = 0
        lastServerContactAt = 0L
        lastPartnerSeenAt = 0L
        updateConnectionState(ConnectionState.CONNECTING, TransportMode.RELAY)
        setupNetworkObserver()
        startRegistrationLoop()
        startHeartbeatLoop()
        startSignalPollLoop()
        startRelayPollLoop()
        startOutboundProcessor()
        triggerImmediateRefresh()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun sendEncryptedPayload(id: Long, data: ByteArray, type: Int): Boolean {
        // No authenticated interoperable content envelope/transport is wired. Respect the
        // Messaging preference for diagnostics, but do not send plaintext or local ciphertext.
        return rejectContent(PrivacyFeature.MESSAGING)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun sendMediaPayload(
        id: Long,
        data: ByteArray,
        type: Int,
        mimeType: String,
        fileName: String = "",
        thumbHash: String = ""
    ): Boolean {
        // File/media content has its own route preference and remains blocked until a real
        // E2EE file transport and a selected route adapter are available.
        return rejectContent(PrivacyFeature.FILE_SHARING)
    }

    private fun rejectContent(feature: PrivacyFeature): Boolean {
        val mode = privacyControls.mode(feature)
        val gate = PrivacyTransportGate.content(
            feature = feature,
            endToEndEncryptionReady = false,
            selectedRouteAvailable = false,
            mode = mode
        )
        privacyRouteUnavailableReason = gate.explanation
        onPrivacyRouteUnavailable?.invoke(gate.explanation)
        return false
    }

    @Suppress("UNUSED_PARAMETER")
    private fun controlRouteAvailable(
        feature: PrivacyFeature,
        mode: PrivacyMode,
        oneTimeWatchConsent: Boolean = false,
        oneTimeListenConsent: Boolean = false
    ): Boolean {
        // HTTPS protects only the client-to-server hop. The configured service does not
        // provide authenticated peer signaling or a ciphertext-only envelope, so it is
        // not an approved route for SDP/ICE, Watch, or Listen control data. TURN config
        // describes a possible media path, not a secure signaling path. Fail closed.
        return false
    }

    private fun controlFeature(type: String, extras: JSONObject): PrivacyFeature? {
        val normalizedType = type.lowercase(java.util.Locale.US)
        return when {
            normalizedType.startsWith("watch_") -> PrivacyFeature.WATCH_TOGETHER
            normalizedType.startsWith("listen_") -> PrivacyFeature.LISTEN_TOGETHER
            normalizedType.startsWith("screenshare_") -> PrivacyFeature.SCREEN_SHARING
            normalizedType.startsWith("call_") || normalizedType.startsWith("webrtc_") -> {
                when (extras.optString("callType").uppercase(java.util.Locale.US)) {
                    "VOICE" -> PrivacyFeature.AUDIO_CALLS
                    "VIDEO" -> PrivacyFeature.VIDEO_CALLS
                    else -> null
                }
            }
            else -> null
        }
    }

    /** Typing status is metadata; keep it local until a feature-approved route exists. */
    fun sendTyping(isTyping: Boolean) {
        if (privacyControls.mode(PrivacyFeature.MESSAGING) == PrivacyMode.MAX_PRIVACY) return
        onPrivacyRouteUnavailable?.invoke(
            "Typing status was not sent: verified end-to-end encryption and an approved messaging route are unavailable."
        )
    }

    suspend fun sendControlSignal(
        type: String,
        extras: JSONObject = JSONObject(),
        oneTimeWatchConsent: Boolean = false,
        oneTimeListenConsent: Boolean = false
    ): Boolean {
        val feature = controlFeature(type, extras)
        if (feature == null || feature !in initializedFeatures) {
            val explanation = "Signaling was not sent: this signal has no active, feature-approved privacy route."
            privacyRouteUnavailableReason = explanation
            onPrivacyRouteUnavailable?.invoke(explanation)
            return false
        }

        val mode = privacyControls.mode(feature)
        val routeAvailable = controlRouteAvailable(
            feature = feature,
            mode = mode,
            oneTimeWatchConsent = oneTimeWatchConsent && initializedWithOneTimeWatchConsent &&
                feature == PrivacyFeature.WATCH_TOGETHER,
            oneTimeListenConsent = oneTimeListenConsent && initializedWithOneTimeListenConsent &&
                feature == PrivacyFeature.LISTEN_TOGETHER
        )
        val gate = PrivacyTransportGate.control(
            feature = feature,
            mode = mode,
            selectedRouteAvailable = routeAvailable,
            oneTimeWatchConsent = oneTimeWatchConsent && initializedWithOneTimeWatchConsent &&
                feature == PrivacyFeature.WATCH_TOGETHER,
            oneTimeListenConsent = oneTimeListenConsent && initializedWithOneTimeListenConsent &&
                feature == PrivacyFeature.LISTEN_TOGETHER
        )
        if (!gate.allowed) {
            privacyRouteUnavailableReason = gate.explanation
            onPrivacyRouteUnavailable?.invoke(gate.explanation)
            return false
        }

        val payload = JSONObject(extras.toString()).apply {
            put("type", type)
            put("from", localUserId)
            put("to", partnerUserId)
            put("ts", System.currentTimeMillis())
        }
        return postJson(endpoints.httpUrl("/signal"), payload)
    }

    private fun startRegistrationLoop() {
        registrationJob?.cancel()
        registrationJob = engineScope.launch {
            while (isActive) {
                val registered = runSafely { registerWithDiscovery() } ?: false
                if (registered) {
                    markHealthy()
                }
                delay(REGISTER_INTERVAL_MS)
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = engineScope.launch {
            while (isActive) {
                val heartbeatOk = runSafely { sendHeartbeat() } ?: false
                val partnerSnapshot = runSafely { refreshPartnerPresence() }
                    ?: PresenceSnapshot(reachedServer = false, isOnline = false)
                if (heartbeatOk || partnerSnapshot.reachedServer) {
                    markHealthy()
                } else {
                    markFailure()
                }
                maybeExpirePartnerPresence()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun startSignalPollLoop() {
        signalPollJob?.cancel()
        signalPollJob = engineScope.launch {
            while (isActive) {
                val hadWork = runSafely { pollSignals() } ?: false
                delay(nextPollDelay(hadWork))
            }
        }
    }

    private fun startRelayPollLoop() {
        relayPollJob?.cancel()
        relayPollJob = engineScope.launch {
            while (isActive) {
                val hadWork = runSafely { pollRelayQueue() } ?: false
                delay(nextPollDelay(hadWork))
            }
        }
    }

    private fun startOutboundProcessor() {
        outboundProcessorJob?.cancel()
        outboundProcessorJob = engineScope.launch {
            for (envelope in outboundQueue) {
                try {
                    val sent = postJson(endpoints.httpUrl("/relay"), envelope.toJson())
                    if (sent) {
                        markHealthy()
                    } else {
                        markFailure()
                        delay(OUTBOUND_RETRY_DELAY_MS)
                        outboundQueue.send(envelope)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    markFailure()
                    delay(OUTBOUND_RETRY_DELAY_MS)
                    outboundQueue.send(envelope)
                }
            }
        }
    }

    private suspend fun pollSignals(): Boolean {
        val result = pollMessages(kind = "signal")
        if (!result.reachedServer) {
            markFailure()
            return false
        }
        markHealthy()
        result.messages.forEach { handleSignalPayload(it) }
        return result.messages.isNotEmpty()
    }

    private suspend fun pollRelayQueue(): Boolean {
        val result = pollMessages(
            kind = "relay",
            fallbackUrl = endpoints.httpUrl("/relay/poll/$localUserId")
        )
        if (!result.reachedServer) {
            markFailure()
            return false
        }
        markHealthy()
        result.messages.forEach { handleRelayPayload(it) }
        return result.messages.isNotEmpty()
    }

    private suspend fun registerWithDiscovery(): Boolean {
        val response = postForJson(
            endpoints.httpUrl("/register"),
            JSONObject().apply {
                put("device_id", localUserId)
                put("device_fingerprint", runCatching { com.calcvault.crypto.E2EKeyManager.getInstance().getDeviceId() }.getOrDefault(""))
                put(
                    "candidates",
                    JSONArray().apply {
                        getInternalIp()?.let { put(JSONObject().apply { put("ip", it); put("type", "host") }) }
                    }
                )
                put("version_code", BuildConfig.VERSION_CODE)
                put("version_name", BuildConfig.VERSION_NAME)
            }
        ) ?: return false

        response.optJSONObject("latest")?.let { latest ->
            val latestCode = latest.optInt("code", BuildConfig.VERSION_CODE)
            if (latestCode > BuildConfig.VERSION_CODE) {
                onUpdateAvailable?.invoke(
                    UpdateInfo(
                        code = latestCode,
                        name = latest.optString("name", ""),
                        url = latest.optString("url", "")
                    )
                )
            }
        }
        return true
    }

    private suspend fun sendHeartbeat(): Boolean {
        return getResponse(endpoints.httpUrl("/heartbeat/$localUserId"))?.isSuccess == true
    }

    private suspend fun refreshPartnerPresence(): PresenceSnapshot {
        val response = getResponse(endpoints.httpUrl("/lookup/$partnerUserId")) ?: run {
            maybeExpirePartnerPresence()
            return PresenceSnapshot(reachedServer = false, isOnline = false)
        }
        if (!response.isSuccess) {
            onPresenceUpdate?.invoke(partnerUserId, "OFFLINE")
            return PresenceSnapshot(
                reachedServer = response.code == HttpURLConnection.HTTP_NOT_FOUND,
                isOnline = false
            )
        }
        val partner = response.json ?: JSONObject()
        val online = partner.optBoolean("online", false)
        if (online) {
            partnerIpAddress = partner.optString("ip").takeIf { it.isNotBlank() }
            markPartnerSeen(partner.optString("deviceId").ifBlank { partner.optString("device_id").ifBlank { partnerUserId } })
        } else {
            partnerIpAddress = null
            maybeExpirePartnerPresence(force = true)
        }
        onPresenceUpdate?.invoke(partnerUserId, if (online) "ONLINE" else "OFFLINE")
        return PresenceSnapshot(reachedServer = true, isOnline = online)
    }

    private suspend fun sendPresenceStatus(status: String): Boolean {
        return postJson(
            endpoints.httpUrl("/presence"),
            JSONObject().apply {
                put("from", localUserId)
                put("to", partnerUserId)
                put("status", status)
            }
        )
    }

    private fun handleSignalPayload(json: JSONObject) {
        val type = inferSignalType(json)
        val isFeatureSignal = type.startsWith("call_") || type.startsWith("webrtc_") ||
            type.startsWith("screenshare_") || type.startsWith("watch_") || type.startsWith("listen_") ||
            type in setOf("offer", "answer", "ice", "bye", "signal")
        val from = json.optString("from")
            .ifBlank { json.optString("device_id") }
            .ifBlank { json.optString("deviceId") }
        val normalizedFrom = PairedUserIdentity.stableId(from) ?: from.trim()
        val normalizedTo = PairedUserIdentity.stableId(json.optString("to")) ?: json.optString("to").trim()
        if (isFeatureSignal && (normalizedFrom.isBlank() || normalizedFrom != partnerUserId ||
                normalizedTo != localUserId)
        ) return
        if (safetyContacts.isBlocked(from) || safetyContacts.isBlocked(normalizedFrom)) return
        if (isFeatureSignal) {
            val feature = controlFeature(type, json) ?: return
            val mode = privacyControls.mode(feature)
            val hasWatchConsent = initializedWithOneTimeWatchConsent &&
                feature == PrivacyFeature.WATCH_TOGETHER
            val hasListenConsent = initializedWithOneTimeListenConsent &&
                feature == PrivacyFeature.LISTEN_TOGETHER
            if (feature !in initializedFeatures ||
                !controlRouteAvailable(feature, mode, hasWatchConsent, hasListenConsent)
            ) return
        }
        when (type) {
            "presence" -> {
                val status = json.optString("status")
                    .ifBlank { json.optString("presence") }
                    .ifBlank { json.optString("state") }
                    .ifBlank { "ONLINE" }
                if (!status.equals("OFFLINE", ignoreCase = true)) {
                    markPartnerSeen(normalizedFrom)
                }
                onPresenceUpdate?.invoke(normalizedFrom, status)
            }
            "call_offer", "call_accept", "call_reject", "call_end", "offer", "answer", "ice", "bye" -> {
                markPartnerSeen(normalizedFrom)
                onControlSignalReceived?.invoke(json)
            }
            else -> {
                if (type.startsWith("listen_") || type.startsWith("screenshare_") ||
                    type.startsWith("watch_") || type == "care_drop"
                ) {
                    markPartnerSeen(from)
                    onControlSignalReceived?.invoke(json)
                } else if (json.has("signal") || json.has("sdp") || json.has("candidate") || json.has("callId")) {
                    markPartnerSeen(from)
                    onControlSignalReceived?.invoke(json)
                } else if (json.has("status") || json.has("presence") || json.has("state")) {
                    onControlSignalReceived?.invoke(json)
                }
            }
        }
    }

    private fun handleRelayPayload(json: JSONObject) {
        val senderId = json.optString("from").ifBlank { json.optString("sid").ifBlank { partnerUserId } }
        if (safetyContacts.isBlocked(senderId)) return
        val type = json.optInt("type", AppendOnlyMessageDB.MSG_TEXT)
        val feature = when (type) {
            AppendOnlyMessageDB.MSG_IMAGE,
            AppendOnlyMessageDB.MSG_VIDEO,
            AppendOnlyMessageDB.MSG_AUDIO,
            AppendOnlyMessageDB.MSG_FILE -> PrivacyFeature.FILE_SHARING
            else -> PrivacyFeature.MESSAGING
        }
        val gate = PrivacyTransportGate.content(
            feature = feature,
            endToEndEncryptionReady = false,
            selectedRouteAvailable = false,
            mode = privacyControls.mode(feature)
        )
        // Reject relay content before base64 decoding, chunk buffering, local decryption, or persistence.
        onPrivacyRouteUnavailable?.invoke("Incoming content ignored: ${gate.explanation}")
    }

    private fun chunkPayload(
        messageId: Long,
        payload: ByteArray,
        type: Int,
        mimeType: String,
        fileName: String,
        thumbHash: String
    ): List<RelayEnvelope> {
        val sender = localUserId
        val receiver = partnerUserId
        if (payload.size <= MAX_CHUNK_BYTES) {
            return listOf(
                RelayEnvelope(
                    messageId = messageId,
                    senderId = sender,
                    receiverId = receiver,
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    payload = payload,
                    mimeType = mimeType,
                    fileName = fileName,
                    thumbHash = thumbHash
                )
            )
        }

        val chunkCount = (payload.size + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES
        val timestamp = System.currentTimeMillis()
        return buildList(chunkCount) {
            var index = 0
            while (index < chunkCount) {
                val start = index * MAX_CHUNK_BYTES
                val end = minOf(start + MAX_CHUNK_BYTES, payload.size)
                add(
                    RelayEnvelope(
                        messageId = messageId,
                        senderId = sender,
                        receiverId = receiver,
                        type = type,
                        timestamp = timestamp,
                        payload = payload.copyOfRange(start, end),
                        chunkIndex = index,
                        chunkCount = chunkCount,
                        mimeType = mimeType,
                        fileName = fileName,
                        thumbHash = thumbHash
                    )
                )
                index++
            }
        }
    }

    private suspend fun getResponse(url: String): HttpResponse? = withContext(Dispatchers.IO) {
        runRequest(url, "GET", null)
    }

    private suspend fun postJson(url: String, payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        runRequest(url, "POST", payload.toString())?.isSuccess == true
    }

    private suspend fun postForJson(url: String, payload: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val response = runRequest(url, "POST", payload.toString()) ?: return@withContext null
        if (!response.isSuccess) return@withContext null
        response.json ?: JSONObject().apply {
            put("ok", true)
            if (response.body.isNotBlank()) {
                put("raw", response.body)
            }
        }
    }

    private fun runRequest(url: String, method: String, body: String?): HttpResponse? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json, text/plain;q=0.9")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }

        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: return null
            BufferedReader(InputStreamReader(stream)).use { reader ->
                val raw = reader.readText()
                HttpResponse(
                    code = responseCode,
                    body = raw,
                    json = parseJsonBody(raw)
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun markHealthy() {
        consecutiveFailures = 0
        lastServerContactAt = System.currentTimeMillis()
        updateConnectionState(ConnectionState.CONNECTED, TransportMode.RELAY)
    }

    private fun markFailure() {
        consecutiveFailures += 1
        maybeExpirePartnerPresence()
        if (consecutiveFailures >= MAX_FAILURES_BEFORE_DISCONNECT) {
            updateConnectionState(ConnectionState.DISCONNECTED, TransportMode.OFFLINE)
        }
    }

    private fun updateConnectionState(state: ConnectionState, transport: TransportMode) {
        if (connectionState == state && currentTransport == transport) return
        connectionState = state
        currentTransport = transport
        onConnectionStateChanged?.invoke(state, transport)
    }

    private fun setupNetworkObserver() {
        if (networkCallback != null) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                consecutiveFailures = 0
                updateConnectionState(ConnectionState.CONNECTING, TransportMode.RELAY)
                triggerImmediateRefresh()
            }

            override fun onLost(network: Network) {
                if (!hasActiveInternetNetwork(manager)) {
                    updateConnectionState(ConnectionState.DISCONNECTED, TransportMode.OFFLINE)
                    maybeExpirePartnerPresence(force = true)
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun getInternalIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val address = addrs.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun disconnect() {
        stopLoops()
        initializedFeatures = emptySet()
        initializedWithOneTimeWatchConsent = false
        initializedWithOneTimeListenConsent = false
        engineScope.cancel()
        networkCallback?.let {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { manager?.unregisterNetworkCallback(it) }
        }
        networkCallback = null
        consecutiveFailures = 0
        lastServerContactAt = 0L
        lastPartnerSeenAt = 0L
        updateConnectionState(ConnectionState.DISCONNECTED, TransportMode.OFFLINE)
    }

    private fun newEngineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun restartEngineScopeIfNeeded() {
        if (!engineScope.isActive) {
            engineScope = newEngineScope()
        }
    }

    private fun stopLoops() {
        registrationJob?.cancel()
        heartbeatJob?.cancel()
        signalPollJob?.cancel()
        relayPollJob?.cancel()
        outboundProcessorJob?.cancel()
        registrationJob = null
        heartbeatJob = null
        signalPollJob = null
        relayPollJob = null
        outboundProcessorJob = null
    }

    private fun nextPollDelay(hadWork: Boolean): Long {
        if (hadWork) return POLL_DELAY_BUSY_MS
        val scaled = POLL_DELAY_IDLE_MS * (consecutiveFailures + 1L)
        return scaled.coerceAtMost(MAX_POLL_BACKOFF_MS)
    }

    private fun triggerImmediateRefresh() {
        if (localUserId.isBlank()) return
        engineScope.launch {
            runSafely { registerWithDiscovery() }?.takeIf { it }?.let { markHealthy() }
            runSafely { sendHeartbeat() }?.takeIf { it }?.let { markHealthy() }
            runSafely { refreshPartnerPresence() }
        }
    }

    private suspend fun pollMessages(kind: String, fallbackUrl: String? = null): PollResult {
        val primaryUrl = "${endpoints.httpUrl("/poll/$localUserId")}?kind=$kind&batch=all&limit=$MAX_POLL_BATCH"
        val primary = parsePollResponse(getResponse(primaryUrl))
        if (primary.reachedServer || fallbackUrl.isNullOrBlank()) {
            return primary
        }
        return parsePollResponse(getResponse(fallbackUrl))
    }

    private fun parsePollResponse(response: HttpResponse?): PollResult {
        if (response == null || !response.isSuccess) {
            return PollResult(reachedServer = false, messages = emptyList())
        }
        val json = response.json ?: return PollResult(reachedServer = true, messages = emptyList())
        if (json.optString("type").equals("none", ignoreCase = true)) {
            return PollResult(reachedServer = true, messages = emptyList())
        }
        if (json.optString("type").equals("batch", ignoreCase = true)) {
            val messages = buildList {
                val array = json.optJSONArray("messages") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(item)
                }
            }
            return PollResult(reachedServer = true, messages = messages)
        }
        return PollResult(reachedServer = true, messages = listOf(json))
    }

    private fun parseJsonBody(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return JSONObject().put("ok", true)
        return runCatching { JSONObject(trimmed) }.getOrNull()
            ?: if (trimmed.equals("ok", ignoreCase = true)) {
                JSONObject().put("ok", true).put("status", trimmed)
            } else {
                null
            }
    }

    private fun inferSignalType(json: JSONObject): String {
        val explicitType = json.optString("type")
        if (explicitType.isNotBlank()) return explicitType
        return when {
            json.has("candidate") || json.has("sdpMid") || json.has("sdpMLineIndex") -> "ice"
            json.has("offer") -> "offer"
            json.has("answer") -> "answer"
            json.has("status") || json.has("presence") || json.has("state") -> "presence"
            json.has("signal") || json.has("sdp") || json.has("callId") -> "signal"
            else -> ""
        }
    }

    private fun markPartnerSeen(partnerId: String) {
        if (partnerId.isBlank()) return
        lastPartnerSeenAt = System.currentTimeMillis()
    }

    private fun maybeExpirePartnerPresence(force: Boolean = false) {
        if (partnerUserId.isBlank()) return
        val now = System.currentTimeMillis()
        val stale = force || (lastPartnerSeenAt > 0L && now - lastPartnerSeenAt >= PARTNER_STALE_MS)
        if (stale) {
            onPresenceUpdate?.invoke(partnerUserId, "OFFLINE")
        }
    }

    private fun hasActiveInternetNetwork(manager: ConnectivityManager): Boolean {
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun <T> runSafely(block: suspend () -> T): T? {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
