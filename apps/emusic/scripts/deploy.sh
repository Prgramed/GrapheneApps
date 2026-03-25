#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "=== eMusic Deploy ==="

# Check for connected device
DEVICES=$(adb devices | grep -v "List" | grep -v "^$" | wc -l | tr -d ' ')
if [ "$DEVICES" -eq 0 ]; then
    echo "ERROR: No ADB device connected."
    echo "Connect your device and enable USB debugging."
    exit 1
fi

echo "Device connected."

# Build release APK
echo "Building release APK..."
cd "$PROJECT_ROOT"
./gradlew :apps:emusic:app:assembleRelease

APK_PATH="apps/emusic/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    # Try unsigned variant
    APK_PATH="apps/emusic/app/build/outputs/apk/release/app-release-unsigned.apk"
fi

if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at expected path."
    exit 1
fi

echo "Installing APK..."
adb install -r "$APK_PATH"

echo "=== Deploy complete ==="
