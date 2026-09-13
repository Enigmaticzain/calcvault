# Android Bridge Integration

This repository now includes a phone-side bridge skeleton for the desktop companion protocol.

## Added Android components

- `app/src/main/java/com/calcvault/companion/CompanionBridgeService.kt`
- `app/src/main/java/com/calcvault/companion/CompanionBridgeServer.kt`
- `app/src/main/java/com/calcvault/companion/CompanionSessionCrypto.kt`
- `app/src/main/java/com/calcvault/companion/CompanionBackupManager.kt`
- `app/src/main/java/com/calcvault/companion/CompanionFileVault.kt`
- `app/src/main/java/com/calcvault/companion/CompanionOperationAuthorizer.kt`
- `app/src/main/java/com/calcvault/companion/CompanionBridgeController.kt`

## What works in this bridge build

- WebSocket bridge endpoint at `ws://127.0.0.1:37111/bridge` (USB mode)
- Pairing handshake compatible with `companion/docs/PROTOCOL.md`
- Encrypted post-pairing message envelopes (`AES-256-GCM` + sequence replay protection)
- Backup create: streams encrypted `container.enc` from phone USB vault
- Backup restore: validates SHA-256 and atomically replaces `container.enc`
- File upload/download (phone-side encrypted storage in app-private `companion_vault`)
- File metadata listing for desktop UI

## Security behavior in this skeleton

- Operations require vault-open state on phone (`SessionManager.isVaultOpen`).
- In debug builds, operation approval can be auto-approved for development.
- In non-debug builds, operations are denied until explicit approval UI is wired (`approval_ui_required`).

## Start from app UI

Open `Settings` and use:
- `Start Desktop Companion Bridge`
- `Show Current Pairing Code`
- `Stop Desktop Companion Bridge`

## USB desktop workflow

1. Connect phone by USB and authorize ADB.
2. Start bridge in app settings.
3. On desktop: connect USB and start pairing.
4. Enter pairing code shown in app settings.
5. Run backup/restore/file operations.

## Known TODOs for production hardening

- Replace debug auto-approval with explicit per-operation approval UI.
- Display QR pairing payload in-app (currently code entry only).
- Add trust-on-first-use desktop fingerprint registry and revoke controls.
- Enforce strict operation-level audit logs and lock-screen invalidation.
