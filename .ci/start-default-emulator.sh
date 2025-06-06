#!/usr/bin/env bash
set -euo pipefail

echo "Starting default emulator"
$ANDROID_HOME/emulator/emulator @default -no-window -no-audio 2>&1 &

TIMEOUT_SEC=1200
INTERVAL_SEC=5
START=$(date +%s)

while true; do
    if [[ $(adb shell getprop init.svc.bootanim 2>/dev/null) == *"stopped"* ]]; then
        echo "Emulator booted"
        break
    fi

    NOW=$(date +%s)
    if (( NOW - START >= TIMEOUT_SEC )); then
        echo "ERROR: Emulator not ready after $((TIMEOUT_SEC/60)) min" >&2
        pkill -f "@default" || true
        exit 99
    fi

    echo "Waiting for emulator to boot..."
    sleep "$INTERVAL_SEC"
done