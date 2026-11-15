#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${MAESTRO_BIN:=$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}"
: "${ANDROID_SERIAL:=$(adb get-serialno)}"
export ANDROID_SERIAL
mkdir -p build/e2e
user_id=$(adb shell am get-current-user | tr -d '\r')

fingerprint_count() {
  adb shell dumpsys fingerprint > build/e2e/fingerprint-state.txt
  # Android's dump mixes JSON provider records with diagnostic text.
  jq -Rsc --argjson user "$user_id" '
    [split("\n")[] | fromjson? | .prints[]? | select(.id == $user) | .count]
    | if length == 0 then error("No fingerprint enrollment data for current user") else add end
  ' build/e2e/fingerprint-state.txt
}

count=$(fingerprint_count)
if (( count > 0 )); then
  echo "Fingerprint already enrolled; leaving device credentials unchanged."
  exit 0
fi
if [[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') != 1 ]]; then
  echo "Automatic fingerprint enrollment requires an emulator." >&2
  exit 1
fi
if adb shell locksettings verify --old 1234 --user "$user_id" > build/e2e/lock-check.log 2>&1; then
  :
elif adb shell locksettings verify --user "$user_id" >> build/e2e/lock-check.log 2>&1; then
  adb shell locksettings set-pin --user "$user_id" 1234
else
  echo "Enroll a fingerprint manually, or use a test emulator with PIN 1234. Existing credentials were preserved." >&2
  exit 1
fi
adb shell am start -a android.settings.FINGERPRINT_ENROLL
# The emulator console has no enrollment-ready event. Bound the sensor input loop
# and stop it as soon as the Settings flow finishes, including on failure.
(
  for ((i=0; i<180; i++)); do
    sleep 1
    adb emu finger touch 1
    adb emu finger remove
  done
) > build/e2e/fingerprint-console.log 2>&1 &
finger_pid=$!
trap 'kill "$finger_pid" 2>/dev/null || true; wait "$finger_pid" 2>/dev/null || true' EXIT
"$MAESTRO_BIN" test --device "$ANDROID_SERIAL" --test-output-dir build/e2e/enrollment \
  --debug-output build/e2e/enrollment-debug e2e/shared/enroll_fingerprint.yaml
count=$(fingerprint_count)
if (( count == 0 )); then
  echo "Settings completed without an enrolled fingerprint." >&2
  exit 1
fi
