# SocketCallEngine - Quick Reference

## Connection States

```
INIT ──→ CONNECTING ──→ CONNECTED ──→ DISCONNECTED
                    ↘ FAILED
```

| State | Meaning | Audio |
|-------|---------|-------|
| INIT | No connection attempt | No |
| CONNECTING | Attempt in progress | No |
| CONNECTED | Real socket established | Yes |
| DISCONNECTED | Clean close | No |
| FAILED | Error/timeout | No |

---

## Public API

### Caller (Server)

```kotlin
val callId = socketEngine.startCall()
// State: INIT → CONNECTING → CONNECTED (on accept) or FAILED (on timeout)
```

### Callee (Client)

```kotlin
socketEngine.answerCall(callerIp)
// State: INIT → CONNECTING → CONNECTED (on success) or FAILED (on all retries)
// Retries: 3 attempts with 1s, 2s back-off
```

### End Call

```kotlin
socketEngine.endCall()
// State: CONNECTED → DISCONNECTED (or CONNECTING → DISCONNECTED)
```

### Controls

```kotlin
socketEngine.toggleMute()           // Stop/resume audio capture
socketEngine.toggleSpeaker(true)    // Enable speaker
socketEngine.getLocalIp()           // Get device IP for signaling
```

---

## Callbacks

```kotlin
socketEngine.onStateChanged = { state ->
    // Called on every state transition
    // state: INIT, CONNECTING, CONNECTED, DISCONNECTED, FAILED
}

socketEngine.onError = { message ->
    // Called on unrecoverable errors
    // message: "Call failed: ...", "Could not connect: ...", etc.
}

socketEngine.onLocalAudioPacket = { encrypted ->
    // Called when local audio is captured and encrypted
    // Use for recording or analytics
}

socketEngine.onRemoteAudioPacket = { encrypted ->
    // Called when remote audio is received and decrypted
    // Use for recording or analytics
}
```

---

## Timeouts

| Operation | Timeout | Behavior |
|-----------|---------|----------|
| Server accept() | 60 seconds | FAILED if no connection |
| Client connect() | 15 seconds per attempt | Retry up to 3 times |
| Total client time | ~48 seconds | 15s + 1s + 15s + 2s + 15s |

---

## Error Scenarios

### Timeout (Caller)

```
startCall()
  ├─ Wait 60 seconds
  ├─ No connection
  └─ State: FAILED
     onError("Call failed: Socket operation timed out")
```

### Unreachable (Callee)

```
answerCall(badIp)
  ├─ Attempt 1: Connection refused (15s)
  ├─ Delay 1s
  ├─ Attempt 2: Connection refused (15s)
  ├─ Delay 2s
  ├─ Attempt 3: Connection refused (15s)
  └─ State: FAILED
     onError("Could not connect: Connection refused")
```

### Network Drop (During CONNECTED)

```
[Audio flowing]
  ├─ Network unavailable
  ├─ Socket read/write fails
  └─ State: DISCONNECTED
     (no error callback - clean close)
```

---

## Integration with CallEngine

### State Mapping

```
SocketCallEngine.ConnectionState → CallEngine.CallState

CONNECTED → ACTIVE (only way to reach ACTIVE)
DISCONNECTED → ENDED
FAILED → FAILED
```

### Callback Flow

```
SocketCallEngine.onStateChanged(CONNECTED)
  ↓
CallEngine.handleSocketConnectionState(CONNECTED)
  ├─ Cancel timeout
  ├─ Validate state
  └─ Update CallEngine to ACTIVE
```

---

## Audio Format

- **Sample Rate:** 16 kHz
- **Channels:** Mono
- **Encoding:** PCM 16-bit
- **Buffer:** 2x minimum (typically ~6.4KB)
- **Encryption:** AES-256-GCM with random IV
- **Frame Format:** [4-byte length][encrypted audio]
- **Max Frame:** 65,536 bytes

---

## Resource Cleanup

On disconnect or error:

```
✓ Cancel send/receive jobs
✓ Stop AudioRecord
✓ Release AudioRecord
✓ Stop AudioTrack
✓ Release AudioTrack
✓ Close client socket
✓ Close server socket
✓ Log call to database
✓ Update state
```

**Result:** No resource leaks, new calls can be made immediately

---

## Guarantees

✅ **CONNECTED state ONLY when:**
- Real socket connection established
- Both send and receive streams active
- Audio data flowing bidirectionally
- No fake or simulated states

✅ **FAILED state ONLY when:**
- Timeout (60s accept or 15s connect)
- Connection refused/unreachable
- Socket error
- All retries exhausted

✅ **DISCONNECTED state ONLY when:**
- User called endCall()
- Remote closed socket
- Clean EOF received

✅ **No resource leaks:**
- All resources released on cleanup
- New calls work after disconnect
- No dangling sockets or threads

---

## Testing

### Verify Real Connection

```kotlin
// Check socket exists
assert(socketEngine.connectionState == ConnectionState.CONNECTED)

// Check audio flowing
var localPackets = 0
var remotePackets = 0
socketEngine.onLocalAudioPacket = { localPackets++ }
socketEngine.onRemoteAudioPacket = { remotePackets++ }

// After 1 second
assert(localPackets > 0)
assert(remotePackets > 0)
```

### Verify Cleanup

```kotlin
socketEngine.endCall()
delay(100)

// Check state
assert(socketEngine.connectionState == ConnectionState.DISCONNECTED)

// Check resources released
// (AudioRecord, AudioTrack, Sockets all null)
```

---

## Known Limitations

1. **NAT Traversal:** Requires direct IP reachability
2. **Keep-Alive:** Silent network drop detected only on next I/O
3. **Single Call:** Only one call at a time
4. **Port Binding:** Requires port 7777 available
5. **No Media Renegotiation:** Can't switch audio↔video mid-call

---

## Files

| File | Purpose |
|------|---------|
| `SocketCallEngine.kt` | Transport layer implementation |
| `CallEngine.kt` | Call state machine (updated) |
| `SOCKET_CALL_ENGINE_DOCUMENTATION.md` | Comprehensive docs |
| `SOCKET_CALL_ENGINE_DELIVERY.md` | Delivery summary |

---

## Build & Test

```bash
# Build
./gradlew build

# Test outgoing call
# 1. Device A: startCall()
# 2. Device B: answerCall(A's IP)
# 3. Verify: CONNECTED state, audio flows
# 4. endCall() on either device
# 5. Verify: DISCONNECTED state

# Test timeout
# 1. Device A: startCall()
# 2. Wait 60+ seconds
# 3. Verify: FAILED state, error callback
```

---

## Summary

SocketCallEngine provides:

- ✅ Real P2P socket connections
- ✅ Proper lifecycle management
- ✅ Retry logic for unstable networks
- ✅ Clean resource management
- ✅ Integration with CallEngine
- ✅ No fake/simulated states
- ✅ Backward compatible

**The transport layer is the foundation of real calling.**
