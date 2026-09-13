# CalcVault Companion Protocol

## Overview

Secure bidirectional communication between PC companion and Android phone.

- **Transport:** WebSocket (ws:// or wss://)
- **Encryption:** AES-256-GCM (after pairing)
- **Authentication:** ECDH (X25519) + HMAC-SHA256
- **Framing:** JSON messages

---

## Connection Lifecycle

```
PC                          Phone
|                            |
|--- USB/Network Connect ----|
|                            |
|--- hello (plain) --------->|
|                            |
|<-- hello_ack (plain) ------|
|                            |
|--- session_ready (enc) --->|
|                            |
|<-- pong (enc) -------------|
|                            |
|--- [operations] (enc) ---->|
|                            |
```

---

## Message Types

### 1. Handshake (Unencrypted)

#### `hello` (PC → Phone)

```json
{
  "type": "hello",
  "version": 1,
  "pairingId": "abc123def456",
  "codeHash": "base64(sha256(code))",
  "pcPub": "base64(x25519_public_key)",
  "pcNonce": "random_nonce_1",
  "sessionTtlMs": 900000
}
```

#### `hello_ack` (Phone → PC)

```json
{
  "type": "hello_ack",
  "pairingId": "abc123def456",
  "codeHash": "base64(sha256(code))",
  "phonePub": "base64(x25519_public_key)",
  "phoneNonce": "random_nonce_2",
  "proof": "base64(hmac_sha256(transcript))",
  "device": {
    "id": "device_id",
    "name": "CalcVault Phone",
    "fingerprint": "android_fingerprint",
    "model": "Pixel 6",
    "osVersion": "14"
  }
}
```

**Proof Computation:**
```
transcript = pairingId | pcPub | phonePub | pcNonce | phoneNonce | deviceId
proof = HMAC-SHA256(pairingCode, transcript)
```

### 2. Session Establishment (Encrypted)

#### `session_ready` (PC → Phone)

```json
{
  "type": "session_ready",
  "at": 1705318245123,
  "capabilities": [
    "backup.create",
    "backup.restore",
    "files.list",
    "files.upload",
    "files.download"
  ]
}
```

### 3. Heartbeat (Encrypted)

#### `ping` (PC → Phone)

```json
{
  "type": "ping",
  "ts": 1705318245123
}
```

#### `pong` (Phone → PC)

```json
{
  "type": "pong",
  "ts": 1705318245123
}
```

### 4. Operations

#### `op_request` (PC → Phone)

```json
{
  "type": "op_request",
  "requestId": "req_abc123",
  "op": "backup.create",
  "args": {
    "requireApproval": true,
    "protocol": 1
  },
  "requestedAt": 1705318245123
}
```

**Supported Operations:**
- `backup.create` - Create encrypted backup
- `backup.restore` - Restore from backup
- `files.list` - Get file index
- `files.upload` - Upload file to phone
- `files.download` - Download file from phone

#### `op_authorized` (Phone → PC)

```json
{
  "type": "op_authorized",
  "requestId": "req_abc123",
  "approved": true,
  "token": "optional_auth_token"
}
```

Or denied:
```json
{
  "type": "op_authorized",
  "requestId": "req_abc123",
  "approved": false,
  "reason": "user_denied"
}
```

#### `op_event` (Phone → PC)

Progress/status updates during operation:

```json
{
  "type": "op_event",
  "requestId": "req_abc123",
  "eventType": "backup_chunk",
  "data": {
    "chunk": "base64(encrypted_data)",
    "index": 0,
    "total": 100
  }
}
```

#### `op_chunk` (PC → Phone or Phone → PC)

Data transfer during operation:

```json
{
  "type": "op_chunk",
  "requestId": "req_abc123",
  "index": 0,
  "chunk": "base64(data)"
}
```

#### `op_chunk_end` (PC → Phone or Phone → PC)

Signal end of data transfer:

```json
{
  "type": "op_chunk_end",
  "requestId": "req_abc123",
  "metadata": {
    "finalChunkIndex": 99,
    "fileName": "backup.cvb",
    "size": 1048576
  }
}
```

#### `op_result` (Phone → PC)

Operation completion:

```json
{
  "type": "op_result",
  "requestId": "req_abc123",
  "ok": true,
  "result": {
    "backupId": "backup_123",
    "sha256": "abc123...",
    "size": 1048576,
    "snapshotTs": 1705318245123
  }
}
```

Or error:
```json
{
  "type": "op_result",
  "requestId": "req_abc123",
  "ok": false,
  "error": "operation_failed"
}
```

### 5. Session Management (Encrypted)

#### `session_expired` (Phone → PC)

```json
{
  "type": "session_expired"
}
```

---

## Encryption Format

### Encrypted Message Envelope

```json
{
  "type": "enc",
  "sid": "session_id",
  "seq": 1,
  "iv": "base64(nonce)",
  "ct": "base64(ciphertext)",
  "tag": "base64(auth_tag)"
}
```

### Nonce Construction

```
nonce = 12 bytes
  [0:4]   = "CVB1" (magic)
  [4]     = direction_flag (1=PC→Phone, 2=Phone→PC)
  [5:12]  = sequence number (big-endian uint32)
```

### Encryption/Decryption

```
Algorithm: AES-256-GCM
Key: 32 bytes (derived from ECDH + HKDF-SHA256)
Nonce: 12 bytes (directional)
AAD: empty
Plaintext: JSON message (UTF-8)
```

### Session Key Derivation

```
shared_secret = ECDH(pc_private, phone_public)
salt = SHA256(pairingCode || pairingId || pcNonce || phoneNonce)
session_key = HKDF-SHA256(
  hash=SHA256,
  ikm=shared_secret,
  salt=salt,
  info="calcvault-session-v1",
  length=32
)
```

---

## Operation Flows

### Backup Create

```
PC                          Phone
|                            |
|--- op_request (backup.create) --->|
|                            |
|<-- op_authorized (approved) ------|
|                            |
|<-- op_event (backup_manifest) ----|
|                            |
|<-- op_chunk (data) --------|
|<-- op_chunk (data) --------|
|<-- op_chunk (data) --------|
|                            |
|<-- op_chunk_end ----------|
|                            |
|<-- op_result (ok) ---------|
|                            |
```

### Backup Restore

```
PC                          Phone
|                            |
|--- op_request (backup.restore) -->|
|                            |
|<-- op_authorized (approved) ------|
|                            |
|--- op_chunk (data) ------->|
|--- op_chunk (data) ------->|
|--- op_chunk (data) ------->|
|                            |
|--- op_chunk_end ---------->|
|                            |
|<-- op_result (ok) ---------|
|                            |
```

### File Upload

```
PC                          Phone
|                            |
|--- op_request (files.upload) ---->|
|                            |
|<-- op_authorized (approved) ------|
|                            |
|--- op_chunk (file data) --->|
|--- op_chunk (file data) --->|
|                            |
|--- op_chunk_end ---------->|
|                            |
|<-- op_result (fileId) -----|
|                            |
```

### File Download

```
PC                          Phone
|                            |
|--- op_request (files.download) -->|
|                            |
|<-- op_authorized (approved) ------|
|                            |
|<-- op_chunk (file data) ---|
|<-- op_chunk (file data) ---|
|                            |
|<-- op_chunk_end ----------|
|                            |
|<-- op_result (ok) ---------|
|                            |
```

---

## Error Codes

### Connection Errors
- `not_connected` - No active connection
- `usb_transport_disabled` - USB mode disabled
- `network_transport_disabled` - Network mode disabled
- `adb_forward_failed` - ADB forward setup failed

### Pairing Errors
- `pairing_not_active` - No pairing in progress
- `pairing_identity_mismatch` - Pairing ID mismatch
- `pairing_proof_invalid` - HMAC proof verification failed
- `pairing_expired` - Pairing code expired

### Session Errors
- `session_not_paired` - No active session
- `session_expired` - Session TTL exceeded
- `replay_detected` - Sequence number replay
- `nonce_mismatch` - Nonce validation failed
- `invalid_envelope` - Malformed encrypted message

### Operation Errors
- `operation_not_found` - Request ID not found
- `operation_not_authorized` - Operation not approved
- `operation_timeout` - Operation exceeded timeout
- `operation_denied_by_phone` - User denied on phone
- `backup_not_found` - Backup ID not found
- `backup_hash_mismatch` - SHA256 integrity check failed
- `backup_empty_stream` - No data in backup

---

## Timeouts

| Operation | Timeout |
|-----------|---------|
| Connect | 15s |
| Pairing | 5 min |
| Session | 15 min (auto-extend) |
| Operation | 60s |
| Heartbeat | 15s interval |

---

## Security Considerations

1. **Replay Protection:** Sequence numbers prevent replay attacks
2. **Directional Nonces:** Different nonce construction for each direction
3. **Proof Verification:** HMAC prevents MITM during pairing
4. **Session Binding:** Session ID tied to device fingerprint
5. **Operation Authorization:** All sensitive ops require phone approval
6. **Integrity:** SHA256 verification for backups and files

---

## Example: Complete Pairing Flow

### Step 1: PC sends hello

```json
{
  "type": "hello",
  "version": 1,
  "pairingId": "pair_abc123",
  "codeHash": "base64(sha256('ABC12345'))",
  "pcPub": "base64(x25519_pub)",
  "pcNonce": "nonce_pc_123",
  "sessionTtlMs": 900000
}
```

### Step 2: Phone sends hello_ack

```json
{
  "type": "hello_ack",
  "pairingId": "pair_abc123",
  "codeHash": "base64(sha256('ABC12345'))",
  "phonePub": "base64(x25519_pub)",
  "phoneNonce": "nonce_phone_456",
  "proof": "base64(hmac_sha256('ABC12345', transcript))",
  "device": {
    "id": "device_xyz",
    "name": "My Phone",
    "fingerprint": "...",
    "model": "Pixel 6",
    "osVersion": "14"
  }
}
```

### Step 3: PC derives session key and sends session_ready

```json
{
  "type": "enc",
  "sid": "pair_abc123",
  "seq": 1,
  "iv": "base64(nonce)",
  "ct": "base64(aes_encrypt(session_ready_json))",
  "tag": "base64(auth_tag)"
}
```

### Step 4: Session established

Both sides can now send encrypted messages with incrementing sequence numbers.

---

## Backward Compatibility

- Version field in hello message
- Capabilities list in session_ready
- Graceful handling of unknown message types
- Future versions can add new operation types

