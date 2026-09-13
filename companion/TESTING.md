# CalcVault Companion - Testing & Validation Guide

## Quick Validation

```bash
cd companion
npm run validate
```

This runs end-to-end tests with a mock phone simulator.

---

## Test Scenarios

### 1. Connection Tests

#### USB Connection
```bash
# Terminal 1: Start companion
npm start

# Terminal 2: Setup ADB forward
adb forward tcp:37111 tcp:37111

# Terminal 3: Connect in UI
# Click "Refresh Devices" → Select device → "Connect USB"
# Expected: Status shows "Connected"
```

#### Network Connection
```bash
# Terminal 1: Start companion
npm start

# Terminal 2: Get phone IP
adb shell ip addr show wlan0 | grep "inet "

# Terminal 3: Connect in UI
# Enter: ws://PHONE_IP:37111/bridge
# Click "Connect Network"
# Expected: Status shows "Connected"
```

### 2. Pairing Tests

#### Valid Pairing
```
1. Connect (USB or Network)
2. Click "Start Pairing"
3. Pairing code appears (e.g., ABC12345)
4. On phone: Settings → Companion → Scan QR or enter code
5. Approve on phone
6. Expected: Session shows "Active", device info displays
```

#### Pairing Timeout
```
1. Click "Start Pairing"
2. Wait 5+ minutes without approving
3. Try to create backup
4. Expected: Error "pairing_expired"
```

#### Pairing Mismatch
```
1. Click "Start Pairing" on PC
2. On phone: Enter wrong code
3. Expected: Error "pairing_proof_invalid"
```

### 3. Backup Tests

#### Create Backup
```
1. Ensure session is active
2. Click "Create Backup"
3. Approve on phone
4. Wait for completion
5. Expected: Backup appears in table with SHA256 hash
```

#### Backup Integrity
```
1. Create backup
2. Check companion/data/backups/DEVICE_ID/
3. Verify .meta.json exists with sha256 field
4. Expected: SHA256 matches backup file hash
```

#### Restore Backup
```
1. Create backup first
2. Click "Restore" on backup row
3. Approve on phone
4. Expected: Phone restores data, no errors
```

#### Large Backup
```
1. Add 500MB+ data to phone vault
2. Create backup
3. Monitor progress in session log
4. Expected: Completes without timeout or corruption
```

### 4. File Transfer Tests

#### Upload Single File
```
1. Select file in "File Manager"
2. Click "Upload To Phone"
3. Approve on phone
4. Expected: File appears in phone vault
```

#### Upload Multiple Files
```
1. Select 5+ files
2. Click "Upload To Phone"
3. Expected: All files upload successfully
```

#### Upload Large File
```
1. Select 1GB+ file
2. Click "Upload To Phone"
3. Monitor progress
4. Expected: Completes without timeout
```

#### Download Encrypted
```
1. Click "Refresh Metadata"
2. Select file → "Encrypted" button
3. Expected: File downloads as encrypted binary
```

#### Download Decrypted
```
1. Click "Refresh Metadata"
2. Select file → "Decrypted" button
3. Expected: File downloads as plaintext/original format
```

### 5. Session Tests

#### Session Expiry
```
1. Establish session
2. Wait 15+ minutes without activity
3. Try to create backup
4. Expected: Error "session_expired"
```

#### Session Auto-Extend
```
1. Establish session
2. Create backup at 14 minutes
3. Expected: Session extends, backup succeeds
```

#### Disconnect and Reconnect
```
1. Establish session
2. Click "Disconnect"
3. Click "Connect USB" again
4. Click "Start Pairing"
5. Expected: New session established
```

### 6. Error Handling Tests

#### Network Interruption
```
1. Start backup
2. Disconnect WiFi/USB mid-transfer
3. Expected: Graceful error, can reconnect
```

#### Invalid Pairing Code
```
1. Start pairing
2. On phone: Enter wrong code
3. Expected: Error "pairing_proof_invalid"
```

#### Corrupted Backup
```
1. Create backup
2. Manually corrupt .cvb file
3. Try to restore
4. Expected: Error "backup_hash_mismatch"
```

#### Missing Device
```
1. Connect to device
2. Unplug USB
3. Try to create backup
4. Expected: Error "not_connected"
```

---

## Mock Phone Simulator

### Run Mock Phone

```bash
node tests/mock-phone.js
```

This simulates a phone bridge on `ws://127.0.0.1:37112/bridge`.

### Connect to Mock Phone

```bash
# In companion UI:
# Enter: ws://127.0.0.1:37112/bridge
# Click "Connect Network"
```

### Mock Phone Features

- Responds to pairing handshake
- Simulates backup creation
- Simulates file operations
- Generates realistic delays
- Supports operation approval/denial

### Mock Phone Configuration

```bash
# Custom port
MOCK_PHONE_PORT=37113 node tests/mock-phone.js

# Simulate slow connection
MOCK_PHONE_DELAY=2000 node tests/mock-phone.js

# Simulate failures
MOCK_PHONE_FAIL_RATE=0.1 node tests/mock-phone.js
```

---

## Automated Tests

### Run All Tests

```bash
npm run validate
```

### Test Coverage

- ✅ Connection (USB, Network)
- ✅ Pairing (valid, timeout, mismatch)
- ✅ Backup (create, restore, integrity)
- ✅ File transfer (upload, download)
- ✅ Session management
- ✅ Error handling
- ✅ Encryption/decryption
- ✅ Timeout handling

### Test Output

```
CalcVault Companion - Validation Suite
======================================

[✓] Connection: USB forward
[✓] Connection: Network WebSocket
[✓] Pairing: Valid handshake
[✓] Pairing: Proof verification
[✓] Backup: Create and store
[✓] Backup: Restore with integrity
[✓] Files: Upload stream
[✓] Files: Download stream
[✓] Session: Expiry and auto-extend
[✓] Encryption: AES-256-GCM
[✓] Errors: Graceful handling

Results: 11/11 passed
Duration: 2.3s
```

---

## Performance Testing

### Backup Performance

```bash
# Create 100MB backup
time npm run validate -- --scenario backup-100mb

# Expected: < 30 seconds
```

### File Transfer Performance

```bash
# Upload 500MB file
time npm run validate -- --scenario upload-500mb

# Expected: < 60 seconds (depends on connection)
```

### Concurrent Operations

```bash
# Run 5 simultaneous uploads
npm run validate -- --scenario concurrent-uploads

# Expected: All complete without errors
```

---

## Security Testing

### Encryption Verification

```bash
# Verify AES-256-GCM
npm run validate -- --test encryption

# Expected: All encryption tests pass
```

### Replay Attack Prevention

```bash
# Verify sequence number checking
npm run validate -- --test replay-protection

# Expected: Replay attempts rejected
```

### Pairing Proof Verification

```bash
# Verify HMAC-SHA256 proof
npm run validate -- --test pairing-proof

# Expected: Invalid proofs rejected
```

---

## Manual Testing Checklist

### Pre-Release

- [ ] USB connection works
- [ ] Network connection works
- [ ] Pairing completes successfully
- [ ] Backup creates and restores
- [ ] Files upload and download
- [ ] Session expires correctly
- [ ] Errors handled gracefully
- [ ] Large files transfer correctly
- [ ] Encryption verified
- [ ] UI responsive and clear

### Post-Deployment

- [ ] Monitor logs for errors
- [ ] Test with real phone
- [ ] Verify backup integrity
- [ ] Check file permissions
- [ ] Monitor disk usage
- [ ] Test network stability
- [ ] Verify session management

---

## Debugging

### Enable Debug Logging

```bash
LOG_LEVEL=debug npm start
```

### Monitor WebSocket Traffic

```bash
# Terminal 1: Start companion
npm start

# Terminal 2: Monitor with wscat
wscat -l 37111

# Terminal 3: Connect from UI
# Watch WebSocket messages in Terminal 2
```

### Inspect Backups

```bash
# List backups
ls -la companion/data/backups/*/

# View metadata
cat companion/data/backups/DEVICE_ID/*.meta.json | jq

# Verify SHA256
sha256sum companion/data/backups/DEVICE_ID/*.cvb
```

### Check Session State

```bash
# View current status
curl http://127.0.0.1:43831/api/status \
  -H "x-calcvault-ui-token: TOKEN"
```

---

## Troubleshooting Test Failures

### Connection Fails

```bash
# Check ADB
adb devices

# Check port forwarding
adb forward --list

# Verify phone bridge is running
adb shell ps | grep calcvault
```

### Pairing Fails

```bash
# Check pairing code matches
# Verify phone time is synchronized
# Check network connectivity
```

### Backup Fails

```bash
# Check disk space
df -h companion/data/

# Check file permissions
ls -la companion/data/backups/

# Monitor logs
tail -f companion/data/logs.txt
```

### File Transfer Fails

```bash
# Check file size limits
# Verify network bandwidth
# Monitor memory usage
```

---

## Continuous Integration

### GitHub Actions Example

```yaml
name: Companion Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-node@v2
        with:
          node-version: '18'
      - run: cd companion && npm install
      - run: npm run validate
```

---

## Test Reports

### Generate Report

```bash
npm run validate -- --report html
```

Output: `companion/test-report.html`

### View Report

```bash
open companion/test-report.html
```

---

## Known Issues & Workarounds

### Issue: ADB not found
**Workaround:** Set `CV_ADB_PATH=/path/to/adb`

### Issue: Port already in use
**Workaround:** Set `CV_PORT=8080` or kill process on port 43831

### Issue: Session expires during large backup
**Workaround:** Increase `CV_SESSION_TTL_MS=1800000` (30 min)

### Issue: WebSocket connection timeout
**Workaround:** Check firewall, ensure phone bridge is running

---

## Next Steps

1. ✅ Run `npm run validate`
2. ✅ Test with mock phone
3. ✅ Test with real phone (USB)
4. ✅ Test with real phone (Network)
5. ✅ Verify backups and restores
6. ✅ Test file transfers
7. ✅ Monitor performance
8. ✅ Deploy to production

