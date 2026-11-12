#!/usr/bin/env bash
# Safe CalcVault Android debug build. Does not install packages, rewrite Gradle files,
# modify local.properties, or copy artifacts outside the repository.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

command -v java >/dev/null 2>&1 || fail "Java 17 is required. Install a JDK 17 and retry."
command -v javac >/dev/null 2>&1 || fail "A full JDK is required (javac was not found)."
JAVA_VERSION="$(java -version 2>&1 | sed -n '1p')"
if ! java -version 2>&1 | grep -Eq 'version "(17)(\.|")'; then
  fail "JDK 17 is required; found: ${JAVA_VERSION}"
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" && -f "$PROJECT_ROOT/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$PROJECT_ROOT/local.properties" | head -n 1)"
fi
[[ -n "$SDK_DIR" && -d "$SDK_DIR" ]] || fail "Android SDK not found. Set ANDROID_SDK_ROOT or configure sdk.dir in local.properties."
[[ -d "$SDK_DIR/platforms/android-34" ]] || fail "Android SDK platform 34 is required at $SDK_DIR/platforms/android-34."
[[ -d "$SDK_DIR/build-tools/34.0.0" ]] || fail "Android build-tools 34.0.0 are required at $SDK_DIR/build-tools/34.0.0."
[[ -x "$PROJECT_ROOT/gradlew" ]] || fail "Gradle wrapper is missing or not executable: $PROJECT_ROOT/gradlew"

export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"
printf 'Using %s\nAndroid SDK: %s\n' "$JAVA_VERSION" "$SDK_DIR"
printf 'Running Android unit tests and debug APK build...\n'
./gradlew clean testDebugUnitTest assembleDebug

APK="$PROJECT_ROOT/app/build/outputs/apk/debug/Calculator-debug-1.1.apk"
[[ -s "$APK" ]] || fail "Gradle completed without producing the expected APK: $APK"
printf '\nAndroid debug APK built: %s\n' "$APK"
