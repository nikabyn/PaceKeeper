#!/usr/bin/env bash
set -e

EMULATOR_PORT="${EMULATOR_PORT:-5554}"
SERIAL="emulator-${EMULATOR_PORT}"

echo "--- Stopping Android Emulator ($SERIAL) ---"

if adb devices | grep -q "$SERIAL"; then
    adb -s "$SERIAL" emu kill || true
    sleep 3
fi

pkill -f "emulator.*-port ${EMULATOR_PORT}" || true

echo "Emulator stopped."