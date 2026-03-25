#!/bin/bash
set -e

echo "Building eWeather..."
./gradlew :apps:eweather:app:assembleDebug

DEVICE=$(adb devices | grep -w device | head -1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "No device connected"
    exit 1
fi

echo "Installing on $DEVICE..."
adb -s "$DEVICE" install -r apps/eweather/app/build/outputs/apk/debug/app-debug.apk
echo "Done."
