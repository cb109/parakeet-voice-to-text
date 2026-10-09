#!/usr/bin/env bash
# Builds if needed, installs on the USB-connected phone and launches the app.
set -euo pipefail
cd "$(dirname "$0")"

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "$devices" ]]; then
    echo "No phone found. Connect via USB, enable USB debugging and accept the prompt on the phone." >&2
    adb devices -l >&2
    exit 1
fi
if [[ $(wc -l <<< "$devices") -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
    echo "Several devices connected; pick one with ANDROID_SERIAL=<serial> $0" >&2
    echo "$devices" >&2
    exit 1
fi

./build.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.parakeet/.MainActivity
