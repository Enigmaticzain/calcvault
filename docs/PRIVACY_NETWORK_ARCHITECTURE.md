# CalcVault Privacy-First Network Architecture

**Status:** repository audit and migration design; the full architecture is not implemented yet. This document deliberately distinguishes existing behavior from planned behavior.

## Executive summary

CalcVault has several overlapping transports rather than one secure transport layer: HTTP polling signaling, WebRTC call/screen-share media, local Bluetooth/Wi-Fi Direct classes, and legacy socket/sync adapters. Tor support is absent. The chat `WebRTCManager` remains a stub. Messaging calls use `sendEncryptedPayload` and media/file calls use `sendMediaPayload`; both fail closed with feature-specific route and encryption explanations and do not provide network transfer. This prevents the prior plaintext outbound path from running, but does not implement E2EE or resolve existing local-storage/privacy concerns.

Therefore, **the current network path must not be described as end-to-end encrypted or production secure**. A privacy-first rollout must first establish and test a real, interoperable, authenticated E2EE envelope, then route only ciphertext through transports. Current code adds fail-closed content gates and feature-aware signaling/ICE checks, but these do not authenticate the signaling service or peer and do not make control JSON E2EE.

## Repository survey

The survey covered Android application modules and manifest/dependencies, network/crypto/storage/call/watch feature entry points, the embedded Android signaling server, and the Node signaling server and deployment notes.

### Existing components

- `NetworkMessageEngine.kt`: registers devices, heartbeats, and polls HTTPS signals and relay queues. The relay handler rejects content before base64 decoding or persistence; outbound content APIs reject Messaging and File Sharing payloads. Control signaling uses the configured `SignalingEndpointResolver` HTTPS service, defaulting to `https://calcvault-signal.onrender.com`.
- `SignalingServer.kt`: small HTTP/WS server started by `CompanionBridgeService`; it is a LAN/companion feature, not a public internet endpoint. Queues are in memory.
- `signaling-server/server.js`: Node HTTP/WebSocket signaling and relay implementation, with in-memory queues and basic IP rate limiting. It has no adequate paired-device authorization in the inspected routes and is not safe to expose publicly as-is. It has no built-in TLS; public deployment needs a correctly secured TLS proxy or equivalent.
- `WebRTCManager.kt`: now contains a WebRTC audio/video implementation attempt (SDP, ICE, candidate queueing, TURN/direct ICE policy input, ICE-restart recovery and bitrate sampling). It is uncompiled and untested; API compatibility, interoperability, authenticated identity and real network behavior are not established.
- `ScreenShareEngine.kt`: uses configured ICE credentials and the feature's ICE policy, with queued SDP/ICE signaling. The WebRTC and signaling path is uncompiled and untested; route policy does not authenticate peers or protect the HTTPS signaling payload from the service.
- `SocketCallEngine.kt`: the active class is now a fail-closed compatibility stub that opens no socket; Listen Together voice is disabled. `SyncEventDataChannel` and `WatchSessionNetworkCoordinator` still contain legacy/dead adapters; the coordinator now refuses to report a connection unless the disabled socket is connected.
- `BluetoothTransport.kt` and `WiFiDirectTransport.kt`: local-only transport implementations. Bluetooth uses insecure RFCOMM; both rely on callers to provide encrypted bytes and are not integrated into the common message engine.
- `E2EKeyManager.kt`, `DoubleRatchet.kt`, and `X3DH.kt`: custom cryptographic code exists, but the message/call paths inspected do not use a verified end-to-end session. The ephemeral ECDH manager now derives a symmetric HKDF salt from the canonically ordered ephemeral public-key transcript; tests cover two independent peers, tampering, wrong keys and malformed ciphertext. Those tests have not run, and the ephemeral exchange still does not authenticate peer identities or integrate with the network content envelope. Replay protection belongs in that future authenticated envelope and is not provided by this primitive. The custom ratchet code has not been independently reviewed and must not be advertised as Signal Protocol.
- `MediaChunkManager.kt` and encrypted storage containers: provide local media/container storage features; local-at-rest encryption does not establish network E2EE.
- `WatchTogetherActivity.kt`: local playback remains separate from sync. A signaling adapter was added for invitations, control events, timing samples, join/resync and state snapshots. It uses the configured HTTP signaling service after a one-time disclosure/consent prompt, but the service and peer are not authenticated by this app; end-to-end delivery and synchronization remain untested.
- `AndroidManifest.xml`: INTERNET/network, location, microphone, camera, Bluetooth, and foreground-service permissions are present. No Tor client or push provider is configured.

### High-severity findings to address first

1. Prior chat call sites passed UTF-8 plaintext, voice bytes, and local URI strings toward the relay. Outbound Messaging and File Sharing content now use feature-specific fail-closed entry points, and inbound relay content is rejected before base64 decoding or local persistence; no secure messaging or file-transfer implementation is available.
2. `sendEncryptedPayload` (Messaging) and `sendMediaPayload` (File Sharing) both return false and do not transmit; their diagnostics include the selected feature mode, unavailable route, and missing verified E2EE. These are fail-closed blocks, not encryption.
3. The raw socket crypto fallback is no longer active: `SocketCallEngine` is a no-socket fail-closed stub. Legacy `SyncEventDataChannel` extensions remain no-ops and must not be treated as a working sync transport.
4. The active call path attempts WebRTC instead of raw TCP and includes bounded same-route ICE restart and SDP-before-ICE queuing. The new negotiation, signaling, media lifecycle and recovery have not compiled or been verified across carrier NATs.
5. Screen sharing now uses the configured ICE policy and requires a saved lower-privacy choice, but configured endpoints and signal identities are not authenticated and the path is untested.
6. The inspected relay routes trust caller-supplied device IDs and do not authenticate paired devices strongly enough for public exposure. Presence and registration also reveal network metadata to the relay.
7. No reliable app-wide E2EE, group key management, anonymous routing, resumable encrypted file transfer, or network-mode display is currently established.

## Feature-to-transport compatibility matrix

| Feature | Existing path / status | Privacy and reliability gap | Target transport and policy |
|---|---|---|---|
| One-to-one text | `ChatActivity` calls `NetworkMessageEngine.sendEncryptedPayload`; current implementation rejects payloads and keeps the message local | No content route or verified/authenticated E2EE envelope; prior relay payload was plaintext; signaling metadata remains visible to service | Keep blocked until reviewed E2EE envelope and an authenticated route adapter are available; then apply explicit feature policy |
| Group text | No complete group-chat/key-management flow found | Pairwise or shared custom keys do not establish secure group membership or forward secrecy | Audited group protocol (for example, MLS where compatible); same ciphertext-only transport policy; membership changes rotate keys |
| Voice messages | `ChatActivity` calls `sendMediaPayload`; the content API rejects it, so voice remains local | No network E2EE/file-transfer route; selected File Sharing mode is reported unavailable | Keep blocked until reviewed E2EE streaming, integrity, resume, and explicit route support exist |
| Images/video/documents | `ChatActivity` passes a URI to the File Sharing gate, which rejects transmission; local display metadata remains on device | No secure network file-transfer implementation; local URI or storage encryption is not network E2EE | Keep blocked until encrypted stream/chunk transfer, bounded metadata, integrity checks, resumption, and authorization exist |
| Large files | Outbound chunking is not reachable from enabled content APIs; current transfers are blocked | No verified end-to-end transfer key, resume protocol, quota/authorization model, or safe public relay | Keep blocked until chunk-encrypted transfer with per-file key, MAC/hash verification, resume state, and short-lived authorization exists |
| Audio calls | `CallEngine` checks the selected ICE preference, but the active signaling gate rejects offers/accepts before they reach the configured HTTPS endpoint | Calls are unavailable regardless of ICE credentials; no offer or direct fallback is sent. WebRTC behavior is uncompiled and untested | Implement authenticated ciphertext-only signaling, then retain TURN-only or explicitly consented direct ICE; validate peer-IP exposure and recovery |
| Video calls | `CallEngine` checks the selected video-call ICE preference, but the active signaling gate rejects all call control signals | Calls are unavailable regardless of ICE credentials; no offer or direct fallback is sent. Native media is untested | Implement authenticated ciphertext-only signaling, then validate camera/media lifecycle, TURN-only and explicit direct consent on devices |
| Screen sharing | `ScreenShareEngine` receives an unavailable-route signal gate and stops setup | Screen sharing is unavailable; ICE config and saved consent do not authorize an insecure control route | Implement authenticated ciphertext-only signaling and validate permissions, cleanup, candidate/IP disclosure, and device behavior |
| Location sharing | Location permissions exist; no complete dedicated location-sharing transport found | Sensitive coordinates and retention policy are not established | Explicit per-session consent; minimize precision/retention; E2EE; onion or relay, never public presence payloads |
| Watch Together | Local player and synchronization state; active network signaling gate rejects invitations and sync events | Partner sync/invitations unavailable; no authenticated ciphertext-only control route is implemented | Small authenticated, encrypted playback events over a reviewed low-latency route; recover from signed state; do not redistribute media by default |
| Presence/notifications | HTTP register/heartbeat/lookup/poll; no push provider found | Device IDs and timing leak; Android may suspend background work; push would add a provider | Opt-in coarse/short-TTL presence; redact IPs; foreground service while user enables online mode; explain push provider dependency if added |
| Bluetooth / nearby Wi-Fi | Separate Bluetooth and Wi-Fi Direct classes | Not internet transports; Bluetooth “insecure” sockets; caller-supplied encryption assumption | Keep as optional nearby transport; authenticate peer and require E2EE envelope; permission/discovery disclosure |
| Future features | No central route abstraction | Feature code can accidentally bypass privacy policy | Every feature requests a capability/policy-based route from one manager; raw sockets/HTTP are not feature APIs |

## Target architecture

### 1. Security boundary

All feature payloads must pass through one crypto boundary before transport selection:

`Feature payload → authenticated E2EE session → encrypted envelope → route manager → transport`

Transport adapters receive opaque ciphertext only. No route adapter should accept arbitrary plaintext bytes. Key exchange and identity verification must use an established, reviewed protocol/library; custom cryptography is not an acceptable substitute. Pairing must authenticate peer identity (for example, QR fingerprint/safety-number verification) and resist signaling-server substitution.

Group messaging needs a group-key protocol rather than reusing a one-to-one session key. File encryption needs a per-file key derived from an authenticated session and separate encrypted metadata. Server-side TLS is still required, but TLS is not a replacement for E2EE.

### 2. Route adapters and their disclosure

- **Tor/onion:** optional for supported asynchronous/sensitive operations. Does not require the app to operate a public inbound phone port, but depends on Tor relays and may be blocked or slow. Do not force Tor onto live audio/video/screen media.
- **Authenticated encrypted relay:** reliable fallback for messaging/files when both endpoints can make outbound HTTPS/WSS connections. It sees endpoint IPs, timing, IDs/queue metadata, and ciphertext, but not plaintext or keys. Prefer a user-controlled laptop endpoint when configured. Queues should be volatile, bounded, short-lived, and authenticated.
- **TURN relay:** WebRTC fallback for live audio/video/screen sharing. TURN can see client IPs, timing, and encrypted packets; peer IPs are hidden from each other. Use an explicitly configured/trusted TURN service and WebRTC's DTLS encryption plus authenticated peer identity.
- **Direct WebRTC:** optional, never automatic when the user requires IP privacy. ICE can reveal a public IP to the peer. Obtain explicit consent, show a warning, and keep the chosen mode visible for the whole session.
- **Nearby Wi-Fi Direct/Bluetooth:** optional local transports, not internet fallback. Require peer authentication and E2EE despite local proximity.

A privacy fallback is not a silent downgrade. A route requiring lower privacy returns a consent-required result; the UI must explain what metadata becomes visible before attempting it. A remembered setting may count as consent only after a clear, reversible opt-in.

### 3. Feature-aware route profiles

- Asynchronous text, voice notes, small media, documents and location: onion when available and allowed; otherwise offer authenticated ciphertext relay. Large transfers use resumable encrypted chunks.
- Audio/video calls and screen sharing: do not route media over Tor by default. Prefer TURN when the user wants peer-IP privacy. Direct WebRTC is offered only after explicit disclosure and consent. If neither route is allowed/reachable, fail clearly rather than downgrade.
- Watch Together: keep media local/authorized; send only small authenticated playback state events. Use a route suitable for real-time control, with drift recovery after reconnection.
- Presence/notifications: opt-in, coarse and short-lived. Background delivery requires a foreground service or an explicit push provider; do not imply offline delivery without that infrastructure.

## Migration plan

### Phase 0 — Audit and policy foundation (current milestone)

- Record existing transport and crypto limitations (this document).
- Added `PrivacyRoutePolicy`, a pure, testable route-policy model that fails closed when E2EE is not ready, excludes Tor for real-time media, requires explicit IP-exposure consent for direct P2P, and marks relay fallback as consent-required by default.
- Added unit tests for route ordering, direct-IP warnings, Tor exclusion from calls, relay consent, encryption gating, candidate ordering, file-grant expiry/revocation and watch sync math. They remain unexecuted: a Java 17 runtime exists under `/tmp`, but no full JDK compiler or Gradle 8.4 distribution is available; the wrapper download previously failed with an SSL EOF.
- `PrivacyTransportGate` and feature-scoped control checks are wired into call, screen-share, Watch Together and Listen Together paths. Since no authenticated ciphertext-only signaling adapter exists, the active gate rejects all of those control signals; HTTPS endpoints, one-time consent and ICE settings do not count as an available privacy route. This is an intentional fail-closed outage. ICE choices are configuration only and do not establish a usable call route, authenticated identity, or E2EE. The pure `PrivacyRoutePolicy` availability planner is still not used by actual adapters.

### Phase 1 — E2EE and authenticated identity (blocker for public traffic)

- Select and integrate a reviewed Android-compatible E2EE implementation; assess license, Android/API compatibility, key backup/recovery, and group support.
- Pair and verify identity keys; establish per-conversation sessions; test independent devices and tamper/replay handling.
- Replace the current E2EKeyManager/hand-rolled ratchet path only after migration and test coverage. Remove every plaintext fallback. Keep network sends disabled for content types whose encryption is not ready.

### Phase 2 — Secure signaling and encrypted relay

- Harden `signaling-server`: paired-device credentials, authorization on register/signal/poll/relay, replay protection, payload/queue quotas, short TTL, no IP/body logging, memory-only by default, TLS/WSS deployment, abuse controls.
- Add a user-configurable self-hosted laptop endpoint. Do not reuse the local companion `:43831` endpoint as a public chat server.
- Introduce an authenticated persistent WSS relay and a visible connection-state component. Avoid silent switching to the default external endpoint.

### Phase 3 — Tor/onion for supported operations

- Decide on a supported Android Tor implementation and lifecycle model; establish how onion addresses and peer identities are exchanged and rotated.
- Add onion capability checks, bootstrap/timeout handling, reconnect and explicit consent for non-anonymous fallback. Test battery, captive portals, Tor-blocked networks and app background limits.

### Phase 4 — WebRTC/TURN calls and direct-consent mode

- Complete the data-channel/WebRTC manager, use authenticated signaling, queue early ICE candidates, handle timeouts/retries and network-change recovery.
- Configure privacy-aware TURN. Make STUN/TURN endpoints explicit rather than hard-coding a public provider.
- Remove raw `ServerSocket` call transport from internet calls. Direct ICE candidates require an explicit warning/consent and must never be chosen after Tor/TURN failure silently.

### Phase 5 — Files, location, Watch Together and presence

- Implement encrypted stream/chunk transfer with integrity and resumption.
- Gate sensitive location sharing; avoid putting coordinates in generic presence events.
- Send Watch Together control state independently of media; authenticate event/session IDs and recover from last valid state.
- Minimize presence and notification metadata; document any external push service.

### Phase 6 — Verification and release gate

- Add unit, integration and two-device tests for crypto interoperability, negotiation, route ordering, consent, IP exposure, transfer resume/tampering, call recovery, and watch resync.
- Test Wi-Fi-to-mobile, mobile-to-mobile, IPv4/IPv6, CGNAT, Tor unavailable/blocked, relay unavailable, app background/foreground and network changes.
- Run dependency/license review and an independent security review. Do not call the system production-ready before this gate.

## Initial automated test plan

- Route policy never returns a network route when E2EE is unavailable.
- Tor is preferred for supported operations when enabled, and is never selected for live audio/video/screen sharing.
- A relay fallback from an anonymous route is marked consent-required unless the user explicitly enabled it.
- Direct WebRTC is absent by default; if enabled, its candidate contains an explicit public-IP disclosure warning.
- A Tor session never automatically transitions to direct P2P after a failure.
- Calls prefer configured TURN for peer-IP privacy; direct calls require explicit consent.
- Crypto tests use two independent parties, not only a self-round-trip; ciphertext tampering, malformed envelopes and wrong-peer keys fail closed. Replay protection must be implemented and tested at the future authenticated message-envelope layer.
- File tests cover chunk order, resume, truncation, wrong keys, hash mismatch, authorization expiry and cancellation cleanup.
- Watch tests cover stale events, replay, clock skew, state recovery and drift correction.

## Current milestone limitations

The Watch Together timing foundation includes pure clock-offset/RTT, arrival-position projection and stale/future-event helpers with unit tests. `WatchTogetherActivity` may invoke the signaling API for small control events, snapshots/resync, timing samples, and invitations, but the active route gate rejects these before network POST because no authenticated ciphertext-only signaling adapter exists. A disclosure prompt or one-time room consent does not change transport availability. Video filenames, paths and bytes are not included in invitations. Sender/room checks are correlation filters, not authenticated identity; delivery, timing, reconnection and two-device synchronization have not been verified. Watch Together's in-room voice/video controls are disabled rather than using the legacy raw socket transport.

This change set still does not implement Tor, app-level E2EE, signaling authorization, authenticated peer identity, owner-enforced file grants, resumable encrypted file transfer, or a completed release security review. TURN settings and ICE policy code exist but call/screen-share behavior is uncompiled and untested. No production or security claim is justified.
