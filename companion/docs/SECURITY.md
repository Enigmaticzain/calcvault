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
- Optional LAN transport still uses protocol-level encryption.

## Residual Risks

- LAN mode over `ws://` relies on protocol-layer encryption; use `wss://` when possible.
- If local browser/profile malware steals session tokens during active session, attacker could issue local API calls.
- USB security assumes trusted `adb` environment and authorized USB debugging state.

## Operational Recommendations

- Disable LAN transport in high-risk environments (`CV_ENABLE_NETWORK_TRANSPORT=false`).
- Keep sessions short (`CV_SESSION_TTL_MS`) and disconnect when idle.
- Require biometric/passcode confirmation on phone before each privileged operation.
- Pin allowed desktop fingerprints on phone for stronger device binding.
