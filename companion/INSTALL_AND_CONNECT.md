# CalcVault Companion - Installation & Connection Guide

## Step 1: Check Prerequisites

Before starting, verify you have:

### On Your PC:
- [ ] Node.js 18+ installed
- [ ] npm installed
- [ ] Terminal/Command Prompt access
- [ ] 1GB+ free disk space

### On Your Phone:
- [ ] CalcVault app installed
- [ ] USB debugging enabled (Settings → Developer Options → USB Debugging)
- [ ] USB cable (for USB mode) OR same WiFi network (for WiFi mode)

### Check Node.js Installation

Open Terminal/Command Prompt and run:

```bash
node --version
npm --version
```

You should see version numbers like:
```
v18.0.0 (or higher)
9.0.0 (or higher)
```

**If not installed:** Download from https://nodejs.org/

---

## Step 2: Navigate to Companion Directory

Open Terminal/Command Prompt and navigate to the companion folder:

```bash
cd /path/to/calcvault/companion
```

Or if you're on Windows:
```bash
cd C:\path\to\calcvault\companion
```

Verify you're in the right directory by checking for `package.json`:

```bash
ls package.json
# or on Windows:
dir package.json
```

You should see the file listed.

---

## Step 3: Install Dependencies

Run the installation command:

```bash
npm install
```

This will:
- Download all required packages
- Create a `node_modules` folder
- Generate `package-lock.json`

**Expected output:**
```
added 150 packages in 45s
```

**If you see errors:**
- Check internet connection
- Try: `npm cache clean --force`
- Then run `npm install` again

---

## Step 4: Start the Companion Server

Run the start command:

```bash
npm start
```

**Expected output:**
```
[2024-01-15T10:30:45.123Z] [INFO] CalcVault companion started
Open secure local UI URL: http://127.0.0.1:43831/?token=abc123def456xyz789...
```

**Important:** Copy the full URL (including the token) - you'll need it next.

---

## Step 5: Open the Web UI

1. Copy the printed URL from the terminal
2. Open your web browser (Chrome, Firefox, Safari, Edge)
3. Paste the URL into the address bar
4. Press Enter

**Expected:** You should see the CalcVault Companion dashboard with:
- Connection status: "Disconnected"
- Session status: "Session Inactive"
- Empty panels for connection, backup, and files

---

## Step 6: Connect via USB (Recommended)

### 6A: Enable USB Debugging on Phone

1. On your phone: Settings → About Phone
2. Tap "Build Number" 7 times (until you see "Developer Mode Enabled")
3. Go back to Settings → Developer Options
4. Enable "USB Debugging"
5. Connect phone to PC via USB cable
6. On phone: Tap "Allow" when prompted for USB debugging

### 6B: Setup ADB Forward

Open a **new Terminal/Command Prompt** (keep the companion running in the first one):

```bash
adb devices
```

You should see your phone listed:
```
List of attached devices
ABC123XYZ device
```

If you see "offline" or nothing, try:
```bash
adb kill-server
adb start-server
adb devices
```

### 6C: Forward the Port

```bash
adb forward tcp:37111 tcp:37111
```

Verify it worked:
```bash
adb forward --list
```

You should see:
```
ABC123XYZ tcp:37111 tcp:37111
```

### 6D: Connect in UI

In the web browser (Companion UI):

1. Click **"Refresh Devices"** button
2. Your phone should appear in the dropdown
3. Select your phone
4. Click **"Connect USB"**
5. Wait 2-5 seconds

**Expected:** Status changes to "Connected" ✅

---

## Step 7: Connect via WiFi (Alternative)

If USB doesn't work or you prefer WiFi:

### 7A: Find Phone IP Address

On your phone:
1. Settings → About Phone → Status
2. Look for "IP Address" (e.g., 192.168.1.100)

Or:
1. Settings → WiFi
2. Tap connected network
3. Look for "IP Address"

### 7B: Connect in UI

In the web browser (Companion UI):

1. In the "Local Network (Optional)" section
2. Enter: `ws://PHONE_IP:37111/bridge`
   - Replace PHONE_IP with your actual IP (e.g., `ws://192.168.1.100:37111/bridge`)
3. Click **"Connect Network"**
4. Wait 2-5 seconds

**Expected:** Status changes to "Connected" ✅

---

## Step 8: Start Pairing

Once connected (USB or WiFi):

1. Click **"Start Pairing"** button
2. A pairing code appears (e.g., `ABC12345`)
3. A QR code displays

**Keep this screen open on your PC.**

---

## Step 9: Approve Pairing on Phone

On your phone:

1. Open **CalcVault app**
2. Go to **Settings** → **Companion**
3. You should see a pairing screen
4. Either:
   - **Scan the QR code** from your PC screen, OR
   - **Enter the pairing code** manually (e.g., `ABC12345`)
5. Verify the code matches on both devices
6. Tap **"Approve"** or **"Pair"**

**Expected:** Phone shows "Pairing successful"

---

## Step 10: Verify Session is Active

Back on your PC in the web browser:

1. Check the status badges at the top
2. **Connection Badge** should show: "Connected" ✅
3. **Session Badge** should show: "Session Active" ✅
4. Dashboard should show:
   - Device ID
   - Device Name
   - Session Expiry time

**If you see these, you're successfully connected!** 🎉

---

## Step 11: Create Your First Backup

Now that you're connected and paired:

1. Click **"Create Backup"** button
2. On your phone: Approve the backup request
3. Wait for completion (progress shown in Session Log)
4. Backup appears in the "Backup Manager" table

**Expected:** Backup shows with:
- Backup ID
- Device ID
- Creation time
- Size
- SHA256 hash

---

## Troubleshooting

### "No USB devices found"

```bash
# Check if ADB sees your phone
adb devices

# If offline, restart ADB
adb kill-server
adb start-server
adb devices

# If still not working:
# 1. Unplug USB cable
# 2. Wait 5 seconds
# 3. Plug back in
# 4. On phone: Tap "Allow" for USB debugging
# 5. Try again
```

### "Connection failed" (USB)

```bash
# Verify forward is set up
adb forward --list

# If not there, set it up again
adb forward tcp:37111 tcp:37111

# Verify it worked
adb forward --list
```

### "Connection failed" (WiFi)

1. Verify phone IP is correct
2. Verify phone and PC are on same WiFi
3. Check phone bridge is running (CalcVault app open)
4. Try: `ws://192.168.1.100:37111/bridge` (replace with your IP)

### "Pairing failed"

1. Verify pairing code matches on both devices
2. Check phone time is correct
3. Try again with new code (click "Start Pairing" again)

### "Session Inactive"

1. Click "Start Pairing" again
2. Approve on phone
3. Session should become "Active"

### Companion won't start

```bash
# Check if port is in use
# Try different port:
CV_PORT=8080 npm start

# Or kill process on port 43831
# On Mac/Linux:
lsof -i :43831
kill -9 <PID>

# On Windows:
netstat -ano | findstr :43831
taskkill /PID <PID> /F
```

---

## Next Steps

Once connected and paired:

1. ✅ **Create Backup** - Click "Create Backup" button
2. ✅ **Upload Files** - Select files and click "Upload To Phone"
3. ✅ **Download Files** - Click "Refresh Metadata" then download
4. ✅ **Restore Backup** - Select backup and click "Restore"

---

## Keep Running

**Important:** Keep the companion running while using it.

To stop: Press `Ctrl+C` in the terminal

To restart: Run `npm start` again

---

## Debug Mode

If something isn't working, enable debug logging:

```bash
LOG_LEVEL=debug npm start
```

This shows detailed logs that help troubleshoot issues.

---

## Quick Reference

| Task | Command |
|------|---------|
| Start companion | `npm start` |
| Check Node.js | `node --version` |
| Check npm | `npm --version` |
| List USB devices | `adb devices` |
| Setup USB forward | `adb forward tcp:37111 tcp:37111` |
| Check forward | `adb forward --list` |
| Debug mode | `LOG_LEVEL=debug npm start` |
| Stop companion | `Ctrl+C` |

---

## Success Checklist

- [ ] Node.js 18+ installed
- [ ] npm installed
- [ ] Companion installed (`npm install`)
- [ ] Companion running (`npm start`)
- [ ] Web UI opens in browser
- [ ] Phone connected (USB or WiFi)
- [ ] Pairing started
- [ ] Pairing approved on phone
- [ ] Connection shows "Connected"
- [ ] Session shows "Active"
- [ ] First backup created

**Once all checked, you're ready to use CalcVault Companion!** 🎉

---

## Need Help?

1. Check [DEPLOYMENT.md](./DEPLOYMENT.md) for detailed setup
2. Check [QUICKSTART.md](./QUICKSTART.md) for quick reference
3. Enable debug logging: `LOG_LEVEL=debug npm start`
4. Check logs for error messages
5. Test with mock phone: `node tests/mock-phone.js`

---

**You're all set! Enjoy secure backups! 🔐**
