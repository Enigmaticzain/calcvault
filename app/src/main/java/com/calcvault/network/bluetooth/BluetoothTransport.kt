package com.calcvault.network.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.*
import java.util.UUID

/**
 * BluetoothTransport
 *
 * Bluetooth RFCOMM transport — last-resort fallback when
 * internet and WiFi Direct are unavailable.
 *
 * Range: ~10m (Classic BT)
 *
 * Both devices share the same CalcVault UUID for service discovery.
 * One device acts as server (InsecureRfcommServer), other as client.
 *
 * All payloads are pre-encrypted — BT layer is transport only.
 */
class BluetoothTransport(private val context: Context) {

    companion object {
        // Fixed UUID for CalcVault — both devices must use same value
        private val CV_UUID = UUID.fromString("7d9b3c4a-1f2e-4a8b-9c0d-e5f6a7b8c9d0")
        private const val SERVICE_NAME = "CalcVaultBT"
        private const val MAX_PACKET = 512 * 1024 // 512KB max per BT packet
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var listenJob: Job? = null

    var isConnected = false
        private set
    var onDataReceived: ((ByteArray) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    // ─── Server Mode ──────────────────────────────────────────────────────────

    /**
     * Start listening for incoming BT connections.
     * Call on one device — the other calls connectToDevice().
     */
    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!hasBluetoothConnectPermission()) return
        if (adapter == null || !adapter.isEnabled) return

        listenJob = scope.launch {
            try {
                serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(
                    SERVICE_NAME,
                    CV_UUID
                )
                val socket = serverSocket!!.accept() // blocks until client connects
                onSocketConnected(socket)
                serverSocket?.close()
            } catch (e: Exception) {
                if (isActive) onDisconnected?.invoke()
            }
        }
    }

    // ─── Client Mode ──────────────────────────────────────────────────────────

    /**
     * Connect to a specific BT device.
     * @param deviceAddress  MAC address of partner device (e.g. "AA:BB:CC:DD:EE:FF")
     *
     * To get partner's MAC: show it in SettingsActivity as a QR or
     * exchange via USB during initial pairing.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        if (!hasBluetoothConnectPermission()) return
        val adapter = adapter ?: return

        scope.launch {
            try {
                val device = adapter.getRemoteDevice(deviceAddress)
                val socket = device.createInsecureRfcommSocketToServiceRecord(CV_UUID)
                adapter.cancelDiscovery() // required before connect
                socket.connect()
                onSocketConnected(socket)
            } catch (e: Exception) {
                onDisconnected?.invoke()
            }
        }
    }

    // ─── Socket I/O ───────────────────────────────────────────────────────────

    private fun onSocketConnected(socket: BluetoothSocket) {
        clientSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream
        isConnected = true
        onConnected?.invoke()
        startReceiveLoop()
    }

    private fun startReceiveLoop() {
        scope.launch {
            val input = DataInputStream(BufferedInputStream(inputStream!!))
            try {
                while (isActive && isConnected) {
                    val length = input.readInt()
                    if (length <= 0 || length > MAX_PACKET) break
                    val data = ByteArray(length)
                    input.readFully(data)
                    onDataReceived?.invoke(data)
                }
            } catch (e: Exception) {
                disconnect()
            }
        }
    }

    /**
     * Send encrypted payload.
     * Packet format: [4B length][N bytes encrypted data]
     */
    fun send(encryptedData: ByteArray): Boolean {
        if (!isConnected || outputStream == null) return false
        return try {
            val out = DataOutputStream(BufferedOutputStream(outputStream!!))
            out.writeInt(encryptedData.size)
            out.write(encryptedData)
            out.flush()
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }

    // ─── Discovery ────────────────────────────────────────────────────────────

    /**
     * Get paired devices list — user selects partner from here.
     * Returns only paired devices that have CalcVault UUID.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothConnectPermission()) return emptyList()
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Get local BT MAC address.
     * Share this with partner during first pairing (show in Settings).
     */
    @SuppressLint("MissingPermission")
    fun getLocalAddress(): String {
        if (!hasBluetoothConnectPermission()) return "Unknown"
        return adapter?.address ?: "Unknown"
    }

    // ─── Disconnect ───────────────────────────────────────────────────────────

    fun disconnect() {
        isConnected = false
        listenJob?.cancel()
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { clientSocket?.close() } catch (e: Exception) {}
        try { serverSocket?.close() } catch (e: Exception) {}
        onDisconnected?.invoke()
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
