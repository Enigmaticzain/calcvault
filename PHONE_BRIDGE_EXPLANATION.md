# ✅ YES - Phone Has Built-In Bridge Server

## Answer to Your Question

**YES, the CalcVault Android app has a built-in WebSocket bridge server included in the APK.**

This means:
- ✅ **No additional app needed** on the phone
- ✅ **Just generating a pairing code is enough** to establish connection
- ✅ The phone bridge server runs as a service
- ✅ PC companion connects to this phone bridge

---

## How It Works

### Phone Side (Built-In)

The Android app includes:

**CompanionBridgeService.kt** - Runs as a background service
- Starts WebSocket server on port 37111
- Listens for PC connections
- Manages pairing state
- Handles backup/restore/file operations

**CompanionBridgeServer.kt** - WebSocket server implementation
- Accepts connections from PC
- Handles pairing handshake
- Manages encrypted sessions
- Processes operations (backup, restore, files)

**CompanionPairingState.kt** - Pairing management
- Generates pairing codes
- Validates pairing
- Manages session lifecycle

### PC Side (Companion App)

The Node.js companion app:
- Connects to phone's WebSocket server
- Initiates pairing handshake
- Exchanges encryption keys
- Sends/receives operations

---

## Connection Flow

```
PC Companion                    Phone (CalcVault App)
    ↓                                    ↓
npm start                    Bridge service running
    ↓                                    ↓
Open browser                 Listening on ws://127.0.0.1:37111
    ↓                                    ↓
Click "Connect USB"          ADB forward setup
    ↓                                    ↓
Connect to ws://127.0.0.1:37111
    ↓                                    ↓
Send "hello" (unencrypted)   Receive "hello"
    ↓                                    ↓
                             Generate pairing code
                             Display QR code
    ↓                                    ↓
User approves on phone       Send "hello_ack"
    ↓                                    ↓
Receive "hello_ack"          
    ↓                                    ↓
Derive session key           Derive same session key
    ↓                                    ↓
Send "session_ready" (encrypted)
    ↓                                    ↓
                             Session established
    ↓                                    ↓
Ready for operations         Ready for operations
```

---

## Pairing Code Generation

### On Phone

When you start the bridge service:

```kotlin
val ticket = pairingState.issue()
// Generates:
// - pairingCode: "ABC12345" (8 alphanumeric)
// - pairingId: random UUID
// - QR code with pairing data
// - Expiry: 5 minutes
```

### On PC

When you click "Start Pairing":

```javascript
const ticket = await bridgeManager.startPairing()
// Generates:
// - pairingCode: "ABC12345" (matches phone)
// - QR code for scanning
// - Waits for phone approval
```

### Verification

Both sides verify:
1. Pairing code matches
2. HMAC-SHA256 proof is valid
3. Device fingerprints match
4. ECDH key exchange succeeds

---

## What Happens When You Approve

### On Phone

```kotlin
// User taps "Approve" in CalcVault app
// Phone sends hello_ack with:
// - Device ID
// - Device name
// - HMAC proof
// - Public key for ECDH
```

### On PC

```javascript
// Receives hello_ack
// Verifies HMAC proof
// Derives session key
// Sends session_ready (encrypted)
// Session established ✅
```

---

## Built-In Components

### Phone Bridge Files

Located in: `app/src/main/java/com/calcvault/companion/`

1. **CompanionBridgeService.kt** (100 lines)
   - Starts/stops bridge service
   - Manages pairing tickets
   - Handles service lifecycle

2. **CompanionBridgeServer.kt** (400+ lines)
   - WebSocket server
   - Pairing handshake
   - Session management
   - Operation handling

3. **CompanionBridgeConstants.kt**
   - Port: 37111
   - Path: /bridge
   - Session TTL: 15 minutes
   - Operation types

4. **CompanionPairingState.kt**
   - Pairing code generation
   - QR code creation
   - Pairing validation

5. **CompanionSessionCrypto.kt**
   - ECDH key exchange
   - Session key derivation
   - AES-256-GCM encryption
   - HMAC-SHA256 proof

6. **CompanionBackupManager.kt**
   - Backup creation
   - Backup streaming
   - Restore handling

7. **CompanionFileVault.kt**
   - File listing
   - File upload
   - File download

8. **CompanionOperationAuthorizer.kt**
   - Operation approval
   - Authorization logic

---

## What You Need to Do

### Step 1: Start Phone Bridge

The bridge starts automatically when:
- CalcVault app is open
- Bridge service is enabled
- Phone is connected to WiFi or USB

### Step 2: Generate Pairing Code

In CalcVault app:
1. Settings → Companion
2. Tap "Generate Pairing Code"
3. Code appears (e.g., `ABC12345`)
4. QR code displays

### Step 3: Approve on PC

In PC Companion:
1. Click "Start Pairing"
2. Scan QR or enter code
3. Verify code matches
4. Session established ✅

---

## Security Flow

### Pairing Handshake

```
PC sends: hello
  - Pairing ID
  - Code hash
  - Public key
  - Nonce

Phone sends: hello_ack
  - Pairing ID
  - Code hash
  - Public key
  - Nonce
  - HMAC proof
  - Device info
```

### Session Establishment

```
Both sides derive session key:
  shared_secret = ECDH(pc_private, phone_public)
  session_key = HKDF-SHA256(shared_secret, salt)

PC sends: session_ready (encrypted)
Phone receives and verifies encryption

Session established ✅
```

### Operation Flow

```
PC sends: op_request (encrypted)
  - Operation type
  - Arguments
  - Request ID

Phone sends: op_authorized (encrypted)
  - Approval status
  - Token

PC/Phone exchange: op_chunk (encrypted)
  - Data chunks
  - Sequence numbers

Phone sends: op_result (encrypted)
  - Success/failure
  - Result data
```

---

## Pairing Code is Enough

**YES, just generating a pairing code is enough because:**

1. ✅ **Phone bridge is built-in** - No separate app needed
2. ✅ **Pairing code is verified** - HMAC-SHA256 proof prevents MITM
3. ✅ **ECDH key exchange** - Secure key derivation
4. ✅ **Session encryption** - AES-256-GCM for all messages
5. ✅ **Device binding** - Session tied to phone fingerprint
6. ✅ **Operation authorization** - Phone approves all operations

---

## Complete Flow

### 1. Phone Bridge Running
```
CalcVault app open
→ CompanionBridgeService starts
→ WebSocket server listening on ws://127.0.0.1:37111
```

### 2. Generate Pairing Code
```
User: Settings → Companion → Generate Code
→ pairingState.issue()
→ Code: "ABC12345"
→ QR code generated
→ Expires in 5 minutes
```

### 3. PC Connects
```
npm start
→ Open browser
→ Click "Connect USB"
→ ADB forward setup
→ WebSocket connects to phone
```

### 4. Start Pairing
```
Click "Start Pairing"
→ Send hello (unencrypted)
→ Phone receives hello
→ Phone generates hello_ack
```

### 5. Approve on Phone
```
User: Scan QR or enter code
→ Verify code matches
→ Tap "Approve"
→ Phone sends hello_ack (unencrypted)
```

### 6. Session Established
```
PC receives hello_ack
→ Verify HMAC proof
→ Derive session key
→ Send session_ready (encrypted)
→ Phone receives and verifies
→ Session established ✅
```

### 7. Ready for Operations
```
Create backup
Upload files
Download files
Restore backup
All encrypted and authorized
```

---

## Summary

| Component | Location | Purpose |
|-----------|----------|---------|
| Bridge Server | Phone APK | WebSocket server on port 37111 |
| Pairing State | Phone APK | Generates and validates pairing codes |
| Session Crypto | Phone APK | ECDH + AES-256-GCM encryption |
| Backup Manager | Phone APK | Handles backup operations |
| File Vault | Phone APK | Handles file operations |
| PC Companion | Node.js | Connects to phone bridge |

---

## Answer: YES ✅

**Just generating a pairing code is enough because:**

1. ✅ Phone has built-in bridge server
2. ✅ Pairing code is cryptographically verified
3. ✅ ECDH key exchange is secure
4. ✅ All operations are encrypted
5. ✅ Phone approves all operations
6. ✅ No additional app needed

**The flow is:**
1. Start phone bridge (automatic)
2. Generate pairing code (5 minutes)
3. PC connects and initiates pairing
4. User approves on phone
5. Session established
6. Ready for operations

---

## Next Steps

1. ✅ Ensure CalcVault app is open
2. ✅ Go to Settings → Companion
3. ✅ Generate pairing code
4. ✅ On PC: Click "Start Pairing"
5. ✅ Scan QR or enter code
6. ✅ Approve on phone
7. ✅ Done! Create backup

---

**Everything is built-in. Just follow the pairing flow!** 🔐
