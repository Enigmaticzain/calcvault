package com.calcvault.network

import java.net.URI

data class SignalingEndpoints(
    val raw: String,
    val httpBaseUrl: String,
    val wsBaseUrl: String
) {
    fun httpUrl(path: String): String = joinBaseAndPath(httpBaseUrl, path)

    fun wsUrl(path: String = "/ws"): String = joinBaseAndPath(wsBaseUrl, path)

    fun wsCandidates(vararg paths: String): List<String> {
        val candidates = linkedSetOf<String>()
        if (paths.isEmpty()) {
            candidates += wsUrl("/ws")
            candidates += wsBaseUrl
        } else {
            paths.forEach { path ->
                val normalized = path.trim()
                candidates += if (normalized.isBlank() || normalized == "/") {
                    wsBaseUrl
                } else {
                    wsUrl(normalized)
                }
            }
        }
        return candidates.toList()
    }
}

object SignalingEndpointResolver {

    private const val DEFAULT_HTTP_BASE = "https://calcvault-signal.onrender.com"
    private val SINGLE_SEGMENT_ENDPOINTS = setOf("register", "signal", "presence", "relay", "ws", "ping")
    private val PAIRED_ENDPOINTS = setOf("lookup", "poll", "heartbeat")

    fun resolve(rawValue: String?, defaultHttpBase: String = DEFAULT_HTTP_BASE): SignalingEndpoints {
        val raw = (rawValue ?: "").trim()
        val fallback = URI(defaultHttpBase)
        val normalizedInput = normalizeInput(raw, fallback)
        val uri = runCatching { URI(normalizedInput) }.getOrNull()
            ?.takeIf { !it.host.isNullOrBlank() }
            ?: fallback

        val secure = when (uri.scheme?.lowercase()) {
            "http", "ws" -> false
            else -> true
        }
        val basePath = normalizeBasePath(uri.path.orEmpty())

        return SignalingEndpoints(
            raw = normalizedInput,
            httpBaseUrl = baseUrlFor(uri, basePath = basePath, secure = secure),
            wsBaseUrl = baseUrlFor(uri, basePath = basePath, websocket = true, secure = secure)
        )
    }

    private fun normalizeInput(raw: String, fallback: URI): String {
        if (raw.isBlank()) return fallback.toString()
        if (raw.contains("://")) return raw
        if (raw.startsWith("//")) {
            return "${defaultScheme(fallback)}:$raw"
        }
        return "${defaultScheme(fallback)}://$raw"
    }

    private fun defaultScheme(fallback: URI): String {
        return when (fallback.scheme?.lowercase()) {
            "http", "ws" -> "http"
            else -> "https"
        }
    }

    private fun normalizeBasePath(path: String): String {
        val segments = path
            .trim()
            .trimEnd('/')
            .split('/')
            .filter { it.isNotBlank() }
            .toMutableList()
        stripKnownEndpointSuffix(segments)
        return if (segments.isEmpty()) "" else "/" + segments.joinToString("/")
    }

    private fun stripKnownEndpointSuffix(segments: MutableList<String>) {
        fun segment(offsetFromEnd: Int): String? {
            val index = segments.size - 1 - offsetFromEnd
            return segments.getOrNull(index)?.lowercase()
        }

        when {
            segments.size >= 4 && segment(3) == "v1" && segment(2) == "relay" && segment(1) == "poll" ->
                repeat(4) { segments.removeAt(segments.lastIndex) }
            segments.size >= 3 && segment(2) == "relay" && segment(1) == "poll" ->
                repeat(3) { segments.removeAt(segments.lastIndex) }
            segments.size >= 3 && segment(2) == "v1" && segment(1) in PAIRED_ENDPOINTS ->
                repeat(3) { segments.removeAt(segments.lastIndex) }
            segments.size >= 2 && segment(1) in PAIRED_ENDPOINTS ->
                repeat(2) { segments.removeAt(segments.lastIndex) }
            segments.size >= 2 && segment(1) == "v1" && segment(0) in SINGLE_SEGMENT_ENDPOINTS ->
                repeat(2) { segments.removeAt(segments.lastIndex) }
            segments.size >= 1 && segment(0) in SINGLE_SEGMENT_ENDPOINTS ->
                segments.removeAt(segments.lastIndex)
        }
    }

    private fun baseUrlFor(uri: URI, basePath: String, websocket: Boolean = false, secure: Boolean): String {
        val scheme = when {
            websocket && secure -> "wss"
            websocket -> "ws"
            secure -> "https"
            else -> "http"
        }
        val portPart = when {
            uri.port <= 0 -> ""
            uri.port == 80 && !secure -> ""
            uri.port == 443 && secure -> ""
            else -> ":${uri.port}"
        }
        return "$scheme://${uri.host}$portPart$basePath"
    }
}

private fun joinBaseAndPath(baseUrl: String, path: String): String {
    val normalizedBase = baseUrl.trimEnd('/')
    if (path.isBlank() || path == "/") return normalizedBase
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    return normalizedBase + normalizedPath
}
