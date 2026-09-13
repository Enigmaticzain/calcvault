# CalcVault Companion - Security Architecture

## Executive Summary

CalcVault Companion is a **local-first secure bridge** between PC and Android phone. It implements:

- **End-to-end encryption** for all sensitive data
- **Device-bound sessions** tied to phone identity
- **Operation authorization** requiring phone approval
- **Integrity verification** for backups and files
- **Replay protection** with directional nonces
- **Local-only access** by default

---

## Threat Model

### Threat 1: Unauthorized PC Access

**Scenario:** Attacker gains access to PC running companion.

**Controls:**
- ✅ One-time launch token (random 18 bytes)
- ✅ Session-based API tokens (random 24 bytes)
- ✅ HttpOnly cookies prevent JavaScript access
- ✅ SameSite=Strict prevents CSRF
- ✅ Local-only binding (127.0.0.1 default)

**Residual Risk:** Low (requires local machine compromise)

### Threat 2: Network Eavesdropping

**Scenario:** Attacker intercepts USB or WiFi traffic.

**Controls:**
- ✅ AES-256-GCM encryption for all session messages
- ✅ ECDH (X25519) key exchange
- ✅ HMAC-SHA256 proof verification
- ✅ Directional nonces prevent replay
- ✅ Sequence numbers detect out-of-order messages

**Residual Risk:** Very Low (AES-256 is cryptographically secure)

### Threat 3: Man-in-the-Middle (MITM)

**Scenario:** Attacker intercepts pairing handshake.

**Controls:**
- ✅ Pairing code (8 alphanumeric) shown on both devices
- ✅ QR code for visual verification
- ✅ HMAC proof prevents code substitution
- ✅ Device fingerprint binding
- ✅ Proof verification before session establishment

**Residual Risk:** Low (requires user to accept wrong code)

### Threat 4: Unauthorized Operations

**Scenario:** Attacker tries to backup/restore without phone approval.

**Controls:**
- ✅ All sensitive operations require phone approval
- ✅ Operation request includes full context
- ✅ Phone user can review and deny
- ✅ Timeout prevents hanging requests
- ✅ Session binding prevents cross-device attacks

**Residual Risk:** Very Low (requires phone compromise)

### Threat 5: Backup Tampering

**Scenario:** Attacker modifies backup file on PC.

**Controls:**
- ✅ SHA256 integrity verification
- ✅ Metadata stored separately
- ✅ Restore validates hash before applying
- ✅ Atomic writes prevent partial files
- ✅ Encrypted storage prevents plaintext access

**Residual Risk:** Low (tampering detected on restore)

### Threat 6: Session Hijacking

**Scenario:** Attacker steals session token.

**Controls:**
- ✅ Session tokens are random (24 bytes)
- ✅ Sessions expire after 15 minutes
- ✅ Sessions auto-extend on activity
- ✅ Device fingerprint binding
- ✅ Sequence numbers prevent replay

**Residual Risk:** Low (requires local machine compromise)

### Threat 7: Replay Attacks

**Scenario:** Attacker replays captured messages.

**Controls:**
- ✅ Sequence numbers (monotonically increasing)
- ✅ Directional nonces (different for each direction)
- ✅ Nonce validation before decryption
- ✅ Sequence check rejects old messages
- ✅ Timestamp validation in operations

**Residual Risk:** Very Low (cryptographic protection)

### Threat 8: Denial of Service

**Scenario:** Attacker floods companion with requests.

**Controls:**
- ✅ Operation timeouts (60 seconds default)
- ✅ Rate limiting on API endpoints
- ✅ Connection limits
- ✅ Resource cleanup on disconnect
- ✅ Memory limits on uploads

**Residual Risk:** Medium (DoS always possible, mitigated by timeouts)

---

## Cryptographic Primitives

### Key Exchange: ECDH (X25519)

```
PC generates: (pc_private, pc_public)
Phone generates: (phone_private, phone_public)

Shared secret = ECDH(pc_private, phone_public)
              = ECDH(phone_private, pc_public)
```

**Security:** 128-bit equivalent strength (256-bit key)

### Session Key Derivation: HKDF-SHA256

```
salt = SHA256(pairingCode || pairingId || pcNonce || phoneNonce)
session_key = HKDF-SHA256(
  ikm=shared_secret,
  salt=salt,
  info="calcvault-session-v1",
  length=32
)
```

**Security:** Extracts entropy from shared secret, expands to 256-bit key

### Encryption: AES-256-GCM

```
Algorithm: AES-256-GCM
Key: 32 bytes (from HKDF)
Nonce: 12 bytes (directional)
AAD: empty
Plaintext: JSON message (UTF-8)
```

**Security:** 256-bit key, authenticated encryption, 128-bit auth tag

### Authentication: HMAC-SHA256

```
proof = HMAC-SHA256(
  key=pairingCode,
  message=transcript
)
```

**Security:** 256-bit output, timing-safe comparison

---

## Nonce Construction

### Directional Nonce

```
nonce = 12 bytes
  [0:4]   = "CVB1" (magic, prevents cross-protocol attacks)
  [4]     = direction_flag (1=PC→Phone, 2=Phone→PC)
  [5:12]  = sequence number (big-endian uint32)
```

**Security:**
- Magic prevents using nonce in other protocols
- Direction prevents bidirectional replay
- Sequence prevents reuse within direction

---

## Session Lifecycle

### 1. Connection

```
PC connects to phone via USB (ADB forward) or WiFi (WebSocket)
```

**Security:** Transport-level security (TLS for WiFi recommended)

### 2. Pairing

```
PC sends: hello (unencrypted)
  - Pairing ID
  - Code hash
  - Public key
  - Nonce

Phone sends: hello_ack (unencrypted)
  - Pairing ID
  - Code hash
  - Public key
  - Nonce
  - HMAC proof
  - Device info
```

**Security:** Proof prevents MITM, code shown to user for verification

### 3. Session Establishment

```
PC derives session key from ECDH + HKDF
PC sends: session_ready (encrypted)
Phone derives session key (same derivation)
Phone validates encryption
```

**Security:** Both sides derive same key, encryption proves key agreement

### 4. Heartbeat

```
PC sends: ping (encrypted) every 15 seconds
Phone responds: pong (encrypted)
```

**Security:** Detects disconnection, keeps session alive

### 5. Operations

```
PC sends: op_request (encrypted)
Phone sends: op_authorized (encrypted, requires user approval)
PC/Phone exchange: op_chunk (encrypted)
Phone sends: op_result (encrypted)
```

**Security:** All operations encrypted, require approval, timeout protected

### 6. Disconnection

```
Either side sends: close frame
Session state cleared
Pending operations rejected
```

**Security:** Clean shutdown, no lingering state

---

## Data Protection

### Backups

**At Rest:**
- Stored as `.cvb` files (encrypted binary)
- Metadata in `.meta.json` (plaintext, non-sensitive)
- SHA256 hash for integrity
- Atomic writes prevent partial files

**In Transit:**
- Encrypted with AES-256-GCM
- Chunked transfer (256KB default)
- Sequence numbers prevent reordering
- Integrity verified on restore

**Restore:**
- Hash verified before applying
- Atomic restore (all-or-nothing)
- Phone validates before committing

### Files

**Upload (PC → Phone):**
- Encrypted in transit
- Phone stores encrypted
- Metadata indexed on phone
- PC only sees metadata

**Download (Phone → PC):**
- Encrypted in transit
- PC can request encrypted or decrypted
- Decryption happens on phone (optional)
- Integrity verified

### Session Data

**In Memory:**
- Session key in memory (cleared on disconnect)
- Sequence numbers in memory
- Pending operations in memory
- Cleared on session expiry

**On Disk:**
- No session keys stored
- No sensitive data cached
- Backups encrypted
- Logs don't contain secrets

---

## Access Control

### Operation Authorization

All sensitive operations require phone approval:

```
backup.create    → User must approve on phone
backup.restore   → User must approve on phone
files.upload     → User must approve on phone
files.download   → User must approve on phone
files.list       → User must approve on phone
```

### Session Binding

Sessions are bound to:
- Device ID (phone fingerprint)
- Session ID (random)
- Pairing code (user-verified)

Cannot be transferred between devices.

### API Token

- Random 24 bytes per session
- Sent in `x-calcvault-ui-token` header
- Validated on every API call
- Expires with session

---

## Timeout Protection

| Operation | Timeout | Purpose |
|-----------|---------|---------|
| Connect | 15s | Prevent hanging connections |
| Pairing | 5 min | Prevent stale pairing codes |
| Session | 15 min | Prevent session hijacking |
| Operation | 60s | Prevent hanging operations |
| Heartbeat | 15s | Detect disconnection |

---

## Input Validation

### Message Validation

```javascript
// All incoming messages validated:
- Type field present and valid
- Required fields present
- Field types correct
- String lengths bounded
- Numbers in valid ranges
- Base64 strings decodable
```

### File Validation

```javascript
// All file uploads validated:
- File size within limits (5GB default)
- MIME type checked
- File name sanitized
- No path traversal
- Atomic write prevents corruption
```

### Operation Validation

```javascript
// All operations validated:
- Request ID present and unique
- Operation type supported
- Arguments valid for operation
- User approved on phone
- Session active and not expired
```

---

## Error Handling

### Sensitive Errors

Errors don't leak sensitive information:

```javascript
// ✅ Good
throw new Error("operation_failed")

// ❌ Bad
throw new Error("backup_failed: /home/user/secret.txt")
```

### Logging

Logs don't contain:
- Session keys
- API tokens
- Pairing codes
- File contents
- User data

---

## Deployment Security

### Local-Only Access (Default)

```bash
CV_BIND_HOST=127.0.0.1
```

Only accessible from same machine. Recommended for:
- Personal use
- Trusted networks
- Development

### Network Access (Optional)

```bash
CV_BIND_HOST=0.0.0.0
```

Accessible from network. Requires:
- Firewall rules
- TLS/SSL (recommended)
- Strong session management
- Regular updates

### Firewall Rules

```bash
# Allow only local
sudo ufw allow from 127.0.0.1 to 127.0.0.1 port 43831

# Allow from trusted network
sudo ufw allow from 192.168.1.0/24 to any port 43831
```

### TLS/SSL (Optional)

For network deployments, use reverse proxy:

```nginx
server {
    listen 443 ssl;
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location / {
        proxy_pass http://127.0.0.1:43831;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## Security Best Practices

### For Users

1. ✅ Keep phone and PC on same trusted network
2. ✅ Verify pairing code matches on both devices
3. ✅ Don't share pairing codes
4. ✅ Disconnect when not in use
5. ✅ Keep CalcVault app updated
6. ✅ Use strong device passwords
7. ✅ Enable USB debugging only when needed
8. ✅ Review operation requests on phone

### For Administrators

1. ✅ Run companion on trusted machine
2. ✅ Use firewall to restrict access
3. ✅ Monitor logs for errors
4. ✅ Keep Node.js updated
5. ✅ Use TLS for network deployments
6. ✅ Rotate backups regularly
7. ✅ Test restore procedures
8. ✅ Audit access logs

### For Developers

1. ✅ Use timing-safe comparisons for secrets
2. ✅ Validate all inputs
3. ✅ Clear sensitive data from memory
4. ✅ Use secure random generators
5. ✅ Don't log sensitive data
6. ✅ Use HTTPS for external APIs
7. ✅ Keep dependencies updated
8. ✅ Run security audits

---

## Compliance

### Data Protection

- ✅ No data stored without encryption
- ✅ No data transmitted without encryption
- ✅ No data retained after session ends
- ✅ User can delete backups anytime
- ✅ No telemetry or tracking

### Privacy

- ✅ Local-first (no cloud)
- ✅ No third-party services
- ✅ No data sharing
- ✅ User has full control
- ✅ Transparent operation

### Security

- ✅ Industry-standard cryptography
- ✅ Secure key exchange
- ✅ Authenticated encryption
- ✅ Integrity verification
- ✅ Replay protection

---

## Incident Response

### If Pairing Code Leaked

1. Don't approve pairing on phone
2. Pairing code expires in 5 minutes
3. Start new pairing with new code
4. No data compromised (code not used)

### If Session Token Leaked

1. Disconnect immediately
2. Session expires in 15 minutes
3. Start new session
4. No data compromised (token alone insufficient)

### If Backup Corrupted

1. Restore fails with hash mismatch
2. Backup not applied
3. Try different backup
4. Check disk for errors

### If Connection Compromised

1. Disconnect immediately
2. Check firewall logs
3. Verify phone is trusted
4. Start new pairing
5. Review operation logs

---

## Security Audit Checklist

- [ ] Encryption verified (AES-256-GCM)
- [ ] Key exchange verified (ECDH X25519)
- [ ] Proof verification tested
- [ ] Replay protection tested
- [ ] Session expiry tested
- [ ] Operation authorization tested
- [ ] Backup integrity tested
- [ ] Error handling reviewed
- [ ] Input validation tested
- [ ] Timeout protection tested
- [ ] Firewall rules verified
- [ ] Logs reviewed for leaks
- [ ] Dependencies audited
- [ ] Code reviewed for vulnerabilities

---

## Future Enhancements

- [ ] TLS 1.3 for network transport
- [ ] Hardware security module (HSM) support
- [ ] Multi-device pairing
- [ ] Backup encryption key rotation
- [ ] Audit logging
- [ ] Rate limiting per device
- [ ] Geofencing (optional)
- [ ] Biometric re-authentication

---

## References

- NIST SP 800-38D (GCM)
- RFC 7748 (X25519)
- RFC 5869 (HKDF)
- OWASP Top 10
- CWE Top 25

