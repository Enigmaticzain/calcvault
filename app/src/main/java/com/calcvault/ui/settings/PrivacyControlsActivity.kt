package com.calcvault.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.network.privacy.ActivePrivacySessionRegistry
import com.calcvault.network.privacy.PrivacyControlStore
import com.calcvault.network.privacy.PrivacyFeature
import com.calcvault.network.privacy.PrivacyMode
import com.calcvault.network.privacy.PrivacyPreferenceRules
import com.calcvault.network.privacy.SafetyContactStore
import com.calcvault.network.webrtc.WebRtcIcePolicy
import com.calcvault.network.webrtc.WebRtcIceSettingsStore
import com.calcvault.storage.RemoteFileRefManager
import com.calcvault.sync.PermanentPairManager
import com.calcvault.utils.PairedUserIdentity
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.launch
import java.io.File

/** Explicit, reversible privacy preferences. Less-private routes are never selected silently. */
class PrivacyControlsActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var choices: PrivacyControlStore
    private lateinit var safety: SafetyContactStore
    private lateinit var fileRefs: RemoteFileRefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        choices = PrivacyControlStore(this)
        safety = SafetyContactStore(this)
        fileRefs = RemoteFileRefManager(File(filesDir, "calcvault/storage").absolutePath)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 48)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        render()
    }

    private fun render() {
        content.removeAllViews()
        addText("Privacy & Connection Controls", 24f, bold = true)
        addText(
            "Maximum privacy is selected by default. A less-private route requires a separate, " +
                "visible confirmation for each feature. Changes are reversible.",
            15f
        )

        addSection("Feature route preferences")
        PrivacyFeature.values().forEach { feature ->
            val mode = choices.mode(feature)
            addAction(
                feature.label,
                modeLabel(feature, mode, includeAvailability = true),
                "Choose route preference"
            ) { chooseMode(feature) }
        }
        addAction(
            "Reset privacy preferences",
            "Return every feature to maximum privacy; no relay or direct fallback consent remains",
            "Reset"
        ) {
            if (ActivePrivacySessionRegistry.activeSessions().isNotEmpty()) {
                Toast.makeText(
                    this,
                    "End active privacy sessions locally before changing route preferences.",
                    Toast.LENGTH_LONG
                ).show()
                return@addAction
            }
            AlertDialog.Builder(this)
                .setTitle("Reset privacy choices?")
                .setMessage("All feature routes return to maximum privacy. You may need to approve an alternative route later.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset") { _, _ ->
                    if (ActivePrivacySessionRegistry.activeSessions().isNotEmpty()) {
                        Toast.makeText(
                            this,
                            "End active privacy sessions locally before changing route preferences.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        choices.resetToMaximumPrivacy()
                    }
                    render()
                }
                .show()
        }

        addSection("Encryption status")
        addText(
            "App-wide end-to-end encryption is not currently verified. This screen does not enable " +
                "encryption or make a security claim. Do not send sensitive information over a route " +
                "whose encryption status is shown as unverified.",
            15f
        )

        addSection("Active local sessions")
        val activeSessions = ActivePrivacySessionRegistry.activeSessions()
        if (activeSessions.isEmpty()) {
            addText("No active call, screen-share, or Watch Together session is registered in this process.", 14f)
        } else {
            activeSessions.forEach { session ->
                addAction(
                    "${session.feature} with ${session.peerId}",
                    "Started ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(session.startedAtMs))}",
                    "End locally"
                ) { confirmEndSession(session.id, session.feature) }
            }
        }

        addSection("Call and screen-share ICE infrastructure")
        addAction(
            "Configure STUN/TURN servers",
            "Credentials are stored with Android encrypted preferences; no public STUN server is preselected",
            "Configure"
        ) { configureIceServers() }

        addSection("Paired device")
        PermanentPairManager.init(this)
        val pairedStatus = if (PermanentPairManager.isPaired) {
            "Identity: ${PermanentPairManager.myRole}; paired device ID: " +
                PermanentPairManager.getPartnerDeviceId().ifBlank { "not recorded" }
        } else {
            "No permanent paired identity is configured."
        }
        addText(pairedStatus, 15f)
        if (PermanentPairManager.isPaired) {
            addAction(
                "Forget paired device",
                "Clears this device's saved pair identity only; it does not erase partner data",
                "Forget"
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Forget this pairing?")
                    .setMessage("This clears the local pairing identity and requires a new explicit setup. It cannot revoke copies or credentials already held by the other device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Forget") { _, _ ->
                        PermanentPairManager.forgetPair()
                        recreate()
                    }
                    .show()
            }
        }

        addSection("Blocked contacts & reports")
        addAction("Block a contact", "Ignore its incoming messages and calls on this device", "Add") {
            promptPeerId("Block contact") { peer ->
                safety.block(peer)
                render()
            }
        }
        safety.blockedPeers().sorted().forEach { peer ->
            addAction("Blocked: $peer", "Tap to unblock", "Unblock") {
                AlertDialog.Builder(this)
                    .setTitle("Unblock $peer?")
                    .setMessage("Incoming messages and calls from this contact will be accepted again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Unblock") { _, _ ->
                        safety.unblock(peer)
                        render()
                    }
                    .show()
            }
        }
        addAction("Create a local report", "Reports are saved on this device only; they are not uploaded", "Report") {
            reportContact()
        }
        addText("Local reports saved: ${safety.reportCount()}", 14f)

        addSection("File access")
        addText(
            "Grants are checked by this device's local storage access API. Revoking or expiring a grant " +
                "blocks future checks, but cannot erase copies already downloaded or captured. The " +
                "current app has no verified remote file-transfer service, so a local grant is not a " +
                "network-wide revocation guarantee.",
            15f
        )
        addAction("Manage a file grant", "Grant access with optional expiry, or revoke future access", "Open") {
            showFileAccessManager()
        }
    }

    private fun configureIceServers() {
        val saved = WebRtcIceSettingsStore.loadOrEmpty(this)
        val stunInput = EditText(this).apply {
            hint = "stun:host:3478 or stuns:host:5349 (one per line)"
            setText(saved.stunUrls.joinToString("\n"))
            minLines = 2
        }
        val turnInput = EditText(this).apply {
            hint = "turn:host:3478 or turns:host:5349 (one per line)"
            setText(saved.turnUrls.joinToString("\n"))
            minLines = 2
        }
        val usernameInput = EditText(this).apply {
            hint = "TURN username"
            setText(saved.turnUsername)
        }
        val passwordInput = EditText(this).apply {
            hint = "TURN credential"
            setText(saved.turnPassword)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(stunInput)
            addView(turnInput)
            addView(usernameInput)
            addView(passwordInput)
        }
        AlertDialog.Builder(this)
            .setTitle("ICE server settings")
            .setMessage(
                "STUN providers can learn your public IP. TURN providers see your IP, timing and traffic volume. " +
                    "These settings are stored for future ICE use only; calls and screen sharing remain blocked " +
                    "until an authenticated ciphertext-only signaling route is implemented. Use credentials from a service you trust."
            )
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save encrypted") { _, _ ->
                val stunUrls = splitServerList(stunInput.text.toString())
                val turnUrls = splitServerList(turnInput.text.toString())
                if (stunUrls.any { !WebRtcIcePolicy.isStunUrl(it) } ||
                    turnUrls.any { !WebRtcIcePolicy.isTurnUrl(it) }
                ) {
                    Toast.makeText(this, "Use stun:/stuns: URIs for STUN and turn:/turns: URIs for TURN", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (turnUrls.isNotEmpty() && (username.isBlank() || password.isBlank())) {
                    Toast.makeText(this, "TURN URLs require both a username and credential", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val stored = runCatching {
                    WebRtcIceSettingsStore(this).save(
                        stunUrls.joinToString("\n"),
                        turnUrls.joinToString("\n"),
                        username,
                        password
                    )
                }.getOrDefault(false)
                Toast.makeText(
                    this,
                    if (stored) "ICE settings saved encrypted at rest" else "Could not save ICE settings",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun splitServerList(value: String): List<String> = value
        .split(',', '\n', '\r', ';')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    private fun confirmEndSession(sessionId: String, feature: String) {
        AlertDialog.Builder(this)
            .setTitle("End $feature?")
            .setMessage("This ends the session locally on this device. The peer may not receive a remote end notification if signaling is unavailable.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("End locally") { _, _ ->
                ActivePrivacySessionRegistry.endLocally(sessionId)
                render()
            }
            .show()
    }

    private fun chooseMode(feature: PrivacyFeature) {
        if (hasActiveSession(feature)) {
            Toast.makeText(
                this,
                "End the active ${feature.label} session locally before changing its route.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val modes = PrivacyMode.values().filter { PrivacyPreferenceRules.canSelect(feature, it) }
        val labels = modes.map { modeLabel(feature, it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${feature.label} route")
            .setItems(labels) { _, index -> confirmMode(feature, modes[index]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmMode(feature: PrivacyFeature, mode: PrivacyMode) {
        if (hasActiveSession(feature)) {
            Toast.makeText(
                this,
                "End the active ${feature.label} session locally before changing its route.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val disclosure = PrivacyPreferenceRules.disclosure(feature, mode)
        val title = if (mode == PrivacyMode.MAX_PRIVACY) "Use maximum privacy?" else "Allow this route?"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(disclosure)
            .setNegativeButton("Keep current choice", null)
            .setPositiveButton(if (mode == PrivacyMode.MAX_PRIVACY) "Use" else "I understand; allow") { _, _ ->
                when {
                    hasActiveSession(feature) -> Toast.makeText(
                        this,
                        "End the active ${feature.label} session locally before changing its route.",
                        Toast.LENGTH_LONG
                    ).show()
                    !choices.setMode(feature, mode, disclosureConfirmed = true) -> Toast.makeText(
                        this,
                        "Could not save this privacy route choice.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                render()
            }
            .show()
    }

    private fun showFileAccessManager() {
        lifecycleScope.launch {
            val refs = fileRefs.getAllRefs()
            if (refs.isEmpty()) {
                Toast.makeText(this@PrivacyControlsActivity, "No local file references to manage", Toast.LENGTH_LONG).show()
                return@launch
            }
            val labels = refs.map { ref ->
                "${ref.name} · ${ref.id.take(10)} · local ${if (ref.revoked) "revoked" else "grant metadata"}"
            }.toTypedArray()
            AlertDialog.Builder(this@PrivacyControlsActivity)
                .setTitle("Choose a file (shown locally only)")
                .setItems(labels) { _, index -> showFileGrantActions(refs[index]) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showFileGrantActions(ref: com.calcvault.storage.UserOwnershipManager.RemoteFileRef) {
        AlertDialog.Builder(this)
            .setTitle("File ${ref.id.take(12)}")
            .setItems(arrayOf("Edit local grant metadata", "Revoke local grant metadata")) { _, index ->
                if (index == 0) promptFileGrant(ref.id) else revokeFileGrant(ref.id, null)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptFileGrant(fileId: String) {
        val recipient = EditText(this).apply { hint = "Recipient device/user ID" }
        val expiryHours = EditText(this).apply {
            hint = "Expires in hours (0 = no expiry)"
            setText("24")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(recipient)
            addView(expiryHours)
        }
        AlertDialog.Builder(this)
            .setTitle("Save local grant metadata")
            .setMessage("This only edits a local reference on this device. No owner-device service currently enforces or synchronizes this grant, so it does not authorize an actual remote transfer. It cannot revoke any copy already downloaded.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Grant") { _, _ ->
                val userId = recipient.text.toString().trim()
                val hours = expiryHours.text.toString().toLongOrNull()?.coerceIn(0L, 87_600L) ?: 24L
                val expiresAt = if (hours == 0L) 0L else System.currentTimeMillis() + hours * 60 * 60 * 1000
                lifecycleScope.launch {
                    val result = fileRefs.grantAccess(
                        id = fileId,
                        ownerId = SessionManager.localUserId.ifBlank { PairedUserIdentity.USER_A },
                        userId = userId,
                        expiresAtMs = expiresAt
                    )
                    Toast.makeText(
                        this@PrivacyControlsActivity,
                        if (result) "Local metadata saved only; remote access is unchanged" else "Local metadata rejected: file owner or expiry is invalid",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun revokeFileGrant(fileId: String, userId: String?) {
        AlertDialog.Builder(this)
            .setTitle("Revoke local grant metadata?")
            .setMessage("This updates only this device's reference. No owner-side enforcement or remote transfer is connected, and it cannot delete any copy already obtained.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Revoke") { _, _ ->
                lifecycleScope.launch {
                    val result = fileRefs.revokeAccess(
                        id = fileId,
                        ownerId = SessionManager.localUserId.ifBlank { PairedUserIdentity.USER_A },
                        userId = userId
                    )
                    Toast.makeText(
                        this@PrivacyControlsActivity,
                        if (result) "Local grant metadata revoked; remote access is unchanged" else "Could not revoke local metadata: this device may not own the reference",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun reportContact() {
        val peer = EditText(this).apply { hint = "Contact ID" }
        val reason = EditText(this).apply { hint = "Reason (stored locally only)" }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(peer)
            addView(reason)
        }
        AlertDialog.Builder(this)
            .setTitle("Save local report")
            .setMessage("This report stays on this device and is not sent to a service.")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                safety.report(peer.text.toString(), reason.text.toString())
                render()
            }
            .show()
    }

    private fun promptPeerId(title: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply { hint = "Contact ID" }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> onSave(input.text.toString()) }
            .show()
    }

    private fun hasActiveSession(feature: PrivacyFeature): Boolean =
        ActivePrivacySessionRegistry.activeSessions().any { it.feature == feature.label }

    private fun modeLabel(
        feature: PrivacyFeature,
        mode: PrivacyMode,
        includeAvailability: Boolean = false
    ): String {
        val selectedMode = when (mode) {
            PrivacyMode.MAX_PRIVACY -> if (feature.supportsOnion) {
                "Tor/onion only; no less-private fallback consent"
            } else {
                "Maximum privacy; no less-private fallback consent"
            }
            PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT -> when {
                feature.isRealtime -> "TURN relay only; no direct fallback"
                feature.supportsOnion -> "Tor/onion first, then consented encrypted-relay fallback"
                else -> "Consented encrypted relay"
            }
            PrivacyMode.DIRECT_WITH_IP_WARNING -> when {
                feature.isRealtime -> "TURN first (separate consent), then direct with public-IP disclosure"
                feature.supportsOnion -> "Tor first, encrypted relay (separate consent), then direct with public-IP disclosure"
                else -> "Encrypted relay (separate consent), then direct with public-IP disclosure"
            }
        }
        if (!includeAvailability) return selectedMode

        val unavailableReason = when (feature) {
            PrivacyFeature.MESSAGING,
            PrivacyFeature.GROUP_MESSAGING,
            PrivacyFeature.FILE_SHARING -> when (mode) {
                PrivacyMode.MAX_PRIVACY ->
                    "the selected Tor/onion-only route and verified E2EE are not available; sends stay local"
                PrivacyMode.ENCRYPTED_RELAY_WITH_CONSENT ->
                    "the selected Tor-first/ciphertext-relay route and verified E2EE are not available; no payload is sent"
                PrivacyMode.DIRECT_WITH_IP_WARNING ->
                    "the selected Tor/relay/direct route and verified E2EE are not available; no peer IP is contacted and no payload is sent"
            } + "; USB local-only companion backup/file operations are separate"
            PrivacyFeature.AUDIO_CALLS,
            PrivacyFeature.VIDEO_CALLS,
            PrivacyFeature.SCREEN_SHARING ->
                "no authenticated, ciphertext-only signaling route is implemented; direct mode also lacks a staged TURN-first then IP-disclosing fallback, so ICE candidates are not gathered. ICE server settings alone do not enable calls or screen sharing"
            PrivacyFeature.LOCATION_PRESENCE -> "no approved network route is implemented"
            PrivacyFeature.WATCH_TOGETHER,
            PrivacyFeature.LISTEN_TOGETHER ->
                "no authenticated, ciphertext-only control signaling route is implemented; consent cannot enable an unavailable transport"
        }
        return if (unavailableReason == null) {
            "$selectedMode — route reachability is checked when the feature starts"
        } else {
            "$selectedMode — unavailable: $unavailableReason"
        }
    }

    private fun addSection(title: String) {
        addText(title, 18f, bold = true)
    }

    private fun addText(text: String, size: Float, bold: Boolean = false) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(12, 12, 12, 12)
        })
    }

    private fun addAction(title: String, detail: String, action: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val text = TextView(this).apply {
            this.text = "$title\n$detail"
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(text)
        row.addView(TextView(this).apply {
            this.text = action
            textSize = 14f
            setPadding(12, 8, 4, 8)
        })
        content.addView(row)
        content.addView(android.view.View(this).apply {
            setBackgroundColor(0x33888888)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })
    }
}
