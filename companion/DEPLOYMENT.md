# CalcVault Companion - Deployment & Setup Guide

## Quick Start

### Prerequisites
- Node.js 18+
- npm
- ADB (Android Debug Bridge) for USB mode
- Phone and PC on same WiFi (for network mode)

### Installation

```bash
cd companion
npm install
```

### Run Companion

```bash
npm start
```

Output:
```
[timestamp] [INFO] CalcVault companion started
Open secure local UI URL: http://127.0.0.1:43831/?token=<launch-token>
```

Open the printed URL in your browser. **This is a one-time secure launch URL.**

---

## Connection Modes

### USB Mode (Recommended)

**Setup on PC:**
```bash
# 1. Connect phone via USB
# 2. Enable USB debugging on phone
# 3. Verify ADB sees device
adb devices

# 4. Forward phone bridge port to local port
adb forward tcp:37111 tcp:37111
```

**In Companion UI:**
1. Click "Refresh Devices"
2. Select your device from dropdown
3. Click "Connect USB"
4. Wait for connection status to show "Connected"

### Network Mode (Optional)

**Setup:**
1. Phone and PC must be on same WiFi
2. Find phone's local IP: Settings → About → IP Address
3. Phone must have bridge service running on port 37111

**In Companion UI:**
1. Enter: `ws://PHONE_IP:37111/bridge`
2. Click "Connect Network"

---

## Pairing Flow

### First Time Setup

1. **Connect** via USB or Network
2. **Click "Start Pairing"**
   - Pairing code appears (e.g., `ABC12345`)
   - QR code displays
3. **On Phone:**
   - Open CalcVault app
   - Go to Settings → Companion
   - Scan QR or enter pairing code
   - Approve pairing
4. **Session established** - Ready for operations

### Session Lifetime
- Default: 15 minutes
- Auto-extends on activity
- Expires on disconnect

---

## Operations

### Backup

1. **Create Backup:**
   - Click "Create Backup"
   - Approve on phone
   - Wait for completion
   - Backup stored in `companion/data/backups/`

2. **Backup Format:**
   - Encrypted end-to-end (AES-256-GCM)
   - Versioned with SHA256 integrity
   - Metadata stored in `.meta.json`

### Restore

1. **Select Backup:**
   - View in "Backup Manager" table
   - Click "Restore"
   - Approve on phone
   - Phone validates and restores

### File Transfer

**Upload (PC → Phone):**
1. Select files in "File Manager"
2. Click "Upload To Phone"
3. Approve on phone
4. Files encrypted and stored in vault

**Download (Phone → PC):**
1. Click "Refresh Metadata" to sync file list
2. Select download mode: Encrypted or Decrypted
3. File downloads to PC

---

## Configuration

### Environment Variables

```bash
# Port
CV_PORT=43831

# Bind address (127.0.0.1 = localhost only)
CV_BIND_HOST=127.0.0.1

# Session timeout (ms)
CV_SESSION_TTL_MS=900000

# Operation timeout (ms)
CV_OPERATION_TIMEOUT_MS=60000

# Max chunk size for transfers
CV_MAX_CHUNK_BYTES=262144

# Max upload size (5GB default)
CV_MAX_UPLOAD_BYTES=5368709120

# ADB path
CV_ADB_PATH=/usr/bin/adb

# Phone bridge port
CV_PHONE_BRIDGE_PORT=37111

# Enable network transport
CV_ENABLE_NETWORK_TRANSPORT=true

# Disable USB transport
CV_DISABLE_USB_TRANSPORT=false

# Log level
LOG_LEVEL=info
```

### Example: Custom Config

```bash
CV_PORT=8080 \
CV_BIND_HOST=0.0.0.0 \
CV_SESSION_TTL_MS=1800000 \
npm start
```

---

## Security Hardening

### 1. Local-Only Access (Default)

Companion binds to `127.0.0.1` by default. Only accessible from same machine.

```bash
# To allow network access (NOT recommended for untrusted networks):
CV_BIND_HOST=0.0.0.0 npm start
```

### 2. Session Management

- Sessions expire after 15 minutes of inactivity
- One-time launch token required
- API token per session
- HttpOnly cookies

### 3. Encryption

- **Pairing:** ECDH (X25519) + HMAC-SHA256
- **Session:** AES-256-GCM with directional nonces
- **Backups:** End-to-end encrypted, verified with SHA256
- **Files:** Encrypted in transit, decryption optional on download

### 4. Operation Authorization

All sensitive operations require phone approval:
- Backup create/restore
- File upload/download
- Device access

### 5. Firewall Rules

```bash
# Allow only local access
sudo ufw allow from 127.0.0.1 to 127.0.0.1 port 43831

# Or for network mode (same WiFi):
sudo ufw allow from 192.168.1.0/24 to any port 43831
```

---

## Troubleshooting

### USB Connection Issues

```bash
# Check ADB
adb devices

# Restart ADB daemon
adb kill-server
adb start-server

# Check forward
adb forward --list

# Manual forward
adb forward tcp:37111 tcp:37111
```

### Network Connection Issues

```bash
# Test connectivity
ping PHONE_IP

# Check phone bridge is running
# (Verify in CalcVault app settings)

# Test WebSocket
wscat -c ws://PHONE_IP:37111/bridge
```

### Session Expired

- Pairing code expires after 5 minutes
- Session expires after 15 minutes of inactivity
- Click "Start Pairing" again to refresh

### Backup Integrity Failed

- Ensure stable connection during backup
- Check disk space on PC
- Verify phone has sufficient data to backup

---

## Monitoring

### Logs

```bash
# View logs
tail -f companion/data/logs.txt

# Debug mode
LOG_LEVEL=debug npm start
```

### Status Endpoint

```bash
curl http://127.0.0.1:43831/health
```

Response:
```json
{
  "ok": true,
  "service": "calcvault-companion",
  "ts": 1234567890
}
```

---

## Backup Storage

Backups stored in: `companion/data/backups/DEVICE_ID/`

Structure:
```
backups/
├── device-id-1/
│   ├── device-id-1_2024-01-15T10-30-45-123_v1.cvb
│   └── device-id-1_2024-01-15T10-30-45-123_v1.cvb.meta.json
└── device-id-2/
    └── ...
```

### Backup Metadata

```json
{
  "backupId": "device-id_2024-01-15T10-30-45-123_v1",
  "deviceId": "device-id",
  "createdAt": 1705318245123,
  "sha256": "abc123...",
  "backupVersion": 1,
  "size": 1048576,
  "encrypted": true
}
```

---

## Performance Tips

1. **Large Backups:** Use USB mode (faster than network)
2. **File Transfer:** Increase `CV_MAX_CHUNK_BYTES` for faster transfers
3. **Network:** Use 5GHz WiFi for better throughput
4. **Disk:** Ensure sufficient free space for backups

---

## Uninstall

```bash
# Stop companion
Ctrl+C

# Remove data
rm -rf companion/data/

# Remove node_modules
rm -rf companion/node_modules/
```

---

## Support

- Check logs: `LOG_LEVEL=debug npm start`
- Verify connection: Use "Refresh Devices" button
- Test pairing: "Start Pairing" and check phone
- Validate backup: Check `.meta.json` SHA256

---

## Next Steps

1. ✅ Install and run companion
2. ✅ Connect phone via USB or network
3. ✅ Complete pairing
4. ✅ Create first backup
5. ✅ Test file transfer
6. ✅ Configure backups schedule (optional)
