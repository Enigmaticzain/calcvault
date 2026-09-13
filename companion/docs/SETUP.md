# Setup Guide

## 1) Install and run companion

```bash
cd companion
npm install
npm start
```

The server prints a secure one-time local URL, for example:

`http://127.0.0.1:43831/?token=<one-time-token>`

Open that URL in browser to access the companion UI.

## 2) Android bridge requirements

Phone app must provide a WebSocket bridge endpoint (`/bridge`) and implement protocol in `docs/PROTOCOL.md`.

Recommended phone bridge binding:
- USB mode: phone listens on `127.0.0.1:37111`, PC uses `adb forward`
- LAN mode (optional): phone exposes `ws://<phone-ip>:37111/bridge` on trusted local network only

## 3) USB workflow (primary)

1. Enable USB debugging on phone.
2. Connect phone to PC and authorize ADB prompt.
3. In companion UI:
- Refresh USB devices
- Connect USB
- Start Pairing
4. Scan QR or enter pairing code on phone.
5. Approve operations from phone prompts.

## 4) LAN workflow (optional)

1. Ensure phone and PC are on same trusted Wi-Fi.
2. Phone app shows bridge URL.
3. In companion UI:
- Enter URL
- Connect Network
- Start Pairing
- Approve on phone

## 5) Validation run

```bash
cd companion
npm run validate
```

This starts a mock phone bridge and validates:
- secure pairing flow
- backup create
- restore integrity
- upload and download
- encrypted transfer leakage checks

## 6) Environment variables

- `CV_PORT` companion local port (default `43831`)
- `CV_BIND_HOST` local bind host (default `127.0.0.1`)
- `CV_PHONE_BRIDGE_PORT` phone loopback port for USB forward (default `37111`)
- `CV_ENABLE_NETWORK_TRANSPORT=true|false`
- `CV_DISABLE_USB_TRANSPORT=true|false`
- `CV_SESSION_TTL_MS` (default `900000`)
- `CV_OPERATION_TIMEOUT_MS` (default `60000`)
- `CV_MAX_CHUNK_BYTES` (default `262144`)

## 7) Production hardening checklist

- Pairing and operation approval prompt on phone for every privileged action.
- Store desktop trust decisions only with explicit user consent.
- Rotate session keys on reconnect and lock-screen transitions.
- Prefer `wss://` for LAN mode.
- Add Android-side certificate pinning if using TLS on LAN.
