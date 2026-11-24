#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
scenario=${1:-all}
if [[ "$scenario" == all ]]; then
  flow=e2e/scenarios
elif [[ "$scenario" =~ ^(onboarding|onboarding_gpg_auth|entry_tap|otp|edit_login|new_login|generate_login|note_fields)$ ]]; then
  flow="e2e/scenarios/$scenario.yaml"
else
  echo "Unknown scenario: $scenario (choose all, onboarding, onboarding_gpg_auth, entry_tap, otp, edit_login, new_login, generate_login, note_fields)" >&2
  exit 1
fi
: "${MAESTRO_BIN:=$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}"
: "${ANDROID_SERIAL:=$(adb get-serialno)}"
export MAESTRO_BIN ANDROID_SERIAL
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
mkdir -p build/e2e
bash e2e/scripts/prepare-device.sh
stop_file=build/e2e/biometric-helper.stop
rm -f "$stop_file"
bash e2e/scripts/accept-biometric-prompts.sh "$stop_file" > build/e2e/biometric-helper.log 2>&1 &
helper_pid=$!
stop_helper() {
  touch "$stop_file"
  wait "$helper_pid" || true
}
trap stop_helper EXIT
"$MAESTRO_BIN" test --device "$ANDROID_SERIAL" --format junit --output build/e2e/report.xml \
  --test-output-dir build/e2e/artifacts --debug-output build/e2e/debug "$flow"
