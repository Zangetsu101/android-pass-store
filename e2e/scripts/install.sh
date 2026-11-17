#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_SERIAL:=$(adb get-serialno)}"
export ANDROID_SERIAL
adb get-state
mkdir -p build/e2e/fixtures
revision=3912051da6e6d4553a990ca63900c3ef2adc06cb
for key in test-key.asc test-key-auth.asc; do
  curl --fail --location --retry 3 "https://raw.githubusercontent.com/Zangetsu101/pass-test-store/$revision/$key" -o "build/e2e/fixtures/$key"
  adb push "build/e2e/fixtures/$key" "/sdcard/Download/$key"
done
adb install -r app/build/outputs/apk/debug/app-debug.apk
