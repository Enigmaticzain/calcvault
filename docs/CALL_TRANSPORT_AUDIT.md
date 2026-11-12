# Call, signaling, and privacy-route audit

**Date:** 2026-11-12  
**Decision:** **NO-GO for network calls** until an authenticated, ciphertext-only signaling adapter and device validation are available.

## Route policy and current behavior

- `NetworkMessageEngine` has no approved call-control/signaling adapter. Its feature route check remains fail-closed; it does not register, poll, send call JSON, SDP, ICE, or attempt a different route.
- Messaging and file-sharing payload paths likewise require a concrete selected route and verified E2EE. Those capabilities are unavailable in this build; the UI/policy reports the selected route unavailable rather than sending plaintext, local ciphertext, or falling back.
- TURN-only calls use only configured TURN URIs with both credentials and `IceTransportPolicy.RELAY`; STUN and host/srflx candidates are excluded. TURN is not signaling encryption or peer authentication.
- The direct-call choice previously set `IceTransportPolicy.ALL`, which would gather direct candidates immediately instead of honoring the displayed TURN-first/direct-last order. It now fails closed before ICE gathering. This direct fallback remains unavailable until a staged TURN-first attempt and separately authorized IP-disclosing restart are implemented and tested.
- A one-time Watch/Listen session consent no longer overrides a saved maximum-privacy setting. Maximum privacy can only use a concrete verified onion route for onion-capable features; no such adapter exists in this build.

## Call lifecycle, UI, permissions, and service

Reviewed `CallEngine`, `CallActivity`, `CallForegroundService`, `WebRTCManager`, ICE policy, candidate buffering, recovery, and bitrate adaptation.

Changes made:

- Canonicalize both paired-user IDs and reject unknown, same-user, blocked, or misaddressed call identities. Call signal matching checks call ID, paired sender, and intended recipient; unknown call types are rejected rather than interpreted as voice calls.
- Serialize call setup/answer transitions, permit only one answer attempt, guard teardown against concurrent signal/timeout callbacks, and check the call is still live after foreground-service startup before constructing media resources.
- Keep privacy-session registration from ringing through call end, preventing a route preference from being changed mid-negotiation. Call duration uses monotonic elapsed time.
- Start and confirm the microphone/camera foreground service after remote acceptance but before creating local WebRTC media. A service/permission startup failure ends setup before capture. On Activity teardown, release WebRTC tracks before stopping the foreground service so capture cannot outlive its protection. WebRTC native EGL/factory allocation is lazy and teardown is idempotent; late signals after disposal are ignored.
- Preserve call notification controls: tapping resumes the existing call activity, the notification End action targets the live call UI, and Back backgrounds an active call rather than destroying its media. Fixed a notification target that previously opened the calculator and could lose the call screen. Restored a video-call camera toggle with explicit track readiness and stable enabled/disabled state; it is hidden for voice calls. Recording remains visible but explains that capture is unavailable until participant consent and encrypted storage are implemented; it never records silently.
- Validate STUN/TURN URI shape and port, reject malformed settings instead of silently dropping them, require TURN credentials, and clear stale TURN credentials when its URI list is removed.
- Keep bounded ICE candidate buffers and SDP-before-ICE local queue ordering. Remote candidates are held until `setRemoteDescription` succeeds; receiving an answer no longer clears candidates that arrived just before it. ICE-restart signaling still depends on the future transport preserving SDP-before-candidate message order; there is no active approved signaling adapter to verify that behavior on the wire. Recovery retries the same ICE policy with finite 1/2/4/8 second backoff and terminates when exhausted. Video bitrate adaptation remains bounded by available bandwidth, RTT, and packet-loss estimates.

## Remaining limitations / validation gates

- No Tor/onion or authenticated ciphertext-only call signaling adapter exists. HTTPS signaling, TURN, DTLS-SRTP, a one-time consent, or a saved route choice is not treated as peer authentication or app-level E2EE.
- Direct P2P fallback remains intentionally unavailable; the current ICE stack cannot safely guarantee the required TURN-first-then-disclosed-direct transition.
- The WebRTC implementation has not been type-checked, built, or exercised on Android. Android runtime permission/FGS behavior, native WebRTC API compatibility, camera/audio cleanup, notification actions, TURN-only candidate leakage, network changes, recovery, and bitrate behavior still require Gradle and two-device verification.
- The foreground service is call-lifetime support, not a service-owned WebRTC engine. Process recreation does not restore a call; the call UI reports/ends a non-restorable session rather than creating a second outgoing call.
- No APK, instrumentation result, device crash test, or security clearance is claimed. See `APP_AUDIT_STATUS.md` and `SECURITY_REVIEW.md`.
