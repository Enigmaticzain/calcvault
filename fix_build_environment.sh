#!/usr/bin/env bash
# Fix permissions, SDK pointer, JDK 17, and clean rebuild for CalcVault.
# Run on Linux/Kali/Debian in the PROJECT ROOT (same folder as gradlew).
#
# Termux on Android: full Android SDK + Gradle build is NOT practical in Termux.
#   Use this script on your PC, or: termux-open / ssh to a machine that has JDK+SDK.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

SDK_DEFAULT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
JAVA17_CANDIDATES=(
  "/usr/lib/jvm/java-17-openjdk-amd64"
  "/usr/lib/jvm/java-17-openjdk"
  "/usr/lib/jvm/temurin-17-jdk"
)

echo "==> Project: $PROJECT_ROOT"

echo "==> Fix write permissions on project (fixes mergeDebugShaders delete errors)"
chmod -R u+w "$PROJECT_ROOT" 2>/dev/null || true
chown -R "$(whoami):$(whoami)" "$PROJECT_ROOT" 2>/dev/null || true

echo "==> Stop Gradle daemons"
./gradlew --stop 2>/dev/null || true

echo "==> Remove build caches"
rm -rf "$PROJECT_ROOT/app/build" "$PROJECT_ROOT/build" "$PROJECT_ROOT/.gradle"

LOCAL_PROPS="$PROJECT_ROOT/local.properties"
if [[ ! -f "$LOCAL_PROPS" ]] || ! grep -q '^sdk.dir=' "$LOCAL_PROPS" 2>/dev/null; then
  echo "==> Write sdk.dir to local.properties"
  echo "sdk.dir=$SDK_DEFAULT" > "$LOCAL_PROPS"
  chmod 644 "$LOCAL_PROPS" 2>/dev/null || true
else
  echo "==> local.properties already has sdk.dir"
fi

export ANDROID_SDK_ROOT="$SDK_DEFAULT"
export ANDROID_HOME="$SDK_DEFAULT"

JAVA17=""
for d in "${JAVA17_CANDIDATES[@]}"; do
  if [[ -d "$d" && -x "$d/bin/java" ]]; then
    JAVA17="$d"
    break
  fi
done

if [[ -n "$JAVA17" ]]; then
  export JAVA_HOME="$JAVA17"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "==> Using JAVA_HOME=$JAVA_HOME"
  java -version
else
  echo "WARN: JDK 17 not found in common paths. Install openjdk-17, then re-run."
  echo "  Debian/Kali: sudo apt update && sudo apt install -y openjdk-17-jdk"
  java -version || true
fi

echo "==> Build debug APK"
./gradlew clean assembleDebug

echo "OK. APK under app/build/outputs/apk/debug/"
