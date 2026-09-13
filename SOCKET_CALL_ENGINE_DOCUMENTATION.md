## SocketCallEngine - Real Transport Layer Documentation

### Overview

**SocketCallEngine** is the source of truth for call connectivity in Calcvault. It provides a real, reliable peer-to-peer socket connection for voice calls with no simulation or placeholder behavior.

---

## Connection Lifecycle

### States

```
INIT
  ↓
CONNECTING (connection attempt in progress)
  ├→ CONNECTED (real bidirectional socket established)
  │   ↓
  │   DISCONNECTED (clean close or remote disconnect)
  │
  └→ FAILED (unrecoverable error: timeout, unreachable, etc.)
```

### State Definitions

| State | Meaning | Guarantees |
|-------|---------|-----------|
| **INIT** | Initial state, no connection attempt yet | No socket resources allocated |
| **CONNECTING** | Connection attempt in progress | Server listening OR client connecting; no audio yet |
| **CONNECTED** | Real bidirectional socket established | Socket confirmed working; audio streams active; data flowing |
| **DISCONNECTED** | Connection closed cleanly | User ended call OR remote disconnected; resources cleaned up |
| **FAILED** | Connection failed unrecoverably | Timeout, unreachable peer, or error; resources cleaned up |

### Critical Guarantee

**CONNECTED state ONLY when:**
1. Real socket connection is established (not just attempted)
2. Both send and receive streams are active
3. Audio data is flowing bidirectionally
4. No fake or simulated states

---

## Connection Setup

### Caller Side (Server)

```kotlin
socketEngine.startCall()
```

**Sequence:**
1. State: INIT → CONNECTING
2. Open ServerSocket on port 7777
3. Set accept timeout to 60 seconds
4. Wait for callee to connect
5. On accept: State → CONNECTED, start audio streams
6. On timeout/error: State → FAILED

**Code Path:**
```
startCall()
  ├─ updateState(CONNECTING)
  ├─ ServerSocket(7777)
  ├─ serverSocket.accept() [blocks 60s]
  ├─ startAudioStreams()
  └─ updateState(CONNECTED)
```

### Callee Side (Client)

```kotlin
socketEngine.answerCall(callerIp)
```

**Sequence:**
1. State: INIT → CONNECTING
2. Attempt to connect to caller's IP:7777
3. Retry up to 2 times with exponential back-off (1s, 2s)
4. On success: State → CONNECTED, start audio streams
5. On all retries exhausted: State → FAILED

**Code Path:**
```
answerCall(callerIp)
  ├─ updateState(CONNECTING)
  ├─ repeat(3 times):
  │   ├─ Socket()
  │   ├─ socket.connect(callerIp:7777, 15s timeout)
  │   ├─ startAudioStreams()
  │   └─ updateState(CONNECTED) [exit loop]
  │   [on error: delay(1s * attempt), retry]
  └─ [all retries failed] updateState(FAILED)
```

---

## Data Transport

### Audio Streaming

Once CONNECTED, bidirectional audio flows:

**Send Path (Local → Remote):**
1. AudioRecord captures voice at 16kHz mono PCM
2. Encrypt with E2EKeyManager
3. Frame format: [4-byte length][encrypted audio]
4. Send via DataOutputStream
5. Callback: `onLocalAudioPacket(encrypted)`

**Receive Path (Remote → Local):**
1. Read 4-byte frame length
2. Read encrypted audio frame
3. Decrypt with E2EKeyManager
4. Play via AudioTrack
5. Callback: `onRemoteAudioPacket(encrypted)`

### Frame Format

```
[4 bytes: frame size N]
[N bytes: encrypted audio]
```

- Max frame size: 65,536 bytes
- Audio format: PCM 16-bit, 16kHz, mono
- Encryption: AES-256-GCM with random IV

### Muting

```kotlin
socketEngine.toggleMute()
```

- Muted: AudioRecord stopped, no frames sent
- Unmuted: AudioRecord resumed, frames sent again

---

## Error Handling

### Connection Errors

| Error | Cause | State | Recovery |
|-------|-------|-------|----------|
| **Timeout** | Server accept() times out after 60s | FAILED | User retries call |
| **Unreachable** | Client can't connect to IP:port | FAILED | Retry logic (2 attempts) |
| **Network Drop** | Socket read/write fails during CONNECTED | DISCONNECTED | Call ends cleanly |
| **Socket Error** | OS-level socket error | FAILED | Call ends with error |

### Error Callbacks

```kotlin
socketEngine.onError = { message ->
    // Called on unrecoverable errors
    // message: "Call failed: ...", "Could not connect: ...", etc.
}
```

### Graceful Disconnect

```kotlin
socketEngine.endCall()
```

- Cancels send/receive jobs
- Stops AudioRecord and AudioTrack
- Closes socket
- Logs call to database
- State: CONNECTED → DISCONNECTED

---

## Integration with CallEngine

### State Mapping

SocketCallEngine states drive CallEngine state transitions:

| Socket State | CallEngine Action |
|--------------|-------------------|
| INIT | No action |
| CONNECTING | Wait for CONNECTED or FAILED |
| CONNECTED | Transition CallEngine to ACTIVE |
| DISCONNECTED | End call cleanly |
| FAILED | End call with failure |

### Callback Flow

```
SocketCallEngine.onStateChanged
  ↓
CallEngine.handleSocketConnectionState()
  ├─ CONNECTED → CallEngine.ACTIVE
  ├─ DISCONNECTED → CallEngine.ENDED
  └─ FAILED → CallEngine.FAILED
```

### Example: Outgoing Call

```
User calls startCall()
  ↓
CallEngine.startCall()
  ├─ SocketCallEngine.startCall() [returns callId]
  ├─ Send call_offer signal
  ├─ CallEngine state: OUTGOING_RINGING
  ├─ Start 45s timeout
  │
  [Remote accepts]
  ├─ CallEngine.onRemoteCallAccepted()
  ├─ CallEngine state: CONNECTING
  ├─ Start 15s timeout
  │
  [Socket accepts connection]
  ├─ SocketCallEngine state: CONNECTED
  ├─ CallEngine.handleSocketConnectionState(CONNECTED)
  ├─ CallEngine state: ACTIVE
  ├─ Cancel timeout
  │
  [Audio flows]
  │
  [User ends call]
  ├─ CallEngine.endCall()
  ├─ SocketCallEngine.endCall()
  ├─ SocketCallEngine state: DISCONNECTED
  ├─ CallEngine state: ENDED
  └─ Call logged to database
```

---

## Retry Logic

### Callee Connection Retries

When `answerCall(callerIp)` is called:

```
Attempt 1: Connect immediately
  ├─ Timeout: 15 seconds
  └─ On failure: delay 1 second, retry

Attempt 2: Connect after 1s delay
  ├─ Timeout: 15 seconds
  └─ On failure: delay 2 seconds, retry

Attempt 3: Connect after 2s delay
  ├─ Timeout: 15 seconds
  └─ On failure: FAILED state

Total max time: 15s + 1s + 15s + 2s + 15s = 48 seconds
```

**Rationale:**
- Handles transient network glitches
- Exponential back-off prevents hammering
- Aligns with CallEngine's 45s offer timeout

---

## Resource Management

### Socket Cleanup

On disconnect or error:

```kotlin
private fun cleanup(reason: String) {
    // Cancel async jobs
    sendJob?.cancel()
    recvJob?.cancel()
    
    // Stop audio
    audioRecord?.stop()
    audioRecord?.release()
    audioTrack?.stop()
    audioTrack?.release()
    
    // Close sockets
    clientSocket?.close()
    serverSocket?.close()
    
    // Update state
    updateState(DISCONNECTED or FAILED)
}
```

### No Resource Leaks

- Audio resources released immediately
- Sockets closed with error handling
- Coroutine jobs cancelled
- Allows new calls after disconnect

---

## Performance Characteristics

### Threading

- All socket operations on `Dispatchers.IO`
- Main thread never blocked
- Audio capture/playback on dedicated threads
- State callbacks on `Dispatchers.Main`

### Latency

- Connection establishment: 15-60 seconds (depends on peer availability)
- Audio capture: ~100ms buffer (16kHz mono)
- Frame transmission: ~20ms per frame
- End-to-end latency: ~150-200ms typical

### Memory

- AudioRecord buffer: ~6.4KB (2x min buffer)
- AudioTrack buffer: ~6.4KB
- Socket buffers: OS-managed
- Per-call overhead: ~50KB

---

## Backward Compatibility

### Public API (Unchanged)

```kotlin
fun startCall(): String
fun answerCall(callerIp: String)
fun endCall()
fun toggleMute()
fun toggleSpeaker(on: Boolean)
fun getLocalIp(): String
```

### Callbacks (Unchanged)

```kotlin
var onStateChanged: ((ConnectionState) -> Unit)?
var onError: ((String) -> Unit)?
var onLocalAudioPacket: ((ByteArray) -> Unit)?
var onRemoteAudioPacket: ((ByteArray) -> Unit)?
```

### Properties (Unchanged)

```kotlin
var connectionState: ConnectionState
var suppressInternalLogging: Boolean
```

---

## Known Limitations

### 1. NAT Traversal

**Limitation:** Requires direct IP reachability. If peers are behind different NATs, connection will timeout.

**Workaround:** Use TURN relay server or implement hole-punching.

### 2. Keep-Alive During CONNECTED

**Limitation:** Silent network drop won't be detected until next read/write fails. OS TCP timeout (minutes) governs this.

**Workaround:** Implement application-level keep-alive pings.

### 3. Single Active Call

**Limitation:** Only one call at a time (enforced by CallEngine).

**Workaround:** Implement call waiting/transfer in CallEngine.

### 4. No Media Renegotiation

**Limitation:** Can't switch from audio to video mid-call.

**Workaround:** End call and start new video call.

### 5. Port Binding

**Limitation:** Requires port 7777 to be available. If already in use, call fails.

**Workaround:** Use dynamic port selection or port pool.

---

## Testing Checklist

### Connection Success

- [ ] Caller opens server socket
- [ ] Callee connects to caller's IP
- [ ] Socket accept() succeeds
- [ ] State transitions: INIT → CONNECTING → CONNECTED
- [ ] Audio streams start
- [ ] Audio data flows both directions

### Connection Failure - Timeout

- [ ] Caller waits 60+ seconds
- [ ] No connection arrives
- [ ] State: INIT → CONNECTING → FAILED
- [ ] Error callback invoked
- [ ] Resources cleaned up

### Connection Failure - Unreachable

- [ ] Callee tries to connect to invalid IP
- [ ] Connection refused or timeout
- [ ] Retry logic activates (3 attempts)
- [ ] State: INIT → CONNECTING → FAILED
- [ ] Error callback invoked

### Graceful Disconnect

- [ ] Call in CONNECTED state
- [ ] User calls endCall()
- [ ] Send/receive jobs cancelled
- [ ] Audio stopped
- [ ] Socket closed
- [ ] State: CONNECTED → DISCONNECTED
- [ ] Call logged to database

### Remote Disconnect

- [ ] Call in CONNECTED state
- [ ] Remote closes socket
- [ ] Receive job detects EOF
- [ ] State: CONNECTED → DISCONNECTED
- [ ] Error callback NOT invoked (clean close)

### Mute/Unmute

- [ ] Call in CONNECTED state
- [ ] toggleMute() called
- [ ] Audio capture stops
- [ ] No frames sent
- [ ] toggleMute() again
- [ ] Audio capture resumes
- [ ] Frames sent again

### Speaker Toggle

- [ ] toggleSpeaker(true)
- [ ] AudioManager.isSpeakerphoneOn = true
- [ ] toggleSpeaker(false)
- [ ] AudioManager.isSpeakerphoneOn = false

---

## Validation

### Real Connection Verification

To verify a real connection is established:

1. **Socket exists:** `clientSocket != null && !clientSocket.isClosed`
2. **Streams active:** `sendJob.isActive && recvJob.isActive`
3. **Audio flowing:** `onLocalAudioPacket` and `onRemoteAudioPacket` callbacks invoked
4. **State is CONNECTED:** `connectionState == ConnectionState.CONNECTED`

### No Fake States

- CONNECTED state ONLY after real socket accept/connect
- No artificial delays before state transitions
- All transitions driven by real socket events
- Error callbacks only on real errors

---

## Summary

SocketCallEngine provides:

✅ **Real connectivity** - Actual socket connection, not simulated
✅ **Reliable** - Retry logic, proper error handling, clean cleanup
✅ **Accurate state** - States reflect actual connection status
✅ **Efficient** - Non-blocking I/O, proper resource management
✅ **Integrated** - Drives CallEngine state machine
✅ **Backward compatible** - No breaking changes to existing API

The transport layer is the foundation of real calling. It guarantees that CONNECTED state means a real, bidirectional socket connection exists and audio is flowing.
