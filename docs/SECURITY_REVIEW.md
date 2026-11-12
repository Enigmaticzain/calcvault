# CalcVault privacy, transport, and call security review

**Review date:** 2026-11-12  
**Target:** current working-tree changes on `arena/b28b3bc0-calcvault`  
**Decision:** **NO-GO for production/security claims.** Static review only; this is not an independent audit or release clearance.

## Work reviewed and implemented in this change set

- Each privacy feature can save a route preference: maximum privacy (Tor/onion only where supported), Tor-first then consented ciphertext relay for supported asynchronous features, TURN relay for real-time media, or safer routes followed by explicit direct/IP disclosure. These are preferences/plans only; no active adapter is available, so the operation gates reject content and control traffic.
- Messaging and File Sharing outbound content remains fail-closed without verified E2EE and a feature-approved content route. Chat reports messages as not sent rather than claiming success.
- The raw HTTP `SignalingServer`, legacy direct `PeerManager`, and old Android WebSocket `SecurePairingManager` are fail-closed compatibility facades. Settings now explain that secure pairing is unavailable rather than presenting a code as if it were connecting.
- The companion's unauthenticated `/register`, `/heartbeat`, `/lookup`, `/relay`, `/signal`, and `/poll` signaling APIs now return HTTP 410 without importing the legacy relay service. Pairing WebSocket upgrade paths are rejected; the authenticated local UI event socket is handled once by the centralized upgrade handler.
- Android companion bridge startup rejects LAN and only binds to `127.0.0.1` for USB/ADB. The Ubuntu companion disables its network adapter in UI and backend, irrespective of legacy opt-in config, and reports route unavailability. There is no silent direct fallback.
- Settings no longer offer an HTTP/direct messaging server. A custom signaling URL must use HTTPS, which is signaling only and does not enable content sends or establish E2EE.
- Existing call, screen-share, ICE policy, and recovery changes are configuration gates, not proof of an active anonymous/relay route or secure peer identity.

The direct-LAN rejection test and companion validation pass locally (details below). Android build, unit tests, two-device testing, and security validation have not succeeded.

## Critical/high findings

### 1. No authenticated peer identity or approved signaling route

The underlying `NetworkMessageEngine` POST implementation serializes ordinary JSON to the configured HTTPS endpoint. The active route gate now rejects all call, screen-share, Watch, and Listen signaling because no authenticated ciphertext-only signaling adapter exists; ICE settings, HTTPS, one-time consent, or stored route choices cannot make this transport approved. The gate is a deliberate feature outage, not protection against a future bypass. If the gate were bypassed, HTTPS would not authenticate the peer, bind sender/recipient IDs to cryptographic identities, or prevent a malicious/compromised service from replacing SDP/ICE or injecting events. IDs and call/room correlation fields are not signatures or replay protection.

**Impact:** call, screen-share, Watch Together, and Listen Together signaling is unavailable. Do not re-enable signaling until authenticated identity, ciphertext-only envelopes, replay protection, and transport validation are implemented.

### 2. No app-level E2EE content transport

Messaging/file payload sends return false before enqueue/network transmission, and inbound relay content is rejected before decoding, buffering, decryption, or persistence. The UI reports not-sent/unavailable. The ephemeral-key/HKDF utility has no authenticated identities, key-rotation enforcement, replay-protected envelope, or integration into the transport. This is fail-closed behavior, not an encryption implementation.

**Impact:** partner content exchange is unavailable. No Tor/onion adapter or ciphertext-only content relay is integrated. Do not enable sends or imply that a configured HTTPS signaling endpoint is an encrypted content route.

### 3. Legacy direct transports are blocked; supported USB companion remains separate

The raw `SignalingServer` and direct `PeerManager` no longer open network sockets. The Android companion service rejects LAN requests and binds its supported bridge to loopback; the Ubuntu companion disables direct LAN both in UI and backend, even if legacy config requests it. A companion integration validation confirms a LAN connection attempt is rejected and the app remains disconnected. The supported USB/ADB bridge handles paired backup/file-vault operations, not partner messaging or privacy-route selection.

**Impact:** direct legacy paths fail closed, but USB bridge functionality is not a Tor/relay solution and is not evidence of partner-content E2EE. Companion cryptography and operation authorization need independent review before security claims.

### 4. Call/WebRTC and recovery behavior remain unavailable/unverified

ICE policy and settings code describe TURN-only or explicit direct media choices, and recovery code uses a finite same-route retry budget. The control gate prevents all call and screen-share signaling because no authenticated ciphertext-only adapter exists. ICE configuration does not enable a usable call route. A review also found that `IceTransportPolicy.ALL` would gather direct candidates before the displayed TURN-first fallback order; direct mode now fails closed before ICE gathering until staged fallback exists. Call lifecycle, permission, foreground service, identity/recipient checks, notification controls, lazy native resource creation and bitrate changes were hardened, but remain uncompiled and cannot prove Android API compatibility, endpoint reachability, TURN-only behavior, peer-IP non-disclosure, correct reconnection, or native media cleanup. No Android build or two-device/NAT testing has passed. See [CALL_TRANSPORT_AUDIT.md](CALL_TRANSPORT_AUDIT.md).

**Impact:** calls/screen sharing do not work through network signaling in this build. Actual Tor/onion-first routing for supported IP-private features has not been integrated.

### 5. Remote file grants and PC backups remain incomplete

The local paired-file path now writes file chunks and metadata through the active encrypted `StorageManager` provider, commits metadata after the chunks, checks the two-user owner/grant policy before local export, and verifies the exported byte count and SHA-256 digest. On USB, logical deletes are recorded as encrypted tombstones and filtered when the index is rebuilt. This is a local vault improvement; it does not implement remote delivery or owner-side authorization at a peer.

`RemoteFileRefManager` still stores local named-user grants/expiry, but no owner-side network API enforces them. Revocation cannot remove an already exported copy, and local JSON metadata is not a cryptographically bound remote capability. Separately, `PCBackupManager`'s `encryptBackup` option and backup-index metadata require further review; the flag must not be treated as proof that the generated filesystem backup is encrypted.

**Impact:** local encrypted file storage/export has not been Android-compiled or runtime-tested. Do not promise remote access control, secure remote sharing, encrypted PC backup, or recall of copies.

### 6. Watch/Listen synchronization is unavailable

Watch/Listen network control is now blocked before POST because no authenticated ciphertext-only route exists. Local playback remains available; sync and invitations do not. Older code paths can form plaintext JSON and must not be re-enabled without a reviewed authenticated envelope and route.

## Validation performed in this continuation

- `cd companion && npm ci --ignore-scripts` succeeded after dependency updates; `npm audit --omit=dev` reported **0 vulnerabilities**.
- `cd companion && npm test` passed: 4 test cases. The ephemeral server test checks unauthenticated legacy REST signaling routes return 410 and pairing WebSocket upgrades are rejected; other tests cover direct-LAN rejection, port-zero loopback startup, and the desktop navigation policy.
- `cd companion && npm run validate` passed: the backend rejected a direct LAN request without connecting/falling back.
- Node syntax checks passed for the edited JavaScript modules; `git diff --check` passed.
- Android build/unit tests were not run: no usable full JDK/Gradle toolchain is available in this workspace. No APK, instrumentation, two-device, network interruption, or independent security validation passed.

## Required release gates

1. Build with full Java 17 JDK and project Gradle; run all Android unit tests, lint/static analysis, and instrumentation tests. Fix every compile/test failure.
2. Adopt an independently reviewed E2EE protocol with authenticated peer identities, replay protection, key rotation, and strict interoperable envelopes. Keep Messaging/File Sharing blocked until integration is complete.
3. Implement Tor/onion-first feature routes where supported, followed only by a ciphertext-only relay that receives no keys/plaintext. Direct P2P must require explicit informed per-feature consent and disclose public-IP exposure. Do not silently downgrade.
4. Authenticate signaling and authorize each operation. Minimize IP/identity/timing logs and metadata; add quotas, bounds, short TTLs, and safe retry/reconnection behavior.
5. Validate TURN-only/direct policy, candidate/IP leakage, permissions, route negotiation, retries, network changes, NATs, and cleanup on two physical devices. Implement and validate the required active-route display.
6. Integrate owner-checked, short-lived remote file capabilities and encrypted resumable transfers; test authorization, expiry, revocation, replay, tampering, interruption, and local-copy limitations.
7. Independently review companion bridge cryptography/authorization; rerun dependency/license audits at release and keep the dependency tree vulnerability-free.
8. Keep the final status NO-GO until build, test, security, and device validation gates are met.
