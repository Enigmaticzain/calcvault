# Call Signaling Architecture - Stabilized Implementation

## Overview

The CallEngine has been stabilized to enforce **strict event-driven call state transitions** where all state changes are driven by:

1. **Real signaling events** (call_offer, call_accept, call_reject, call_end)
2. **Real transport state changes** (socket connection established)
3. **Explicit timeouts** (with clear guard conditions)

**GUARANTEE**: The ACTIVE state is **ONLY** reached when the transport layer confirms real connection. No fake or simulated success states.

---

## Call State Machine

```
IDLE (initial)
  ↓
  ├─→ [OUTGOING] startCall() sends call_offer
  │    ↓
  │    OUTGOING_RINGING (waiting for peer acceptance)
  │    │
  │    ├─→ Timeout 45s → FAILED (NO_ANSWER)
  │    ├─→ call_reject received → FAILED
  │    └─→ call_accept received → CONNECTING
  │         ↓
  │         CONNECTING (waiting for socket)
  │         │
  │         ├─→ Timeout 15s → FAILED (CONNECTION_TIMEOUT)
  │         └─→ Socket.ACTIVE → ACTIVE ✓
  │              ↓
  │              ACTIVE (transport confirmed)
  │              │
  │              ├─→ call_end received → ENDED
  │              ├─→ Socket.ENDED → ENDED
  │              └─→ endCall() → ENDED
  │
  └─→ [INCOMING] call_offer received → onIncomingCall()
       ↓
       INCOMING_RINGING (waiting for user response)
       │
       ├─→ Timeout 45s → FAILED (NO_ANSWER)
       ├─→ declineCall() → FAILED (DECLINED)
       └─→ answerCall() sends call_accept, connects socket
            ↓
            CONNECTING (waiting for socket)
            │
            ├─→ Timeout 15s → FAILED (CONNECTION_TIMEOUT)
            └─→ Socket.ACTIVE → ACTIVE ✓
                 ↓
                 ACTIVE (transport confirmed)
                 │
                 ├─→ call_end received → ENDED
                 ├─→ Socket.ENDED → ENDED
                 └─→ endCall() → ENDED
```

---

## Signaling Flow - Outgoing Call

### Timeline

```
Time  Local Side            Network              Remote Side
────  ──────────────────    ──────────────────   ──────────────────
 0ms  │                     │                    │
      startCall()           │                    │
      ├─ Create socket      │                    │
      │  server (listen)    │                    │
      ├─ State: IDLE        │                    │
      │                     │                    │
 10ms ├─ Send               →  call_offer      →  │
      │  call_offer         │                    IncomingCallLauncher
      │                     │                    triggered
      ├─ State:             │                    │
      │  OUTGOING_RINGING   │                    Launch CallActivity
      │                     │                    │
      ├─ Timeout:           │                    onIncomingCall()
      │  45s (NO_ANSWER)    │                    State: INCOMING_RINGING
      │                     │                    │
100ms │                     │                 ← call_accept
      │ Still waiting...    │                 ← (user tapped Answer)
      │                     │                    │
      │ onRemoteCallAccepted()                   answerCall()
      │ ├─ Cancel timeout   │                    ├─ Send call_accept
      │ ├─ State:           │                    │
      │ │  CONNECTING       │                    ├─ Socket client:
      │ ├─ Timeout:         │                    │  connect to caller
      │ │  15s (CONN_TO)    │                    │
      │                     │                    Socket.CONNECTING
150ms │                     │                    │
      │ Waiting for socket  │                    Waiting for socket
      │ connection...       │                    connection...
      │                     │                    │
      │ Socket.ACTIVE!      ← ← ← client connects to server
      │ (client accepted)   │                    │
      ├─ handleSocketState()                     Socket.ACTIVE!
      ├─ Cancel timeout    │                    │
      ├─ State: ACTIVE     │                    ├─ handleSocketState()
      │ (REAL!)            │                    ├─ Cancel timeout
      │                    │                    ├─ State: ACTIVE
Xms   │                    │                    │  (REAL!)
      │ (call active)      │                    │ (call active)
      │ Audio flowing ←  ←  ←  ←  ←  ← Audio flowing
      │                    │                    │
Nms   │ endCall()       →   call_end         →  │
      ├─ State: ENDED      │                    onRemoteCallEnded()
      │                    │                    ├─ State: ENDED
      └─ Cleanup           │                    └─ Cleanup
```

---

## Signaling Flow - Incoming Call

### Timeline

```
Time  Remote Side          Network              Local Side
────  ──────────────────   ──────────────────   ──────────────────
 0ms  startCall()          │                    │
      ├─ Server listen     │                    │
      ├─ State: IDLE       │                    │
      │                    │                    │
 10ms ├─ Send            →  call_offer        →  │
      │  call_offer        │                    IncomingCallLauncher
      │                    │                    triggered
      ├─ State:           │                    │
      │  OUTGOING_RINGING │                    Launch CallActivity
      │                    │                    │
      ├─ Timeout:          │                    onIncomingCall()
      │  45s (NO_ANSWER)   │                    State: INCOMING_RINGING
      │                    │                    │
100ms │                    │                    User reviews caller
      │ Still waiting...   │                    │
      │                    │                    │
      │                    │                    User taps [Answer]
      │                    │                    │
      │                    │                    answerCall()
      │                    │                    ├─ Send call_accept
      │                    │                    ├─ Socket client:
      │                    │                    │  connect to remote
      │                    │                    │
      │                    │                    Socket.CONNECTING
150ms │                    │                    │
      │                    │              ← ← ← Connecting...
      │                    │                    │
      │ onRemoteCallAccepted()                 │
      │ ├─ Cancel timeout  │                    │
      │ ├─ State:          │                    │
      │ │  CONNECTING      │                    │
      │ ├─ Timeout:        │                    │
      │ │  15s (CONN_TO)   │                    │
      │                    │                    │
      │ Socket client connected! (server accepted)
      │                    │                    │
      │ Socket.ACTIVE!    ← ← ← ← ← ← ← ← ← Socket.ACTIVE!
      │                    │                    │
      ├─ handleSocketState()                    ├─ handleSocketState()
      ├─ Cancel timeout    │                    ├─ Cancel timeout
      ├─ State: ACTIVE     │                    ├─ State: ACTIVE
      │ (REAL!)            │                    │ (REAL!)
Xms   │                    │                    │
      │ (call active)      │                    │ (call active)
      │ Audio flowing ←  ←  ←  ←  ←  ← Audio flowing
      │                    │                    │
Nms   │                  ← call_end         ←   endCall()
      │ onRemoteCallEnded()                     ├─ State: ENDED
      ├─ State: ENDED      │                    │
      │                    │                    └─ Cleanup
      └─ Cleanup           │
```

---

## Key Implementation Details

### 1. State Transitions are Event-Driven

**NOT time-based**. Each state transition is triggered by a real event:

- `IDLE → OUTGOING_RINGING`: Event = offer sent successfully
- `OUTGOING_RINGING → CONNECTING`: Event = received call_accept signal
- `INCOMING_RINGING → CONNECTING`: Event = user tapped answer button
- `CONNECTING → ACTIVE`: Event = socket reached ACTIVE state
- `ACTIVE → ENDED`: Event = call_end signal OR socket ended OR endCall() called

### 2. ACTIVE State is Only From Socket

In `handleSocketState()`:
```kotlin
SocketCallEngine.CallState.ACTIVE -> {
    // CRITICAL: Only transition from CONNECTING or OUTGOING_RINGING to ACTIVE
    if (call.state == CallState.CONNECTING || call.state == CallState.OUTGOING_RINGING) {
        updateCallState(call, CallState.ACTIVE, "Transport connected")
    }
}
```

**GUARANTEE**: No fake "ACTIVE" states. Socket must actually confirm connection.

### 3. Timeouts are Explicit Guards

Each timeout guards a specific transition:

| State | Timeout | Duration | Trigger | Action |
|-------|---------|----------|---------|--------|
| OUTGOING_RINGING | NO_ANSWER | 45s | No acceptance received | FAILED |
| INCOMING_RINGING | NO_ANSWER | 45s | User didn't respond | FAILED |
| CONNECTING | CONNECTION_TIMEOUT | 15s | Socket didn't establish | FAILED |

Timeouts are **cancelled** when the expected event arrives (e.g., call_accept).

### 4. Validation Guards

Each public method validates preconditions:

```kotlin
fun answerCall() {
    val call = currentCall ?: return
    // VALIDATION: Only allow answer from INCOMING_RINGING state
    if (call.state != CallState.INCOMING_RINGING) {
        onCallError?.invoke("Call is not in ringing state")
        return
    }
    // ... rest of logic
}
```

### 5. Socket State Drives Real Connection

Socket states in `SocketCallEngine`:

- `LISTENING`: Server socket listening (caller's side)
- `CONNECTING`: Client socket connecting (callee's side)
- `ACTIVE`: Both sides connected, audio flowing
- `ENDED`: Socket closed, cleanup needed
- `IDLE`: No active socket

Call state transitions to `ACTIVE` **ONLY WHEN** socket reports `ACTIVE`.

---

## Signaling Message Types

All messages go through `NetworkMessageEngine.sendControlSignal()`:

### call_offer
**Sent by**: Initiating peer when starting a call
**Payload**:
```json
{
    "type": "call_offer",
    "callId": "uuid",
    "callType": "VOICE" | "VIDEO",
    "callerIp": "192.168.1.100",
    "callPort": 7777,
    "from": "USER_A",
    "to": "USER_B",
    "ts": 1234567890
}
```
**Receiver action**: Launch CallActivity, call `onIncomingCall()`

### call_accept
**Sent by**: Receiving peer when accepting the call
**Payload**:
```json
{
    "type": "call_accept",
    "callId": "uuid",
    "callType": "VOICE" | "VIDEO",
    "from": "USER_B",
    "to": "USER_A",
    "ts": 1234567890
}
```
**Receiver action**: Call `onRemoteCallAccepted()`, start connecting

### call_reject
**Sent by**: Receiving peer when declining the call
**Payload**:
```json
{
    "type": "call_reject",
    "callId": "uuid",
    "reason": "DECLINED",
    "from": "USER_B",
    "to": "USER_A",
    "ts": 1234567890
}
```
**Receiver action**: Call `onRemoteCallRejected()`, end call as FAILED

### call_end
**Sent by**: Either peer to gracefully end the call
**Payload**:
```json
{
    "type": "call_end",
    "callId": "uuid",
    "reason": "NORMAL" | "FAILED" | "REMOTE_ENDED" | ...,
    "from": "USER_A",
    "to": "USER_B",
    "ts": 1234567890
}
```
**Receiver action**: Call `onRemoteCallEnded()`, end call

---

## Failure Scenarios and Recovery

### Scenario: User presses Answer but signal fails to send

```
1. User taps [Answer]
2. Call state: INCOMING_RINGING
3. answerCall() → sendControlSignal("call_accept") FAILS
4. Error callback: "Unable to accept call"
5. endCallInternal() called with reason="FAILED"
6. Call state: FAILED → ENDED
7. Socket is stopped (never started)
```

### Scenario: Signal sent but socket connection fails

```
1. answerCall() sends signal successfully
2. State: INCOMING_RINGING → CONNECTING
3. Timeout started: 15s CONNECTION_TIMEOUT
4. socketEngine.answerCall() fails (bad IP, network error)
5. onError callback from socket
6. handleSocketError() called
7. endCallInternal() called with reason="FAILED"
8. Call state: FAILED → ENDED
```

### Scenario: Connection established but remote closes

```
1. State: CONNECTING
2. Socket reaches ACTIVE
3. State: CONNECTING → ACTIVE
4. Remote calls endCall()
5. call_end signal received
6. onRemoteCallEnded() called
7. endCallInternal() called with reason="REMOTE_ENDED"
8. Call state: ENDED
9. Socket is stopped
```

### Scenario: Timeout during CONNECTING

```
1. State: CONNECTING
2. Timeout started: 15s
3. Socket doesn't establish (network hang)
4. Timeout fires
5. onCallError: "Connection timed out."
6. endCallInternal() called with reason="CONNECTION_TIMEOUT"
7. Call state: FAILED → ENDED
8. Socket is stopped
```

---

## Testing Checklist

### Outgoing Call - Success Path
- [ ] startCall() creates IDLE call
- [ ] Offer sent → state becomes OUTGOING_RINGING
- [ ] Timeout set: 45s NO_ANSWER
- [ ] Remote accepts → onRemoteCallAccepted() called
- [ ] State: OUTGOING_RINGING → CONNECTING
- [ ] Previous timeout cancelled, new timeout set: 15s CONNECTION_TIMEOUT
- [ ] Socket reaches ACTIVE
- [ ] State: CONNECTING → ACTIVE
- [ ] Timeout cancelled
- [ ] Call can be ended with endCall()
- [ ] call_end sent, state: ENDED
- [ ] Call logged in database

### Incoming Call - Success Path
- [ ] call_offer received
- [ ] IncomingCallLauncher launches CallActivity
- [ ] onIncomingCall() called, state: INCOMING_RINGING
- [ ] Timeout set: 45s NO_ANSWER
- [ ] User taps [Answer]
- [ ] answerCall() called
- [ ] call_accept sent → state becomes CONNECTING
- [ ] Previous timeout cancelled, new timeout set: 15s CONNECTION_TIMEOUT
- [ ] socketEngine.answerCall() connects
- [ ] Socket reaches ACTIVE
- [ ] State: CONNECTING → ACTIVE
- [ ] Timeout cancelled
- [ ] Call can be ended
- [ ] call_end sent, state: ENDED
- [ ] Call logged in database

### Outgoing Call - Rejection Path
- [ ] startCall() → OUTGOING_RINGING
- [ ] Remote rejects: call_reject received
- [ ] onRemoteCallRejected() called
- [ ] State: FAILED → ENDED
- [ ] Error callback: "Call declined by partner"
- [ ] Socket stopped, not connected

### Outgoing Call - Timeout Path
- [ ] startCall() → OUTGOING_RINGING
- [ ] Timeout: 45s passes
- [ ] onCallError: "No answer from partner."
- [ ] State: FAILED → ENDED
- [ ] Socket stopped, never connected

### Incoming Call - Decline Path
- [ ] onIncomingCall() → INCOMING_RINGING
- [ ] User taps [Decline]
- [ ] declineCall() called
- [ ] call_reject sent
- [ ] State: FAILED → ENDED
- [ ] Socket never created/started

### CONNECTING State - Timeout Path
- [ ] answerCall() → CONNECTING
- [ ] Timeout: 15s passes
- [ ] onCallError: "Connection timed out."
- [ ] State: FAILED → ENDED
- [ ] Socket stopped

### CONNECTING State - Remote Accepts Then Closes
- [ ] Caller: offer sent → OUTGOING_RINGING
- [ ] Remote accepts: call_accept received
- [ ] Caller: state CONNECTING
- [ ] Socket connects properly
- [ ] State: ACTIVE
- [ ] Remote sends call_end before connection
- [ ] onRemoteCallEnded() called
- [ ] State: ENDED
- [ ] No audio flows (call ended before actual connection)

---

## Backward Compatibility

All existing public API methods remain unchanged:

- `startCall(localUser, remoteUser, type)`
- `answerCall()`
- `endCall()`
- `declineCall()`
- `onIncomingCall(callId, fromUser, type, callerIp, localUser)`
- `onRemoteCallAccepted(callId)`
- `onRemoteCallRejected(callId, reason)`
- `onRemoteCallEnded(callId, reason)`
- `toggleMute()`
- `toggleSpeaker()`
- `startRecording(target)`
- `stopRecording()`
- `destroy()`

New optional features:
- `attachSignalHandler()` - Can be called to have CallEngine process incoming call_offer signals directly (alternative to IncomingCallLauncher)
- `onInboundCall` callback - Called when call_offer received (if attachSignalHandler used)

---

## Implementation Notes

### Thread Safety
- All state transitions happen on `callScope` (Dispatchers.IO)
- UI callbacks use `Dispatchers.Main`
- `isEnding` flag prevents re-entrance during cleanup

### Socket Callbacks
- Socket state changes are processed asynchronously via `handleSocketState()`
- Socket errors trigger `handleSocketError()`
- Both route through CallEngine's unified state machine

### Timeout Handling
- Timeouts are properly cancelled when expected events arrive
- If no expected event arrives, timeout fires and triggers end
- Helps prevent hung/stuck calls

### Signal Ordering
- All signals go through unified `NetworkMessageEngine.sendControlSignal()`
- No direct signal bypasses
- Messages are queued and delivered reliably (per NetworkMessageEngine)

### Recording
- Only active during ACTIVE state
- Stopped/flushed on call end
- Works with all failure scenarios (cleanup happens)

---

## Known Limitations

1. **Socket Timeouts**: Underlying `Socket()` constructor and read operations have OS-level timeouts. Doesn't add additional higher-level socket timeouts beyond the 15s CONNECTION_TIMEOUT in CONNECTING state.

2. **No Keep-Alive During ACTIVE**: If network becomes unavailable during ACTIVE state, the call may not detect it immediately. Only when socket read/write fails will it be detected.

3. **Single Active Call**: Only one call at a time (enforced by currentCall being singular).

4. **No Call Hold/Resume**: ON_HOLD state exists in enum but is not used. Full implementation would require signaling and state management for this.

5. **P2P Only**: Requires direct connectivity. If peers cannot reach each other's IP:port, call will timeout.

---

## Future Enhancements

1. Add call hold/resume signaling (PAUSE/RESUME messages)
2. Add ICE candidates exchange for NAT traversal
3. Add DTLS for encrypted audio
4. Add call state metrics/analytics
5. Add automatic reconnection on network change
6. Add multi-call support with call waiting/transfer
