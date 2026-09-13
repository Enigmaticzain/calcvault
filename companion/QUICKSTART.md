# CalcVault Companion - Quick Start (5 Minutes)

## Step 1: Install (1 min)

```bash
cd companion
npm install
```

## Step 2: Start (30 sec)

```bash
npm start
```

You'll see:
```
[timestamp] [INFO] CalcVault companion started
Open secure local UI URL: http://127.0.0.1:43831/?token=abc123...
```

**Copy and open that URL in your browser.**

## Step 3: Connect Phone (2 min)

### Option A: USB (Recommended)

```bash
# Terminal 2: Connect phone via USB, then:
adb forward tcp:37111 tcp:37111
```

In companion UI:
1. Click **"Refresh Devices"**
2. Select your phone
3. Click **"Connect USB"**
4. Wait for "Connected" status

### Option B: WiFi

In companion UI:
1. Enter phone IP: `ws://192.168.1.XXX:37111/bridge`
2. Click **"Connect Network"**
3. Wait for "Connected" status

## Step 4: Pair (1 min)

1. Click **"Start Pairing"**
2. Pairing code appears (e.g., `ABC12345`)
3. On phone: Open CalcVault → Settings → Companion
4. Scan QR or enter code
5. Approve on phone
6. Status shows "Session Active" ✅

## Step 5: Backup (1 min)

1. Click **"Create Backup"**
2. Approve on phone
3. Wait for completion
4. Backup appears in table

**Done!** You now have a secure backup on your PC.

---

## Next: File Transfer

### Upload Files

1. Select files in "File Manager"
2. Click "Upload To Phone"
3. Approve on phone
4. Files encrypted and stored

### Download Files

1. Click "Refresh Metadata"
2. Select file
3. Click "Encrypted" or "Decrypted"
4. File downloads

---

## Troubleshooting

### "No USB devices found"
- Connect phone via USB
- Enable USB debugging: Settings → Developer Options → USB Debugging
- Run: `adb devices`

### "Connection failed"
- Check phone is connected
- Verify WiFi/USB working
- Check firewall

### "Pairing failed"
- Verify code matches on both devices
- Check phone time is correct
- Try again with new code

### "Backup failed"
- Check disk space: `df -h`
- Check phone has data to backup
- Try smaller backup first

---

## Security Notes

✅ **Local-only** - Runs on your machine, no cloud  
✅ **Encrypted** - All data encrypted end-to-end  
✅ **Verified** - Backups integrity checked  
✅ **Approved** - Phone approves all operations  

---

## What's Next?

- 📚 Read [DEPLOYMENT.md](./DEPLOYMENT.md) for advanced setup
- 🔒 Read [SECURITY_DETAILED.md](./docs/SECURITY_DETAILED.md) for security details
- 🧪 Read [TESTING.md](./TESTING.md) for testing
- 📋 Read [PROTOCOL_DETAILED.md](./docs/PROTOCOL_DETAILED.md) for protocol details

---

## Support

```bash
# View logs
LOG_LEVEL=debug npm start

# Check status
curl http://127.0.0.1:43831/health

# List backups
ls -la companion/data/backups/
```

**Enjoy secure backups! 🔐**
