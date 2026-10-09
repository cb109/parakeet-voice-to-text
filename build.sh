#!/usr/bin/env bash
# Builds the debug APK (Gradle skips work that is already up to date).
set -euo pipefail
cd "$(dirname "$0")"

if [[ -z "${JAVA_HOME:-}" && -d /opt/android-studio/jbr ]]; then
    export JAVA_HOME=/opt/android-studio/jbr
fi
if [[ ! -f local.properties && -n "${ANDROID_HOME:-}" ]]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

./gradlew assembleDebug "$@"
echo "APK: $PWD/app/build/outputs/apk/debug/app-debug.apk"
