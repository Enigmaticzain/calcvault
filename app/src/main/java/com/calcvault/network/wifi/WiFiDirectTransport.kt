package com.calcvault.network.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * WiFiDirectTransport
 *
 * Uses Android WiFi P2P API for device-to-device communication
 * without internet. Works within ~200m range.
 *
 * Flow:
 *  1. Discover peers
 *  2. Connect to partner device
 *  3. One device becomes group owner (acts as server socket)
 *  4. Other device connects as client
 *  5. Exchange encrypted payloads over TCP socket
 *
 * All data is already encrypted before reaching this layer.
 * WiFi Direct only transports ciphertext.
 */
class WiFiDirectTransport(private val context: Context) {

    companion object {
        private const val PORT = 8988
        private const val SERVICE_NAME = "calcvault_p2p"
        private const val BUFFER_SIZE = 65_536 // 64KB
    }

    private val manager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onDataReceived: ((ByteArray) -> Unit)? = null
    var onPeerConnected: ((String) -> Unit)? = null
    var onPeerDisconnected: (() -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var isGroupOwner = false
    private var groupOwnerAddr = ""
    private var serverJob: Job? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun initialize() {
        channel = manager.initialize(context, Looper.getMainLooper(), null)
        registerReceiver()
    }

    // ─── Discovery ────────────────────────────────────────────────────────────

    fun discoverPeers(
        onFound: (List<WifiP2pDevice>) -> Unit,
        onFailed: (Int) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            onFailed(WifiP2pManager.ERROR)
            return
        }

        manager.discoverPeers(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.NEARBY_WIFI_DEVICES
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        onFailed(WifiP2pManager.ERROR)
                        return
                    }
                    manager.requestPeers(channel) { peerList ->
                        onFound(peerList.deviceList.toList())
                    }
                }
                override fun onFailure(reason: Int) { onFailed(reason) }
            }
        )
    }

    // ─── Connect ──────────────────────────────────────────────────────────────

    fun connectToPeer(device: WifiP2pDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            onPeerDisconnected?.invoke()
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
        }

        manager.connect(
            channel, config,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // Connection initiated — wait for WIFI_P2P_CONNECTION_CHANGED_ACTION
                }
                override fun onFailure(reason: Int) {
                    onPeerDisconnected?.invoke()
                }
            }
        )
    }

    /**
     * Called from BroadcastReceiver when connection state changes.
     */
    fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        isGroupOwner = info.isGroupOwner
        groupOwnerAddr = info.groupOwnerAddress?.hostAddress ?: ""

        if (info.groupFormed) {
            onPeerConnected?.invoke(groupOwnerAddr)
            if (isGroupOwner) startServer() else connectToServer()
        }
    }

    // ─── Server (Group Owner) ─────────────────────────────────────────────────

    private fun startServer() {
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                while (isActive) {
                    val client = serverSocket!!.accept()
                    handleSocket(client)
                }
            } catch (e: Exception) {
                if (isActive) onPeerDisconnected?.invoke()
            }
        }
    }

    // ─── Client ───────────────────────────────────────────────────────────────

    private fun connectToServer() {
        scope.launch {
            try {
                val socket = Socket(groupOwnerAddr, PORT)
                handleSocket(socket)
            } catch (e: Exception) {
                onPeerDisconnected?.invoke()
            }
        }
    }

    // ─── Socket I/O ───────────────────────────────────────────────────────────

    private var activeSocket: Socket? = null

    private fun handleSocket(socket: Socket) {
        activeSocket = socket
        scope.launch {
            try {
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                while (isActive && !socket.isClosed) {
                    val length = input.readInt()
                    if (length <= 0 || length > 10 * 1024 * 1024) break // max 10MB per packet
                    val data = ByteArray(length)
                    input.readFully(data)
                    onDataReceived?.invoke(data)
                }
            } catch (e: Exception) {
                onPeerDisconnected?.invoke()
            } finally {
                socket.close()
            }
        }
    }

    /**
     * Send encrypted payload to connected peer.
     * Packet format: [4B length][N bytes data]
     */
    fun send(encryptedData: ByteArray): Boolean {
        val socket = activeSocket ?: return false
        return try {
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            out.writeInt(encryptedData.size)
            out.write(encryptedData)
            out.flush()
            true
        } catch (e: Exception) { false }
    }

    // ─── Disconnect ───────────────────────────────────────────────────────────

    fun disconnect() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED
            ) {
                channel?.let { manager.removeGroup(it, null) }
            }
        } catch (e: Exception) {}
        try { serverJob?.cancel() } catch (e: Exception) {}
        try { serverSocket?.close() } catch (e: Exception) {}
        try { activeSocket?.close() } catch (e: Exception) {}
        onPeerDisconnected?.invoke()
    }

    // ─── BroadcastReceiver ────────────────────────────────────────────────────

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.NEARBY_WIFI_DEVICES
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        onPeerDisconnected?.invoke()
                        return
                    }
                    manager.requestPeers(channel) { /* handled in discoverPeers callback */ }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.NEARBY_WIFI_DEVICES
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        onPeerDisconnected?.invoke()
                        return
                    }
                    manager.requestConnectionInfo(channel) { info ->
                        onConnectionInfoAvailable(info)
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> { }
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregister() {
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
    }
}
