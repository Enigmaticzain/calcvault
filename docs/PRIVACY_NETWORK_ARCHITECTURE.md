# CalcVault Privacy-First Network Architecture

**Status:** repository audit and migration design; the full architecture is not implemented yet. This document deliberately distinguishes existing behavior from planned behavior.

## Executive summary

CalcVault has several overlapping transports rather than one secure transport layer: an HTTP polling relay for chat, raw TCP sockets for calls/watch synchronization, a separate WebRTC screen-share engine, and local Bluetooth/Wi-Fi Direct classes. Tor support is absent. The chat `WebRTCManager` is a stub. Most importantly, the current chat path passes text/media bytes to `NetworkMessageEngine.sendEncryptedPayload`, but that method only Base64-encodes and queues the supplied bytes; the chat call sites pass plaintext text, URI strings, and voice bytes. The method name is not evidence of end-to-end encryption.

Therefore, **the current network path must not be described as end-to-end encrypted or production secure**. A privacy-first rollout must first establish and test a real, interoperable, authenticated E2EE envelope, then route only ciphertext through transports. A transport policy is added as an initial, fail-closed foundation; it is not yet wired to the existing feature paths.

## Repository survey

The survey covered Android application modules and manifest/dependencies, network/crypto/storage/call/watch feature entry points, the embedded Android signaling server, and the Node signaling server and deployment notes.

### Existing components

- `NetworkMessageEngine.kt`: registers devices, heartbeats, polls signals and relays, chunks payloads, and writes messages to local storage. It uses HTTP and defaults to `https://calcvault-signal.onrender.com` through `SignalingEndpointResolver`.
- `SignalingServer.kt`: small HTTP/WS server started by `CompanionBridgeService`; it is a LAN/companion feature, not a public internet endpoint. Queues are in memory.
- `signaling-server/server.js`: Node HTTP/WebSocket signaling and relay implementation, with in-memory queues and basic IP rate limiting. It has no adequate paired-device authorization in the inspected routes and is not safe to expose publicly as-is. It has no built-in TLS; public deployment needs a correctly secured TLS proxy or equivalent.
- `WebRTCManager.kt`: now contains a WebRTC audio/video implementation attempt (SDP, ICE, candidate queueing, TURN/direct ICE policy input, ICE-restart recovery and bitrate sampling). It is uncompiled and untested; API compatibility, interoperability, authenticated identity and real network behavior are not established.
- `ScreenShareEngine.kt`: uses WebRTC and hard-codes Google's public STUN service. This exposes network candidates to a third party and can expose peer addresses; there is no shared route/privacy policy.
- `SocketCallEngine.kt`: legacy code still opens raw TCP and has unsafe encryption fallbacks. The active `CallEngine` and `SimpleCallActivity` paths were changed not to use it, and Watch Together no longer starts it, but the class remains in the repository and other call entry points require audit.
- `BluetoothTransport.kt` and `WiFiDirectTransport.kt`: local-only transport implementations. Bluetooth uses insecure RFCOMM; both rely on callers to provide encrypted bytes and are not integrated into the common message engine.
- `E2EKeyManager.kt`, `DoubleRatchet.kt`, and `X3DH.kt`: custom cryptographic code exists, but the message/call paths inspected do not use a verified end-to-end session. The current E2E tests exercise a self-round-trip, not two independent devices. The ephemeral ECDH key derivation uses a freshly random salt on each side without exchanging that salt, so independent peers cannot derive the same key by that function. The custom ratchet code has not been independently reviewed and must not be advertised as Signal Protocol.
- `MediaChunkManager.kt` and encrypted storage containers: provide local media/container storage features; local-at-rest encryption does not establish network E2EE.
- `WatchTogetherActivity.kt`: local playback remains separate from sync. A signaling adapter was added for invitations, control events, timing samples, join/resync and state snapshots. It uses the configured HTTP signaling service after a one-time disclosure/consent prompt, but the service and peer are not authenticated by this app; end-to-end delivery and synchronization remain untested.
- `AndroidManifest.xml`: INTERNET/network, location, microphone, camera, Bluetooth, and foreground-service permissions are present. No Tor client or push provider is configured.

### High-severity findings to address first

1. Chat text is passed as UTF-8 plaintext to the relay path. Voice-message bytes and other payloads are also passed without an E2EE transform in the inspected call sites.
2. Image/file sending currently sends a URI string as the network payload, not an encrypted file stream; this is not a secure file-transfer implementation.
3. `sendEncryptedPayload` does not encrypt. `NetworkMessageEngine.decodeOpaquePayload` tries local storage decryption and falls back to a copy of the original bytes, which can conceal whether data was actually protected.
4. `SocketCallEngine`'s crypto failure fallback is plaintext. It must be removed before any public call transport is allowed.
5. The active call path now attempts WebRTC instead of raw TCP and includes bounded same-route ICE restart; the legacy raw socket class remains. The new negotiation, relay behavior and media lifecycle have not compiled or been verified across carrier NATs.
6. Screen sharing uses a third-party STUN service without a user-facing disclosure or policy gate.
7. The inspected relay routes trust caller-supplied device IDs and do not authenticate paired devices strongly enough for public exposure. Presence and registration also reveal network metadata to the relay.
8. No reliable app-wide E2EE, group key management, anonymous routing, resumable encrypted file transfer, or network-mode display is currently established.

## Feature-to-transport compatibility matrix

| Feature | Existing path / status | Privacy and reliability gap | Target transport and policy |
|---|---|---|---|
| One-to-one text | `ChatActivity` → `NetworkMessageEngine` → HTTP relay; current payload is plaintext at the call site | Relay can read text; default external service; polling and user IDs reveal metadata | Audited E2EE envelope; Tor/onion when enabled and suitable; authenticated encrypted relay fallback; direct data only after explicit IP warning/consent |
| Group text | No complete group-chat/key-management flow found | Pairwise or shared custom keys do not establish secure group membership or forward secrecy | Audited group protocol (for example, MLS where compatible); same ciphertext-only transport policy; membership changes rotate keys |
| Voice messages | Voice bytes passed to message engine | No demonstrated network E2EE; current file send is not resumable | Encrypt on device; chunked authenticated transfer over onion/relay; direct only with consent |
| Images/video/documents | Chat sends a content URI string; local media container exists | URI is not file content; filenames/MIME/thumbnail can leak; local encryption is not network encryption | Encrypt stream/chunks before transport; opaque IDs, bounded metadata, integrity checks, resumable transfer, expiring authorization |
| Large files | Message relay chunks payloads; local media manager has chunks | No verified end-to-end transfer key, resume protocol, quota/authorization model, or safe public relay | Chunk-encrypted transfer with per-file key, MAC/hash verification, resume bitmap, short-lived capability, no plaintext temp files |
| Audio calls | `CallEngine`/`SocketCallEngine`, raw TCP and local-address signaling | No NAT traversal; crypto fallback can send plaintext; no route/IP consent | WebRTC with DTLS plus authenticated peer identity; TURN when peer-IP privacy is selected; direct ICE only with explicit consent; no Tor for live media by default |
| Video calls | No integrated general video-call transport found; WebRTC elements exist in screen sharing | Not a complete call path; candidate/IP exposure and policy are not controlled | WebRTC audio/video with TURN-first for peer-IP privacy; optional direct only after warning; recovery and adaptive bitrate |
| Screen sharing | `ScreenShareEngine` WebRTC, Google STUN, signaling via message engine | Third-party STUN and peer candidate exposure; no app-wide consent/status | WebRTC track through route manager; TURN-first for IP privacy; share permission/session-scoped stop; no silent direct downgrade |
| Location sharing | Location permissions exist; no complete dedicated location-sharing transport found | Sensitive coordinates and retention policy are not established | Explicit per-session consent; minimize precision/retention; E2EE; onion or relay, never public presence payloads |
| Watch Together | `PlaybackSynchronizationEngine` plus legacy socket coordinator; local player | Control messages use legacy transport; video source authorization/sync identity need validation | Small authenticated, encrypted playback events over low-latency relay or WebRTC data channel; recover from latest signed state; do not redistribute media by default |
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
- Added unit tests for route ordering, direct-IP warnings, Tor exclusion from calls, relay consent, and encryption gating. They could not be executed in this environment because Java/JAVA_HOME is unavailable.
- The policy is not yet wired to the feature adapters; do not advertise it as an active transport until those adapters use it.

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
- Crypto tests use two independent parties, not a self-round-trip; ciphertext tampering, replay and wrong-peer keys fail closed.
- File tests cover chunk order, resume, truncation, wrong keys, hash mismatch, authorization expiry and cancellation cleanup.
- Watch tests cover stale events, replay, clock skew, state recovery and drift correction.

## Current milestone limitations

The Watch Together timing foundation includes pure clock-offset/RTT, arrival-position projection and stale/future-event helpers with unit tests. `WatchTogetherActivity` now attempts to transmit small control events, handle snapshots/resync, exchange timing samples, and send/receive invitations using the configured HTTP signaling service. A disclosure gate precedes an explicit one-time room override, and video filenames, paths and bytes are not included in invitations. The sender/room checks are correlation filters, not authenticated identity; server delivery, timing behavior, reconnection and two-device synchronization have not been verified. Watch Together's in-room voice/video controls are disabled rather than using the legacy raw socket transport.

This change set still does not implement Tor, app-level E2EE, signaling authorization, authenticated peer identity, resumable encrypted file transfer, or a completed security review. TURN settings and ICE policy code exist but call/screen-share behavior is uncompiled and untested. The policy foundation, transport integrations and security tests must be verified before making any production or security claim.
