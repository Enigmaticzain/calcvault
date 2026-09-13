# 🚀 CalcVault Companion - Installation in 10 Steps

## ✅ Prerequisites Check

Before you start, make sure you have:

**On PC:**
- [ ] Node.js 18+ (download from https://nodejs.org/)
- [ ] USB cable (for USB mode) OR WiFi connection
- [ ] Terminal/Command Prompt

**On Phone:**
- [ ] CalcVault app installed
- [ ] USB debugging enabled
- [ ] Connected to same WiFi (for WiFi mode)

---

## 📋 Step-by-Step Installation

### STEP 1️⃣: Open Terminal

**Windows:**
- Press `Win + R`
- Type `cmd`
- Press Enter

**Mac/Linux:**
- Open Terminal app

---

### STEP 2️⃣: Navigate to Companion Folder

Copy and paste this command:

```bash
cd /path/to/calcvault/companion
```

**Replace `/path/to/` with your actual path.**

Example:
- Windows: `cd C:\Users\YourName\Downloads\calcvault\companion`
- Mac: `cd /Users/YourName/Downloads/calcvault/companion`
- Linux: `cd /home/username/Downloads/calcvault/companion`

Press Enter.

---

### STEP 3️⃣: Verify Node.js Installation

```bash
node --version
npm --version
```

**Expected output:**
```
v18.0.0
9.0.0
```

If you see errors, install Node.js from https://nodejs.org/

---

### STEP 4️⃣: Install Dependencies

```bash
npm install
```

**This will take 1-2 minutes.**

**Expected output:**
```
added 150 packages in 45s
```

---

### STEP 5️⃣: Start the Companion

```bash
npm start
```

**Expected output:**
```
[2024-01-15T10:30:45.123Z] [INFO] CalcVault companion started
Open secure local UI URL: http://127.0.0.1:43831/?token=abc123def456xyz789...
```

**⚠️ IMPORTANT:** Copy the full URL (including the token)

---

### STEP 6️⃣: Open Web Browser

1. Open your browser (Chrome, Firefox, Safari, Edge)
2. Paste the URL from Step 5
3. Press Enter

**Expected:** You see the CalcVault Companion dashboard

---

### STEP 7️⃣: Connect Phone (Choose One)

#### Option A: USB Connection (Recommended)

**On Phone:**
1. Settings → Developer Options → Enable "USB Debugging"
2. Connect phone to PC with USB cable
3. Tap "Allow" when prompted

**On PC (New Terminal):**
```bash
adb devices
```

You should see your phone listed.

**Then:**
```bash
adb forward tcp:37111 tcp:37111
```

**In Web Browser:**
1. Click "Refresh Devices"
2. Select your phone
3. Click "Connect USB"
4. Wait for "Connected" status ✅

#### Option B: WiFi Connection

**On Phone:**
1. Settings → About Phone → Status
2. Note the IP Address (e.g., 192.168.1.100)

**In Web Browser:**
1. Enter: `ws://192.168.1.100:37111/bridge` (use your IP)
2. Click "Connect Network"
3. Wait for "Connected" status ✅

---

### STEP 8️⃣: Start Pairing

**In Web Browser:**
1. Click "Start Pairing"
2. Pairing code appears (e.g., `ABC12345`)
3. QR code displays

**Keep this screen open.**

---

### STEP 9️⃣: Approve on Phone

**On Phone:**
1. Open CalcVault app
2. Settings → Companion
3. Scan QR code OR enter pairing code
4. Verify code matches
5. Tap "Approve"

---

### STEP 🔟: Verify Connection

**In Web Browser:**
Check the top badges:
- ✅ Connection: "Connected"
- ✅ Session: "Session Active"

**You're connected!** 🎉

---

## 🎯 First Backup

Now create your first backup:

1. Click "Create Backup"
2. Approve on phone
3. Wait for completion
4. Backup appears in table

---

## 🐛 Quick Troubleshooting

### "No USB devices found"
```bash
adb kill-server
adb start-server
adb devices
```

### "Connection failed"
```bash
adb forward tcp:37111 tcp:37111
adb forward --list
```

### "Pairing failed"
- Verify code matches on both devices
- Try again with new code

### "Session Inactive"
- Click "Start Pairing" again
- Approve on phone

---

## 📊 Status Indicators

| Status | Meaning | Action |
|--------|---------|--------|
| Disconnected | No connection | Click "Connect USB" or "Connect Network" |
| Connected | Connected to phone | Click "Start Pairing" |
| Session Inactive | Not paired | Click "Start Pairing" |
| Session Active | Ready to use | Create backup or transfer files |

---

## ✨ You're Done!

Once you see:
- ✅ Connection: "Connected"
- ✅ Session: "Session Active"

You can:
- 💾 Create backups
- 📁 Transfer files
- ↩️ Restore backups

---

## 📞 Need Help?

1. Check [INSTALL_AND_CONNECT.md](./INSTALL_AND_CONNECT.md) for detailed steps
2. Enable debug: `LOG_LEVEL=debug npm start`
3. Check logs for errors
4. Test with mock phone: `node tests/mock-phone.js`

---

**Congratulations! CalcVault Companion is installed and connected! 🔐**
