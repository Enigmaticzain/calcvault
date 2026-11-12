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

Supported phone bridge binding:
- USB mode: phone listens on `127.0.0.1:37111`, PC uses `adb forward`
- LAN mode is currently rejected because no per-feature File Sharing direct-route consent is integrated.

## 3) USB workflow (primary)

1. Enable USB debugging on phone.
2. Connect phone to PC and authorize ADB prompt.
3. In companion UI:
- Refresh USB devices
- Connect USB
- Start Pairing
4. Scan QR or enter pairing code on phone.
5. Approve operations from phone prompts.

## 4) LAN workflow

Direct LAN file transfer is unavailable and fails closed. The desktop UI disables the network connection controls, the backend rejects network transport requests, and the Android bridge binds only to loopback for USB/ADB. Use USB local-only or wait for a feature-approved route.

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
- Direct LAN transport has no environment override and remains disabled.
- `CV_DISABLE_USB_TRANSPORT=true|false`
- `CV_SESSION_TTL_MS` (default `900000`)
- `CV_OPERATION_TIMEOUT_MS` (default `60000`)
- `CV_MAX_CHUNK_BYTES` (default `262144`)

## 7) Production hardening checklist

- Pairing and operation approval prompt on phone for every privileged action.
- Store desktop trust decisions only with explicit user consent.
- Rotate session keys on reconnect and lock-screen transitions.
- Do not re-enable LAN without per-feature route integration, explicit IP disclosure/consent, and device validation.
- Add Android-side certificate pinning if using TLS on LAN.
