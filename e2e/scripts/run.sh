#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
scenario=${1:-all}
if [[ "$scenario" == all ]]; then
  flow=e2e/scenarios
elif [[ "$scenario" =~ ^(onboarding|onboarding_gpg_auth|entry_tap)$ ]]; then
  flow="e2e/scenarios/$scenario.yaml"
else
  echo "Unknown scenario: $scenario (choose all, onboarding, onboarding_gpg_auth, entry_tap)" >&2
  exit 1
fi
: "${MAESTRO_BIN:=$(command -v maestro || echo "$HOME/.maestro/bin/maestro")}"
: "${ANDROID_SERIAL:=$(adb get-serialno)}"
export MAESTRO_BIN ANDROID_SERIAL
export MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
mkdir -p build/e2e
bash e2e/scripts/prepare-device.sh
"$MAESTRO_BIN" test --device "$ANDROID_SERIAL" --format junit --output build/e2e/report.xml \
  --test-output-dir build/e2e/artifacts --debug-output build/e2e/debug "$flow"
