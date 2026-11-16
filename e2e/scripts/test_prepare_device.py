"""Exercise credential-preservation and enrollment branches without changing a device."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

ADB = r'''#!/usr/bin/env python3
import os, pathlib, sys, time
args = sys.argv[1:]
root = pathlib.Path(os.environ['FAKE_DEVICE'])
with (root / 'calls').open('a') as log:
    log.write(' '.join(args) + '\n')
mode = os.environ['FAKE_MODE']
if mode == 'delayed-console' and args == ['emu', 'finger', 'touch', '1'] and (root / 'actual-enrollment').exists():
    (root / 'console-started').touch()
    time.sleep(0.2)
    with (root / 'calls').open('a') as log:
        log.write('console-finished\n')

if args == ['shell', 'am', 'get-current-user']:
    print(0)
elif args == ['shell', 'dumpsys', 'fingerprint']:
    if mode == 'unknown':
        print('Unsupported diagnostics')
    else:
        count = int(mode == 'enrolled' or (root / 'enrolled').exists())
        print('Diagnostic text\n{"prints":[{"id":0,"count":%d},{"id":10,"count":3}]}' % count)
elif args[:3] == ['shell', 'getprop', 'ro.kernel.qemu']:
    print(1)
elif args[:3] == ['shell', 'locksettings', 'verify']:
    sys.exit(0 if (mode in ('fresh', 'enrollment-fails', 'no-enrollment', 'delayed-console') and '--old' not in args) or (mode == 'existing-pin' and '--old' in args) else 1)
'''

MAESTRO = r'''#!/usr/bin/env python3
import os, pathlib, sys, time
root = pathlib.Path(os.environ['FAKE_DEVICE'])
with (root / 'calls').open('a') as log:
    log.write('maestro\n')
if sys.argv[-1].endswith('find_fingerprint_sensor.yaml'):
    sys.exit(0)
if sys.argv[-1].endswith('await_fingerprint_enrollment.yaml'):
    (root / 'actual-enrollment').touch()
    sys.exit(0)
if os.environ['FAKE_MODE'] == 'delayed-console':
    deadline = time.monotonic() + 5
    while not (root / 'console-started').exists():
        if time.monotonic() > deadline:
            sys.exit('Console input never started')
        time.sleep(0.01)
if os.environ['FAKE_MODE'] == 'enrollment-fails':
    sys.exit(1)
if os.environ['FAKE_MODE'] != 'no-enrollment':
    (root / 'enrolled').touch()
'''


class DeviceSetupTest(unittest.TestCase):
    def run_setup(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, source in [('adb', ADB), ('maestro', MAESTRO), ('sleep', '#!/bin/sh\nexit 0\n')]:
                path = root / name
                path.write_text(source)
                path.chmod(0o755)
            script = root / 'workspace/e2e/scripts/prepare-device.sh'
            script.parent.mkdir(parents=True)
            script.write_text((ROOT / 'e2e/scripts/prepare-device.sh').read_text())
            env = dict(os.environ, PATH=f'{root}:{os.environ["PATH"]}',
                       FAKE_DEVICE=directory, FAKE_MODE=mode,
                       MAESTRO_BIN=str(root / 'maestro'), ANDROID_SERIAL='emulator-test')
            result = subprocess.run(['bash', str(script)],
                                    env=env, capture_output=True, text=True)
            return result, (root / 'calls').read_text()

    def test_enrolled_device_keeps_credentials(self):
        result, calls = self.run_setup('enrolled')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('locksettings', calls)
        self.assertNotIn('maestro', calls)

    def test_other_users_fingerprints_do_not_skip_setup(self):
        result, calls = self.run_setup('fresh')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('locksettings set-pin --user 0 1234', calls)
        self.assertIn('maestro', calls)

    def test_existing_unknown_credential_is_preserved(self):
        result, calls = self.run_setup('locked')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('set-pin', calls)
        self.assertNotIn('maestro', calls)

    def test_existing_test_pin_is_reused(self):
        result, calls = self.run_setup('existing-pin')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('set-pin', calls)
        self.assertIn('maestro', calls)

    def test_enrollment_failure_propagates(self):
        result, calls = self.run_setup('enrollment-fails')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('maestro', calls)

    def test_settings_must_actually_enroll_a_fingerprint(self):
        result, calls = self.run_setup('no-enrollment')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Settings completed without an enrolled fingerprint', result.stderr)

    def test_console_command_finishes_before_setup_returns(self):
        result, calls = self.run_setup('delayed-console')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('console-finished', calls)

    def test_sensor_discovery_gets_one_touch_before_enrollment(self):
        result, calls = self.run_setup('fresh')
        self.assertEqual(result.returncode, 0, result.stderr)
        stages = calls.split('maestro\n')
        self.assertEqual(len(stages), 4, 'Discovery, readiness, and completion must run separately')
        self.assertEqual(stages[0].count('emu finger touch 1'), 0)
        self.assertEqual(stages[1].count('emu finger touch 1'), 1)
        self.assertIn('emu finger remove', stages[1])

    def test_unknown_diagnostics_fail_without_modifying_device(self):
        result, calls = self.run_setup('unknown')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('locksettings', calls)
        self.assertNotIn('maestro', calls)


if __name__ == '__main__':
    unittest.main()
