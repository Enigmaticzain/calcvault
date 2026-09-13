package com.calcvault.companion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.calcvault.BuildConfig
import com.calcvault.network.SignalingServer
import com.calcvault.storage.USBStorageEngine
import java.net.InetSocketAddress

class CompanionBridgeService : Service() {

    companion object {
        private const val TAG = "CompanionBridgeService"

        @Volatile
        private var lastPairingTicket: CompanionPairingTicket? = null

        fun start(context: Context, bindLan: Boolean = false, autoApprove: Boolean = BuildConfig.DEBUG) {
            val intent = Intent(context, CompanionBridgeService::class.java).apply {
                action = CompanionBridgeConstants.ACTION_START
                putExtra(CompanionBridgeConstants.EXTRA_BIND_LAN, bindLan)
                putExtra(CompanionBridgeConstants.EXTRA_AUTO_APPROVE, autoApprove)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CompanionBridgeService::class.java).apply {
                action = CompanionBridgeConstants.ACTION_STOP
            }
            context.startService(intent)
        }

        fun getLastPairingTicket(): CompanionPairingTicket? = lastPairingTicket
    }

    private var bridgeServer: CompanionBridgeServer? = null
    private var signalingServer: SignalingServer? = null
    private val pairingState = CompanionPairingState()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: CompanionBridgeConstants.ACTION_START
        if (action == CompanionBridgeConstants.ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val bindLan = intent?.getBooleanExtra(CompanionBridgeConstants.EXTRA_BIND_LAN, false) ?: false
        val autoApprove = intent?.getBooleanExtra(CompanionBridgeConstants.EXTRA_AUTO_APPROVE, BuildConfig.DEBUG)
            ?: BuildConfig.DEBUG
        startBridge(bindLan = bindLan, autoApprove = autoApprove)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bridgeServer?.shutdown()
        bridgeServer = null
        signalingServer?.stop()
        signalingServer = null
        pairingState.invalidate()
        lastPairingTicket = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBridge(bindLan: Boolean, autoApprove: Boolean) {
        if (bridgeServer != null) {
            return
        }

        val ticket = pairingState.issue()
        lastPairingTicket = ticket

        val backupManager = CompanionBackupManager(USBStorageEngine.getInstance(this))
        val fileVault = CompanionFileVault(this)
        val authorizer = CompanionOperationAuthorizer(allowDebugAutoApprove = autoApprove)
        val host = if (bindLan) "0.0.0.0" else "127.0.0.1"

        val server = CompanionBridgeServer(
            context = this,
            bindAddress = InetSocketAddress(host, CompanionBridgeConstants.BRIDGE_PORT),
            pairingState = pairingState,
            backupManager = backupManager,
            fileVault = fileVault,
            authorizer = authorizer,
            onStatus = { status -> Log.i(TAG, "Bridge status=$status") }
        )

        runCatching {
            server.start()
            bridgeServer = server
            Log.i(TAG, "Bridge listening on ws://$host:${CompanionBridgeConstants.BRIDGE_PORT}${CompanionBridgeConstants.BRIDGE_PATH}")
            Log.i(TAG, "Pairing code issued: ${ticket.pairingCode} (expires ${ticket.expiresAt})")
            
            // Start Signaling Server on a related port
            val sigServer = SignalingServer(CompanionBridgeConstants.SIGNALING_PORT)
            sigServer.start()
            signalingServer = sigServer
            Log.i(TAG, "Signaling server listening on http://$host:${CompanionBridgeConstants.SIGNALING_PORT}")
            
        }.onFailure { err ->
            Log.e(TAG, "Failed to start bridge server", err)
            server.shutdown()
            bridgeServer = null
            signalingServer?.stop()
            signalingServer = null
            pairingState.invalidate()
            lastPairingTicket = null
        }
    }
}
