# CalcVault Companion (Desktop/Web)

For the Ubuntu desktop package build and its local-only security boundary, see [UBUNTU_DESKTOP.md](UBUNTU_DESKTOP.md).

Local-first secure companion for CalcVault Android app.

## Features

- Secure pairing (QR or code), device-bound session
- USB/ADB loopback connection; direct LAN is disabled until an approved File Sharing route is integrated
- Backup to PC as encrypted versioned snapshot
- Restore from encrypted backup with integrity validation
- File transfer PC <-> phone with phone-authorized operations
- Phone-authoritative file index (PC only displays synced metadata)

## Run

```bash
cd companion
npm install
npm start
```

Then open the one-time URL printed in terminal.

## Validate

```bash
cd companion
npm run validate
```

## Structure

- `src/main.js` app bootstrap
- `src/api/http-server.js` local authenticated API + UI event stream
- `src/services/bridge-manager.js` encrypted session + operation engine
- `src/services/backup-store.js` backup persistence + integrity
- `src/transport/usb.js` ADB USB bridge transport
- `src/transport/network.js` fail-closed direct LAN adapter
- `src/security/crypto.js` pairing/session crypto primitives
- `web/` dashboard + backup manager + file manager + connection panel
- `docs/PROTOCOL.md` phone/PC communication protocol
- `docs/SECURITY.md` threat model and controls
- `docs/SETUP.md` setup and hardening
- `docs/ANDROID_BRIDGE_INTEGRATION.md` Android phone bridge notes
- `tests/mock-phone.js` mock Android bridge
- `tests/run-validation.js` end-to-end validation harness
