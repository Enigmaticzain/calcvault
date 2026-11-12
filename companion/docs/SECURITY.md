# Security Model

## Trust Boundaries

- Phone is primary authority and source of truth.
- PC companion is an untrusted helper that can only request operations.
- Every sensitive operation requires phone-side authorization.

## Controls Implemented

1. Local API hardening
- Server binds to `127.0.0.1` by default.
- UI requires one-time launch URL token.
- API calls require per-session token (`x-calcvault-ui-token`) and short-lived cookie.
- Sessions are in-memory only and expire automatically.

2. Pairing and session protection
- Pairing code never sent in plaintext.
- Session key uses ephemeral X25519 + HKDF with pairing-code-bound salt.
- Post-pairing traffic encrypted with AES-256-GCM envelopes.
- Monotonic encrypted sequence numbers block replay.

3. Authorization model
- `op_request` -> `op_authorized` enforced before transfer.
- Companion rejects chunk upload/restore if phone did not approve.
- Session expiry or disconnect aborts pending operations.

4. Data-at-rest guarantees on PC
- Backups are stored as encrypted blobs (`.cvb`) exactly as received from phone.
- Backup integrity is verified with SHA-256 before commit.
- File metadata cache is in-memory only.
- Upload flow streams from browser to phone; no plaintext temp files created by server.

5. Network exposure
- No cloud dependencies.
- No open unauthenticated API route for operational actions.
- Direct LAN bridge transport is disabled and rejected until an Android File Sharing privacy route with explicit IP disclosure is integrated.
- The supported companion transport is USB/ADB loopback; it is a local companion path, not the partner messaging/file-sharing route.

## Residual Risks

- LAN mode is not available in the current implementation. Do not re-enable it without per-feature consent, explicit IP disclosure, and validation of the encrypted file route.
- If local browser/profile malware steals session tokens during active session, attacker could issue local API calls.
- USB security assumes trusted `adb` environment and authorized USB debugging state.

## Operational Recommendations

- Keep LAN transport disabled; it is currently rejected in code and has no `CV_ENABLE_NETWORK_TRANSPORT` override.
- Keep sessions short (`CV_SESSION_TTL_MS`) and disconnect when idle.
- Require biometric/passcode confirmation on phone before each privileged operation.
- Pin allowed desktop fingerprints on phone for stronger device binding.
