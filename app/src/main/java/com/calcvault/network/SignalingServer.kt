package com.calcvault.network

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SignalingServer(private val port: Int) {
    private val TAG = "SignalingServer"
    private var httpThread: Thread? = null
    private var wsServer: SignalingWsServer? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    // Storage for messaging relay
    private val deviceRegistry = ConcurrentHashMap<String, DeviceInfo>()
    private val messageQueues = ConcurrentHashMap<String, MutableList<JSONObject>>()
    private val signalQueues = ConcurrentHashMap<String, MutableList<JSONObject>>()
    
    // WebSocket Room storage for Pairing
    private val rooms = ConcurrentHashMap<String, MutableSet<WebSocket>>()

    data class DeviceInfo(
        val deviceId: String,
        var lastSeen: Long,
        val ip: String
    )

    fun start() {
        if (isRunning.getAndSet(true)) return
        
        // Start HTTP Server for polling/relay
        httpThread = Thread {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "HTTP Signaling server started on port $port")
                while (isRunning.get()) {
                    val client = serverSocket.accept() ?: break
                    executor.execute { handleHttpClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "HTTP Server error", e)
            } finally {
                runCatching { serverSocket?.close() }
            }
        }.apply { start() }

        // Start WebSocket Server for Pairing (on port + 1 for simplicity if same port is hard)
        // Actually, let's try to use port + 1 and update the resolver if needed, 
        // OR better: use port for both if we can detect the protocol. 
        // Since we are using raw ServerSocket for HTTP, we can't easily share with Java-WebSocket.
        // Let's use port + 1 for WebSockets (37113).
        wsServer = SignalingWsServer(port + 1)
        wsServer?.start()
        Log.i(TAG, "WS Signaling server started on port ${port + 1}")
    }

    fun stop() {
        isRunning.set(false)
        httpThread?.interrupt()
        httpThread = null
        wsServer?.stop()
        wsServer = null
        executor.shutdownNow()
    }

    private fun handleHttpClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream())

            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotBlank()) {
                if (line!!.lowercase().startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toInt()
                }
            }

            val body = if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                reader.read(charBuffer, 0, contentLength)
                String(charBuffer)
            } else ""

            val response = processRequest(method, path, body, socket.inetAddress.hostAddress ?: "unknown")
            
            writer.print("HTTP/1.1 ${response.status}\r\n")
            writer.print("Content-Type: application/json\r\n")
            writer.print("Content-Length: ${response.body.length}\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.print(response.body)
            writer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Client handling failed", e)
        } finally {
            runCatching { socket.close() }
        }
    }

    private data class HttpResponse(val status: String, val body: String)

    private fun processRequest(method: String, path: String, body: String, clientIp: String): HttpResponse {
        return try {
            when {
                path == "/register" && method == "POST" -> handleRegister(body, clientIp)
                path.startsWith("/heartbeat/") -> handleHeartbeat(path)
                path.startsWith("/lookup/") -> handleLookup(path)
                path == "/relay" && method == "POST" -> handleRelay(body)
                path == "/signal" && method == "POST" -> handleSignal(body)
                path.startsWith("/poll/") -> handlePoll(path)
                else -> HttpResponse("404 Not Found", "{\"ok\":false,\"error\":\"not_found\"}")
            }
        } catch (e: Exception) {
            HttpResponse("500 Internal Server Error", "{\"ok\":false,\"error\":\"${e.message}\"}")
        }
    }

    private fun handleRegister(body: String, clientIp: String): HttpResponse {
        val json = JSONObject(body)
        val deviceId = json.optString("device_id").ifBlank { json.optString("deviceId") }
        if (deviceId.isBlank()) return HttpResponse("400 Bad Request", "{\"ok\":false}")
        
        deviceRegistry[deviceId] = DeviceInfo(deviceId, System.currentTimeMillis(), clientIp)
        Log.d(TAG, "Registered device: $deviceId from $clientIp")
        return HttpResponse("200 OK", "{\"ok\":true}")
    }

    private fun handleHeartbeat(path: String): HttpResponse {
        val deviceId = path.substringAfterLast("/")
        val info = deviceRegistry[deviceId]
        if (info != null) {
            info.lastSeen = System.currentTimeMillis()
            return HttpResponse("200 OK", "{\"ok\":true}")
        }
        return HttpResponse("404 Not Found", "{\"ok\":false}")
    }

    private fun handleLookup(path: String): HttpResponse {
        val deviceId = path.substringAfterLast("/")
        val info = deviceRegistry[deviceId]
        if (info != null) {
            val online = System.currentTimeMillis() - info.lastSeen < 60000
            return HttpResponse("200 OK", JSONObject().apply {
                put("ok", true)
                put("deviceId", deviceId)
                put("online", online)
                put("ip", info.ip)
            }.toString())
        }
        return HttpResponse("404 Not Found", "{\"ok\":false}")
    }

    private fun handleRelay(body: String): HttpResponse {
        val msg = JSONObject(body)
        val to = msg.optString("to")
        if (to.isBlank()) return HttpResponse("400 Bad Request", "{\"ok\":false}")
        
        messageQueues.getOrPut(to) { mutableListOf() }.add(msg)
        return HttpResponse("200 OK", "{\"ok\":true}")
    }

    private fun handleSignal(body: String): HttpResponse {
        val msg = JSONObject(body)
        val to = msg.optString("to")
        if (to.isBlank()) return HttpResponse("400 Bad Request", "{\"ok\":false}")
        
        signalQueues.getOrPut(to) { mutableListOf() }.add(msg)
        return HttpResponse("200 OK", "{\"ok\":true}")
    }

    private fun handlePoll(path: String): HttpResponse {
        val uri = path.split("?")
        val deviceId = uri[0].substringAfterLast("/")
        val query = if (uri.size > 1) uri[1] else ""
        val isSignal = query.contains("kind=signal")
        
        val queue = if (isSignal) signalQueues[deviceId] else messageQueues[deviceId]
        
        if (queue == null || queue.isEmpty()) {
            return HttpResponse("200 OK", "{\"type\":\"none\"}")
        }

        val batch = JSONArray()
        synchronized(queue) {
            queue.forEach { batch.put(it) }
            queue.clear()
        }

        return HttpResponse("200 OK", JSONObject().apply {
            put("type", "batch")
            put("messages", batch)
        }.toString())
    }

    private inner class SignalingWsServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            Log.d(TAG, "WS Open: ${conn.remoteSocketAddress}")
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            rooms.values.forEach { it.remove(conn) }
        }

        override fun onMessage(conn: WebSocket, message: String) {
            try {
                val json = JSONObject(message)
                if (json.has("join")) {
                    val room = json.getString("join")
                    rooms.getOrPut(room) { mutableSetOf() }.add(conn)
                    
                    val roomSet = rooms[room] ?: return
                    if (roomSet.size >= 2) {
                        val status = JSONObject().put("status", "PEER_CONNECTED")
                        roomSet.forEach { it.send(status.toString()) }
                    }
                } else if (json.has("signal")) {
                    // Broadcast signal to other peer in the same room
                    rooms.values.find { it.contains(conn) }?.forEach { 
                        if (it != conn) it.send(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WS Message error", e)
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.e(TAG, "WS Error", ex)
        }

        override fun onStart() {
            Log.i(TAG, "WS Server started")
        }
    }
}
