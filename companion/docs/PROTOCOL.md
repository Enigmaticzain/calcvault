# CalcVault Companion Bridge Protocol (`v1`)

This protocol is used only on local transports:
- `USB`: `adb forward tcp:<pc-port> tcp:37111` then WebSocket `/bridge`
- `LAN` (optional): direct `ws://` or `wss://` from PC to phone bridge endpoint

The phone remains the source of truth. The PC is a transport and storage companion.

## 1) Handshake and Pairing

### 1.1 PC -> Phone (`hello`, plaintext)

```json
{
  "type": "hello",
  "version": 1,
  "pairingId": "hex",
  "codeHash": "base64(sha256(pairingCode))",
  "pcPub": "base64(spki x25519 pubkey)",
  "pcNonce": "hex",
  "sessionTtlMs": 900000
}
```

### 1.2 Phone -> PC (`hello_ack`, plaintext)

```json
{
  "type": "hello_ack",
  "pairingId": "same",
  "codeHash": "same hash",
  "phonePub": "base64(spki x25519 pubkey)",
  "phoneNonce": "hex",
  "proof": "base64(hmac_sha256(pairingCode, transcript))",
  "device": {
    "id": "device-id",
    "name": "Device Name",
    "fingerprint": "stable-device-fingerprint"
  }
}
```

Transcript for `proof`:
`pairingId|pcPub|phonePub|pcNonce|phoneNonce|device.id`

### 1.3 Session Key Derivation

Both sides derive:
- `sharedSecret = X25519(private, peerPublic)`
- `sessionKey = HKDF-SHA256(sharedSecret, salt=SHA256(pairingCode + pairingId + pcNonce + phoneNonce), info="calcvault-session-v1", len=32)`

### 1.4 Encrypted Envelope (`enc`)

All post-pairing messages are encrypted:

```json
{
  "type": "enc",
  "sid": "pairingId",
  "seq": 1,
  "iv": "base64(12-byte nonce)",
  "ct": "base64(ciphertext)",
  "tag": "base64(aes-gcm tag)"
}
```

Cipher: `AES-256-GCM`

Nonce derivation (deterministic, anti-replay):
- bytes 0..3: `CVB1`
- byte 4: direction (`1` PC->Phone, `2` Phone->PC)
- bytes 8..11: `seq` (uint32 BE)

Replay protection: receiver rejects `seq <= lastSeenSeq`.

## 2) Operation Lifecycle

All privileged actions are operation-based and must be approved by phone:

1. PC sends `op_request`.
2. Phone prompts user and sends `op_authorized` (`approved: true|false`).
3. Optional streamed events/chunks.
4. Phone sends `op_result`.

### 2.1 Request

```json
{
  "type": "op_request",
  "requestId": "hex",
  "op": "backup.create|backup.restore|files.list|files.upload|files.download",
  "args": { "...": "..." },
  "requestedAt": 0
}
```

### 2.2 Authorization

```json
{
  "type": "op_authorized",
  "requestId": "hex",
  "approved": true,
  "token": "optional-approval-token"
}
```

### 2.3 Stream Events

```json
{
  "type": "op_event",
  "requestId": "hex",
  "eventType": "backup_manifest|backup_chunk|download_chunk|...",
  "data": { "chunk": "base64" }
}
```

### 2.4 PC -> Phone Upload/Restore Chunks

```json
{
  "type": "op_chunk",
  "requestId": "hex",
  "index": 0,
  "chunk": "base64"
}
```

```json
{
  "type": "op_chunk_end",
  "requestId": "hex",
  "metadata": { "finalChunkIndex": 12 }
}
```

### 2.5 Result

```json
{
  "type": "op_result",
  "requestId": "hex",
  "ok": true,
  "result": { "...": "..." }
}
```

## 3) Required Phone-side Behavior

- Never execute `op_request` without explicit user approval on phone.
- Keep file index authoritative on phone; PC only receives metadata snapshots.
- Backup payload produced by phone must already be encrypted before transmission.
- Restore must be all-or-nothing after integrity validation.
- Session expires automatically at TTL or lock-screen events.

## 4) Transport Security Notes

- USB forwarding keeps the phone bridge non-routable from external hosts.
- LAN mode should prefer `wss://` when available; protocol-level encryption still applies.
- All operations remain encrypted post-handshake regardless of transport.
