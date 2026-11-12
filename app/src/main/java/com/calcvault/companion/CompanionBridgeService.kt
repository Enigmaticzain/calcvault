package com.calcvault.companion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.calcvault.BuildConfig
import com.calcvault.storage.USBStorageEngine
import java.net.InetSocketAddress

class CompanionBridgeService : Service() {

    companion object {
        private const val TAG = "CompanionBridgeService"

        @Volatile
        private var lastPairingTicket: CompanionPairingTicket? = null

        /** The only supported companion route is USB/ADB loopback; LAN has no approved file route. */
        fun start(context: Context, bindLan: Boolean = false, autoApprove: Boolean = BuildConfig.DEBUG): Boolean {
            if (bindLan) return false
            val intent = Intent(context, CompanionBridgeService::class.java).apply {
                action = CompanionBridgeConstants.ACTION_START
                putExtra(CompanionBridgeConstants.EXTRA_BIND_LAN, false)
                putExtra(CompanionBridgeConstants.EXTRA_AUTO_APPROVE, autoApprove)
            }
            context.startService(intent)
            return true
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
    private val pairingState = CompanionPairingState()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: CompanionBridgeConstants.ACTION_START
        if (action == CompanionBridgeConstants.ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val bindLan = intent?.getBooleanExtra(CompanionBridgeConstants.EXTRA_BIND_LAN, false) ?: false
        if (bindLan) {
            Log.w(TAG, "LAN companion route rejected: no feature-approved ciphertext-only file transport is configured")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val autoApprove = intent?.getBooleanExtra(CompanionBridgeConstants.EXTRA_AUTO_APPROVE, BuildConfig.DEBUG)
            ?: BuildConfig.DEBUG
        startBridge(autoApprove = autoApprove)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bridgeServer?.shutdown()
        bridgeServer = null
        pairingState.invalidate()
        lastPairingTicket = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBridge(autoApprove: Boolean) {
        if (bridgeServer != null) {
            return
        }

        val ticket = pairingState.issue()
        lastPairingTicket = ticket

        val backupManager = CompanionBackupManager(USBStorageEngine.getInstance(this))
        val fileVault = CompanionFileVault(this)
        val authorizer = CompanionOperationAuthorizer(allowDebugAutoApprove = autoApprove)
        val host = "127.0.0.1"

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

        }.onFailure { err ->
            Log.e(TAG, "Failed to start bridge server", err)
            server.shutdown()
            bridgeServer = null
            pairingState.invalidate()
            lastPairingTicket = null
        }
    }
}
