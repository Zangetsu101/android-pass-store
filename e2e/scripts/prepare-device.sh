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
"$MAESTRO_BIN" test --device "$ANDROID_SERIAL" --test-output-dir build/e2e/find-sensor \
  --debug-output build/e2e/find-sensor-debug e2e/shared/find_fingerprint_sensor.yaml
# Settings uses a separate enrollment session for sensor discovery. Send exactly
# one touch there, then wait for the actual enrollment screen before repeating.
timeout 10s adb emu finger touch 1
timeout 10s adb emu finger remove
"$MAESTRO_BIN" test --device "$ANDROID_SERIAL" --test-output-dir build/e2e/await-enrollment \
  --debug-output build/e2e/await-enrollment-debug e2e/shared/await_fingerprint_enrollment.yaml
# Let an in-flight console command finish before removing its working files.
# Killing just the loop shell would leave that command running after cleanup.
input_dir=$(mktemp -d build/e2e/fingerprint-input.XXXXXX)
(
  for ((i=0; i<180; i++)); do
    sleep 1
    [[ ! -e "$input_dir/stop" ]] || break
    timeout 10s adb emu finger touch 1
    timeout 10s adb emu finger remove
  done
) > build/e2e/fingerprint-console.log 2>&1 &
finger_pid=$!
stop_fingerprint_input() {
  touch "$input_dir/stop"
  wait "$finger_pid" || true
  rm -r "$input_dir"
}
trap stop_fingerprint_input EXIT
"$MAESTRO_BIN" test --device "$ANDROID_SERIAL" --test-output-dir build/e2e/enrollment \
  --debug-output build/e2e/enrollment-debug e2e/shared/enroll_fingerprint.yaml
count=$(fingerprint_count)
if (( count == 0 )); then
  echo "Settings completed without an enrolled fingerprint." >&2
  exit 1
fi
