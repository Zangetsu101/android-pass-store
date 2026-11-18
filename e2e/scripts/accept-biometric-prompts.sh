#!/usr/bin/env bash
# Maestro cannot touch the fingerprint sensor. This loop touches it for the
# emulator when the app's biometric prompt stays open for PROMPT_DELAY seconds.
# The delay lets a scenario assert the prompt before the touch.
# The loop stops when the file STOP_FILE exists.
set -uo pipefail
stop_file=$1
: "${PROMPT_DELAY:=5}"
app=com.zangetsu101.pass
open_for=0
until [[ -e "$stop_file" ]]; do
  sleep 1
  if adb shell dumpsys window | grep -q "mCurrentFocus=.*BiometricPrompt" &&
    adb shell dumpsys fingerprint | grep -q "Current operation: .*AuthenticationClient.*owner=$app"; then
    open_for=$((open_for + 1))
  else
    open_for=0
  fi
  if (( open_for >= PROMPT_DELAY )); then
    echo "$(date +%T) touch fingerprint"
    timeout 10s adb emu finger touch 1
    timeout 10s adb emu finger remove
    open_for=0
  fi
done
