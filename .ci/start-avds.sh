#!/usr/bin/env bash
set -euo pipefail

AVD_NAME="api${ANDROID_API}"

echo "Starting emulator: $AVD_NAME"
/opt/android/emulator/emulator @"$AVD_NAME" \
  -no-window -no-audio -gpu off -no-snapshot -no-boot-anim -read-only \
  2>&1 &

echo "Waiting for emulator …"
adb wait-for-device
echo "Emulator ready!"
