# CalcVault Companion - Desktop/Web Bridge

**Local-first secure backup, restore, and file transfer for CalcVault Android app.**

Connect your phone to your PC via USB or WiFi, securely backup your vault, and transfer files—all encrypted, all local, all under your control.

## ⚡ Quick Start

```bash
cd companion
npm install
npm start
```

Open the printed URL in your browser. Connect your phone. Done.

**→ [5-Minute Quick Start Guide](./QUICKSTART.md)**

## ✨ Features

- 🔐 **End-to-end encrypted** - AES-256-GCM for all data
- 📱 **USB + WiFi** - Connect via USB (recommended) or local network
- ✅ **Phone-authorized** - All operations require phone approval
- 💾 **Versioned backups** - Multiple backups with integrity verification
- 📁 **File transfer** - Upload/download with encryption options
- 🔒 **Secure pairing** - QR code or 8-character code
- ⏱️ **Session management** - Auto-expiring sessions, device-bound
- 🎯 **Local-first** - No cloud, no third parties, just you

## 📋 System Requirements

- **PC:** Node.js 18+, npm
- **Phone:** CalcVault Android app with bridge support
- **Connection:** USB cable (recommended) or same WiFi network
- **ADB:** For USB mode (Android Debug Bridge)

## 🚀 Installation

### 1. Install Dependencies

```bash
cd companion
npm install
```

### 2. Start Companion

```bash
npm start
```

Output:
```
[2024-01-15T10:30:45.123Z] [INFO] CalcVault companion started
Open secure local UI URL: http://127.0.0.1:43831/?token=abc123def456...
```

### 3. Open in Browser

Copy and open the printed URL. This is a **one-time secure launch URL**.

## 🔌 Connection Modes

### USB (Recommended)

```bash
# Terminal 1: Start companion
npm start

# Terminal 2: Setup ADB forward
adb forward tcp:37111 tcp:37111

# In UI: Click "Refresh Devices" → Select phone → "Connect USB"
```

**Advantages:** Faster, more stable, no WiFi needed

### WiFi (Optional)

```bash
# In UI: Enter ws://PHONE_IP:37111/bridge → "Connect Network"
```

**Advantages:** No USB cable needed, convenient

## 🔐 Pairing

1. Click **"Start Pairing"** in UI
2. Pairing code appears (e.g., `ABC12345`)
3. On phone: Open CalcVault → Settings → Companion
4. Scan QR or enter code
5. Approve on phone
6. Session established ✅

## 💾 Backup & Restore

### Create Backup

```
1. Click "Create Backup"
2. Approve on phone
3. Wait for completion
4. Backup stored in companion/data/backups/
```

### Restore Backup

```
1. Select backup in table
2. Click "Restore"
3. Approve on phone
4. Phone validates and restores
```

**Security:** Backups are encrypted end-to-end with SHA256 integrity verification.

## 📁 File Transfer

### Upload (PC → Phone)

```
1. Select files in "File Manager"
2. Click "Upload To Phone"
3. Approve on phone
4. Files encrypted and stored in vault
```

### Download (Phone → PC)

```
1. Click "Refresh Metadata"
2. Select file
3. Choose "Encrypted" or "Decrypted"
4. File downloads
```

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| [QUICKSTART.md](./QUICKSTART.md) | 5-minute setup guide |
| [DEPLOYMENT.md](./DEPLOYMENT.md) | Full setup, configuration, troubleshooting |
| [TESTING.md](./TESTING.md) | Testing, validation, mock phone |
| [docs/PROTOCOL_DETAILED.md](./docs/PROTOCOL_DETAILED.md) | PC-Phone communication protocol |
| [docs/SECURITY_DETAILED.md](./docs/SECURITY_DETAILED.md) | Threat model, cryptography, security controls |

## 🏗️ Architecture

```
PC Companion                    Phone Bridge
┌─────────────────────┐        ┌──────────────────┐
│  Web UI (React)     │        │  CalcVault App   │
│  - Dashboard        │        │  - Settings      │
│  - Backup Manager   │        │  - Pairing UI    │
│  - File Manager     │        │  - Approval UI   │
└──────────┬──────────┘        └────────┬─────────┘
           │                           │
           └───────────────────────────┘
                  WebSocket
              (AES-256-GCM)
                   │
        ┌──────────┴──────────┐
        │                     │
    USB (ADB)            WiFi (LAN)
    Forward              Direct
```

## 🔒 Security

### Encryption
- **Pairing:** ECDH (X25519) + HMAC-SHA256
- **Session:** AES-256-GCM with directional nonces
- **Backups:** End-to-end encrypted, SHA256 verified
- **Files:** Encrypted in transit, optional decryption

### Authentication
- Pairing code (8 alphanumeric) shown on both devices
- QR code for visual verification
- Device fingerprint binding
- Session tokens (random 24 bytes)

### Authorization
- All sensitive operations require phone approval
- User can review and deny requests
- Timeout prevents hanging requests
- Session binding prevents cross-device attacks

### Protection
- Replay protection with sequence numbers
- Directional nonces prevent bidirectional replay
- Integrity verification for backups
- Atomic writes prevent partial files
- Local-only access by default (127.0.0.1)

**→ [Full Security Documentation](./docs/SECURITY_DETAILED.md)**

## ⚙️ Configuration

### Environment Variables

```bash
CV_PORT=43831                          # HTTP port
CV_BIND_HOST=127.0.0.1                 # Bind address (local-only)
CV_SESSION_TTL_MS=900000               # Session timeout (15 min)
CV_OPERATION_TIMEOUT_MS=60000          # Operation timeout (60 sec)
CV_MAX_CHUNK_BYTES=262144              # Transfer chunk size (256 KB)
CV_MAX_UPLOAD_BYTES=5368709120         # Max upload (5 GB)
CV_ADB_PATH=/usr/bin/adb               # ADB path
CV_PHONE_BRIDGE_PORT=37111             # Phone bridge port
CV_ENABLE_NETWORK_TRANSPORT=true       # Allow WiFi mode
CV_DISABLE_USB_TRANSPORT=false         # Disable USB mode
LOG_LEVEL=info                         # Log level (debug, info, warn, error)
```

### Example: Custom Port

```bash
CV_PORT=8080 npm start
```

## 🧪 Testing

### Run Validation Suite

```bash
npm run validate
```

Tests:
- ✅ Connection (USB, Network)
- ✅ Pairing (valid, timeout, mismatch)
- ✅ Backup (create, restore, integrity)
- ✅ File transfer (upload, download)
- ✅ Session management
- ✅ Encryption/decryption
- ✅ Error handling

### Mock Phone Simulator

```bash
node tests/mock-phone.js
```

Then connect to `ws://127.0.0.1:37112/bridge` in UI.

## 📊 File Structure

```
companion/
├── src/
│   ├── main.js                 # App bootstrap
│   ├── config.js               # Configuration
│   ├── api/
│   │   └── http-server.js      # HTTP API + WebSocket
│   ├── services/
│   │   ├── bridge-manager.js   # Session + operations
│   │   └── backup-store.js     # Backup persistence
│   ├── security/
│   │   ├── crypto.js           # Encryption primitives
│   │   └── pairing-manager.js  # Pairing flow
│   ├── transport/
│   │   ├── usb.js              # USB (ADB) transport
│   │   └── network.js          # WiFi transport
│   └── util/
│       ├── logger.js           # Logging
│       ├── fs.js               # File utilities
│       ├── exec.js             # Command execution
│       ├── net.js              # Network utilities
│       └── deferred.js         # Promise helpers
├── web/
│   ├── index.html              # UI
│   ├── app.js                  # UI logic
│   └── styles.css              # Styling
├── tests/
│   ├── mock-phone.js           # Mock phone simulator
│   └── run-validation.js       # Validation harness
├── data/
│   └── backups/                # Backup storage
├── docs/
│   ├── PROTOCOL_DETAILED.md    # Protocol spec
│   ├── SECURITY_DETAILED.md    # Security architecture
│   └── ANDROID_BRIDGE_INTEGRATION.md
├── QUICKSTART.md               # 5-minute guide
├── DEPLOYMENT.md               # Full setup guide
├── TESTING.md                  # Testing guide
├── README.md                   # This file
├── package.json
└── .gitignore
```

## 🐛 Troubleshooting

### USB Connection Issues

```bash
# Check ADB
adb devices

# Restart ADB
adb kill-server
adb start-server

# Manual forward
adb forward tcp:37111 tcp:37111
```

### Network Connection Issues

```bash
# Test connectivity
ping PHONE_IP

# Check phone bridge running
# (Verify in CalcVault app)

# Test WebSocket
wscat -c ws://PHONE_IP:37111/bridge
```

### Session Expired

- Pairing code expires after 5 minutes
- Session expires after 15 minutes of inactivity
- Click "Start Pairing" again to refresh

### Backup Integrity Failed

- Ensure stable connection
- Check disk space: `df -h`
- Verify phone has data to backup

**→ [Full Troubleshooting Guide](./DEPLOYMENT.md#troubleshooting)**

## 📈 Performance

| Operation | Time | Notes |
|-----------|------|-------|
| Connect | 2-5s | USB faster than WiFi |
| Pairing | 5-10s | User approval required |
| Backup 100MB | 10-30s | Depends on connection |
| Restore 100MB | 10-30s | Depends on connection |
| Upload 100MB | 15-45s | Depends on connection |
| Download 100MB | 15-45s | Depends on connection |

## 🔄 Backup Storage

Backups stored in: `companion/data/backups/DEVICE_ID/`

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

## 🛡️ Security Best Practices

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

## 📝 Logs

```bash
# View logs
tail -f companion/data/logs.txt

# Debug mode
LOG_LEVEL=debug npm start

# Check status
curl http://127.0.0.1:43831/health
```

## 🚀 Next Steps

1. ✅ [Quick Start](./QUICKSTART.md) - Get running in 5 minutes
2. ✅ [Deployment Guide](./DEPLOYMENT.md) - Full setup and configuration
3. ✅ [Testing Guide](./TESTING.md) - Validate your setup
4. ✅ [Security Details](./docs/SECURITY_DETAILED.md) - Understand the security model
5. ✅ [Protocol Spec](./docs/PROTOCOL_DETAILED.md) - Deep dive into communication

## 📄 License

CalcVault Companion is part of the CalcVault project.

## 🤝 Support

For issues or questions:

1. Check [DEPLOYMENT.md](./DEPLOYMENT.md) troubleshooting section
2. Enable debug logging: `LOG_LEVEL=debug npm start`
3. Review logs for error messages
4. Test with mock phone: `node tests/mock-phone.js`

---

**Ready to secure your backups? [Get started now →](./QUICKSTART.md)**
