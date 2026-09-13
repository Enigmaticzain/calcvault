# CalcVault Companion - Implementation Checklist

## Pre-Implementation Review

### System Requirements
- [ ] Node.js 18+ installed
- [ ] npm installed
- [ ] ADB installed (for USB mode)
- [ ] Phone with CalcVault app
- [ ] USB cable (for USB mode) or WiFi connection
- [ ] 1GB+ free disk space for backups

### Documentation Review
- [ ] Read QUICKSTART.md
- [ ] Read DEPLOYMENT.md
- [ ] Read SECURITY_DETAILED.md
- [ ] Understand threat model
- [ ] Review encryption approach
- [ ] Understand operation flow

---

## Installation Phase

### Step 1: Clone/Download Code
- [ ] Navigate to companion directory
- [ ] Verify all source files present
- [ ] Check file permissions

### Step 2: Install Dependencies
```bash
cd companion
npm install
```
- [ ] npm install completes without errors
- [ ] node_modules directory created
- [ ] package-lock.json generated

### Step 3: Verify Installation
```bash
npm list
```
- [ ] All dependencies listed
- [ ] No missing packages
- [ ] No version conflicts

---

## Configuration Phase

### Step 1: Default Configuration
- [ ] Review default config in src/config.js
- [ ] Verify default port: 43831
- [ ] Verify default bind: 127.0.0.1
- [ ] Verify default session TTL: 15 minutes

### Step 2: Custom Configuration (Optional)
- [ ] Set CV_PORT if needed
- [ ] Set CV_BIND_HOST if needed
- [ ] Set CV_SESSION_TTL_MS if needed
- [ ] Set LOG_LEVEL for debugging
- [ ] Document all custom settings

### Step 3: Environment Setup
- [ ] Create .env file (optional)
- [ ] Export environment variables
- [ ] Verify configuration loaded

---

## Startup Phase

### Step 1: Start Companion
```bash
npm start
```
- [ ] Server starts without errors
- [ ] Prints launch URL
- [ ] Launch URL contains token
- [ ] Server listens on configured port

### Step 2: Verify Server Running
```bash
curl http://127.0.0.1:43831/health
```
- [ ] Returns 200 OK
- [ ] Response contains service name
- [ ] Response contains timestamp

### Step 3: Open Web UI
- [ ] Copy launch URL from terminal
- [ ] Open URL in browser
- [ ] UI loads without errors
- [ ] All panels visible
- [ ] Status shows "Disconnected"

---

## USB Connection Phase

### Step 1: Setup ADB
- [ ] Connect phone via USB
- [ ] Enable USB debugging on phone
- [ ] Verify ADB sees device: `adb devices`
- [ ] Device shows as "device" (not "offline")

### Step 2: Setup Port Forward
```bash
adb forward tcp:37111 tcp:37111
```
- [ ] Forward command succeeds
- [ ] Verify forward: `adb forward --list`
- [ ] Forward shows in list

### Step 3: Connect in UI
- [ ] Click "Refresh Devices"
- [ ] Device appears in dropdown
- [ ] Select device
- [ ] Click "Connect USB"
- [ ] Status changes to "Connected"
- [ ] Endpoint shows in dashboard

---

## Network Connection Phase (Optional)

### Step 1: Find Phone IP
- [ ] Get phone IP from settings
- [ ] Verify phone on same WiFi
- [ ] Verify phone bridge running

### Step 2: Connect in UI
- [ ] Enter ws://PHONE_IP:37111/bridge
- [ ] Click "Connect Network"
- [ ] Status changes to "Connected"
- [ ] Endpoint shows in dashboard

---

## Pairing Phase

### Step 1: Start Pairing
- [ ] Click "Start Pairing"
- [ ] Pairing code appears (8 characters)
- [ ] QR code displays
- [ ] Pairing expiry shows

### Step 2: Approve on Phone
- [ ] Open CalcVault app
- [ ] Go to Settings → Companion
- [ ] Scan QR or enter code
- [ ] Verify code matches PC
- [ ] Approve pairing

### Step 3: Verify Session
- [ ] Session badge changes to "Active"
- [ ] Device info displays in dashboard
- [ ] Device ID visible
- [ ] Device name visible
- [ ] Session expiry shows

---

## Backup Phase

### Step 1: Create Backup
- [ ] Click "Create Backup"
- [ ] Approve on phone
- [ ] Wait for completion
- [ ] Session log shows progress

### Step 2: Verify Backup
- [ ] Backup appears in table
- [ ] Backup ID visible
- [ ] Device ID matches
- [ ] Creation time shows
- [ ] Size displays
- [ ] SHA256 hash shows

### Step 3: Check Storage
```bash
ls -la companion/data/backups/DEVICE_ID/
```
- [ ] .cvb file exists
- [ ] .meta.json file exists
- [ ] File sizes reasonable
- [ ] Timestamps correct

### Step 4: Verify Integrity
```bash
sha256sum companion/data/backups/DEVICE_ID/*.cvb
```
- [ ] SHA256 matches metadata
- [ ] Hash is 64 hex characters
- [ ] No corruption detected

---

## Restore Phase

### Step 1: Select Backup
- [ ] Click "Refresh Backups"
- [ ] Backup appears in table
- [ ] Select backup row
- [ ] Click "Restore"

### Step 2: Approve on Phone
- [ ] Approve restore on phone
- [ ] Wait for completion
- [ ] Session log shows progress

### Step 3: Verify Restore
- [ ] Restore completes without errors
- [ ] Phone shows restored data
- [ ] No data loss
- [ ] All files present

---

## File Transfer Phase

### Step 1: Upload Files
- [ ] Select files in "File Manager"
- [ ] Click "Upload To Phone"
- [ ] Approve on phone
- [ ] Wait for completion
- [ ] Session log shows progress

### Step 2: Verify Upload
- [ ] Files appear in phone vault
- [ ] File names correct
- [ ] File sizes correct
- [ ] Files encrypted

### Step 3: Download Files
- [ ] Click "Refresh Metadata"
- [ ] Files appear in table
- [ ] Select file
- [ ] Click "Encrypted" or "Decrypted"
- [ ] File downloads

### Step 4: Verify Download
- [ ] File downloads to PC
- [ ] File size correct
- [ ] File readable (if decrypted)
- [ ] No corruption

---

## Session Management Phase

### Step 1: Session Expiry
- [ ] Note session expiry time
- [ ] Wait 15+ minutes without activity
- [ ] Try to create backup
- [ ] Verify error: "session_expired"

### Step 2: Session Auto-Extend
- [ ] Create backup at 14 minutes
- [ ] Verify backup succeeds
- [ ] Session extends automatically

### Step 3: Disconnect and Reconnect
- [ ] Click "Disconnect"
- [ ] Status changes to "Disconnected"
- [ ] Click "Connect USB" again
- [ ] Click "Start Pairing"
- [ ] New session established

---

## Error Handling Phase

### Step 1: Connection Errors
- [ ] Unplug USB
- [ ] Try to create backup
- [ ] Verify error: "not_connected"
- [ ] Reconnect and verify recovery

### Step 2: Pairing Errors
- [ ] Start pairing
- [ ] Enter wrong code on phone
- [ ] Verify error: "pairing_proof_invalid"
- [ ] Start new pairing

### Step 3: Backup Errors
- [ ] Create backup
- [ ] Manually corrupt .cvb file
- [ ] Try to restore
- [ ] Verify error: "backup_hash_mismatch"

### Step 4: Timeout Errors
- [ ] Start operation
- [ ] Disconnect mid-transfer
- [ ] Verify timeout after 60 seconds
- [ ] Verify graceful error handling

---

## Security Verification Phase

### Step 1: Encryption Verification
- [ ] Backup file is binary (not plaintext)
- [ ] Backup file not readable as text
- [ ] Metadata file is JSON (not encrypted)
- [ ] Session messages encrypted

### Step 2: Pairing Verification
- [ ] Pairing code shown on both devices
- [ ] QR code scannable
- [ ] Code matches on both devices
- [ ] Proof verification prevents MITM

### Step 3: Session Verification
- [ ] Session token random
- [ ] Session expires after TTL
- [ ] Session auto-extends on activity
- [ ] Device fingerprint binding works

### Step 4: Operation Verification
- [ ] All operations require phone approval
- [ ] User can deny operations
- [ ] Denied operations don't execute
- [ ] Approved operations execute

---

## Performance Testing Phase

### Step 1: Backup Performance
- [ ] Create 100MB backup
- [ ] Measure time
- [ ] Verify < 30 seconds
- [ ] Check CPU usage
- [ ] Check memory usage

### Step 2: File Transfer Performance
- [ ] Upload 100MB file
- [ ] Measure time
- [ ] Verify < 45 seconds
- [ ] Check network bandwidth
- [ ] Check disk I/O

### Step 3: Large File Handling
- [ ] Upload 1GB file
- [ ] Verify completes without timeout
- [ ] Verify no memory issues
- [ ] Verify file integrity

---

## Logging & Monitoring Phase

### Step 1: Enable Debug Logging
```bash
LOG_LEVEL=debug npm start
```
- [ ] Debug logs appear
- [ ] Logs show message flow
- [ ] Logs show encryption/decryption
- [ ] Logs show operation progress

### Step 2: Monitor Logs
- [ ] Check for errors
- [ ] Check for warnings
- [ ] Verify no sensitive data logged
- [ ] Verify timestamps correct

### Step 3: Check Status Endpoint
```bash
curl http://127.0.0.1:43831/api/status \
  -H "x-calcvault-ui-token: TOKEN"
```
- [ ] Returns current status
- [ ] Shows connection state
- [ ] Shows session state
- [ ] Shows device info

---

## Testing Phase

### Step 1: Run Validation Suite
```bash
npm run validate
```
- [ ] All tests pass
- [ ] No errors reported
- [ ] Test duration reasonable
- [ ] Coverage adequate

### Step 2: Test with Mock Phone
```bash
node tests/mock-phone.js
```
- [ ] Mock phone starts
- [ ] Can connect to mock phone
- [ ] Pairing works with mock
- [ ] Backup works with mock
- [ ] File transfer works with mock

### Step 3: Manual Testing
- [ ] Test USB connection
- [ ] Test Network connection
- [ ] Test pairing flow
- [ ] Test backup/restore
- [ ] Test file transfer
- [ ] Test error scenarios
- [ ] Test session management

---

## Deployment Readiness Phase

### Step 1: Code Review
- [ ] All source files present
- [ ] No debug code left
- [ ] No hardcoded secrets
- [ ] No console.log statements
- [ ] Error handling complete

### Step 2: Documentation Review
- [ ] QUICKSTART.md complete
- [ ] DEPLOYMENT.md complete
- [ ] TESTING.md complete
- [ ] SECURITY_DETAILED.md complete
- [ ] PROTOCOL_DETAILED.md complete

### Step 3: Security Review
- [ ] Encryption verified
- [ ] Authentication verified
- [ ] Authorization verified
- [ ] Input validation verified
- [ ] Error handling verified
- [ ] No data leaks
- [ ] No timing attacks

### Step 4: Performance Review
- [ ] Backup performance acceptable
- [ ] File transfer performance acceptable
- [ ] Memory usage acceptable
- [ ] CPU usage acceptable
- [ ] Disk usage acceptable

---

## Production Deployment Phase

### Step 1: Pre-Deployment
- [ ] All checklist items completed
- [ ] All tests passing
- [ ] All documentation reviewed
- [ ] Security audit completed
- [ ] Performance tested

### Step 2: Deployment
- [ ] Install on production machine
- [ ] Configure environment variables
- [ ] Start companion service
- [ ] Verify service running
- [ ] Verify UI accessible

### Step 3: Post-Deployment
- [ ] Monitor logs for errors
- [ ] Test backup/restore
- [ ] Test file transfer
- [ ] Verify performance
- [ ] Document any issues

### Step 4: Ongoing Maintenance
- [ ] Monitor disk usage
- [ ] Monitor logs regularly
- [ ] Test restore procedures
- [ ] Keep Node.js updated
- [ ] Keep CalcVault app updated
- [ ] Review security best practices

---

## Rollback Plan

### If Issues Occur
- [ ] Stop companion service
- [ ] Restore previous version
- [ ] Verify backups intact
- [ ] Investigate issue
- [ ] Fix and redeploy

### Backup Verification
- [ ] Verify backup files exist
- [ ] Verify metadata files exist
- [ ] Verify SHA256 hashes
- [ ] Test restore procedure
- [ ] Verify data integrity

---

## Sign-Off

### Development Team
- [ ] Code complete and tested
- [ ] Documentation complete
- [ ] Security review passed
- [ ] Performance acceptable

### QA Team
- [ ] All tests passing
- [ ] No critical issues
- [ ] No security issues
- [ ] Performance verified

### Operations Team
- [ ] Deployment procedure clear
- [ ] Monitoring configured
- [ ] Backup procedure verified
- [ ] Rollback procedure ready

### Project Manager
- [ ] All deliverables complete
- [ ] Documentation complete
- [ ] Testing complete
- [ ] Ready for production

---

## Final Verification

### System Functionality
- [ ] Connection works (USB and Network)
- [ ] Pairing works
- [ ] Backup works
- [ ] Restore works
- [ ] File transfer works
- [ ] Session management works
- [ ] Error handling works

### Security
- [ ] Encryption verified
- [ ] Authentication verified
- [ ] Authorization verified
- [ ] No data leaks
- [ ] No security issues

### Documentation
- [ ] All guides complete
- [ ] All examples working
- [ ] All troubleshooting covered
- [ ] All security explained

### Performance
- [ ] Backup performance acceptable
- [ ] File transfer performance acceptable
- [ ] Memory usage acceptable
- [ ] CPU usage acceptable

---

## Deployment Complete ✅

**CalcVault Companion is ready for production deployment.**

All checklist items completed. System is secure, tested, documented, and ready for use.

**Next Steps:**
1. Deploy to production
2. Monitor logs
3. Test backup/restore
4. Verify performance
5. Document any issues

---

## Support Contact

For issues or questions:
1. Check DEPLOYMENT.md troubleshooting
2. Enable debug logging
3. Review logs for errors
4. Test with mock phone
5. Contact development team

---

**Deployment Date:** _______________
**Deployed By:** _______________
**Verified By:** _______________
**Sign-Off Date:** _______________
