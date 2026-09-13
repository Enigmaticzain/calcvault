#!/bin/bash
# CalcVault — One-paste Linux build script
# Paste this entire block into terminal and press Enter

set -e

echo "=== CalcVault Build Script ==="
echo "This will take about 10 minutes on first run."
echo ""

# ── 1. Install Java if missing ─────────────────────────────────────────────
if ! command -v java &> /dev/null; then
    echo "[1/7] Installing Java 17..."
    sudo apt-get update -q
    sudo apt-get install -y openjdk-17-jdk
else
    echo "[1/7] Java found: $(java -version 2>&1 | head -1)"
fi

# ── 2. Install Android SDK command line tools ──────────────────────────────
ANDROID_HOME="$HOME/android-sdk"
if [ ! -d "$ANDROID_HOME" ]; then
    echo "[2/7] Downloading Android SDK tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    cd /tmp
    wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O cmdtools.zip
    unzip -q cmdtools.zip
    mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
    rm cmdtools.zip
    echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.bashrc
    echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools" >> ~/.bashrc
else
    echo "[2/7] Android SDK found at $ANDROID_HOME"
fi

export ANDROID_HOME="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# ── 3. Accept licenses + install SDK ──────────────────────────────────────
echo "[3/7] Installing Android SDK 34 and build tools..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /dev/null 2>&1
echo "    SDK installed."

# ── 4. Extract project ────────────────────────────────────────────────────
echo "[4/7] Setting up project..."
cd "$HOME"
if [ ! -d "calcvault" ]; then
    if [ -f "$HOME/Downloads/calcvault_v6_final.zip" ]; then
        unzip -q "$HOME/Downloads/calcvault_v6_final.zip"
    elif [ -f "$HOME/calcvault_v6_final.zip" ]; then
        unzip -q "$HOME/calcvault_v6_final.zip"
    else
        echo ""
        echo "ERROR: calcvault_v6_final.zip not found."
        echo "Please copy it to your home folder (~/) or ~/Downloads/"
        echo "Then run this script again."
        exit 1
    fi
fi
cd "$HOME/calcvault"

# ── 5. Make Gradle wrapper executable ─────────────────────────────────────
chmod +x gradlew

# ── 6. Patch build.gradle — skip WebRTC, enable simple socket audio ────────
echo "[5/7] Configuring build for socket-based audio calls (no WebRTC needed)..."

# Comment out WebRTC dependency (not needed — we use socket audio)
sed -i 's|.*libwebrtc.*|    // WebRTC replaced by socket audio — no .aar needed|' app/build.gradle

# Make sure compileSdk and targetSdk are correct
sed -i 's/compileSdk [0-9]*/compileSdk 34/' app/build.gradle
sed -i 's/targetSdk [0-9]*/targetSdk 34/' app/build.gradle

echo "    Build config patched."

# ── 7. Build debug APK ────────────────────────────────────────────────────
echo "[6/7] Building APK (this takes 3-5 minutes)..."
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
./gradlew assembleDebug --no-daemon -q 2>&1 | tail -5

APK_PATH=$(find . -name "*.apk" | head -1)

if [ -z "$APK_PATH" ]; then
    echo ""
    echo "Build failed. Running again with full output to show errors:"
    ./gradlew assembleDebug --no-daemon 2>&1 | tail -30
    exit 1
fi

echo ""
echo "========================================="
echo "  BUILD SUCCESSFUL"
echo "========================================="
echo ""
echo "  APK: $HOME/calcvault/$APK_PATH"
echo ""
echo "[7/7] To install on your phone:"
echo ""
echo "  1. Connect phone via USB"
echo "  2. Enable USB Debugging on phone"
echo "  3. Run:"
echo ""
echo "     adb install $HOME/calcvault/$APK_PATH"
echo ""
echo "  OR copy the APK to your phone and open it manually."
echo ""
echo "  APK location for manual copy:"
cp "$APK_PATH" "$HOME/CalcVault.apk"
echo "     ~/CalcVault.apk"
echo ""
echo "  First unlock: type 98765 then press = in the calculator"
echo "========================================="
