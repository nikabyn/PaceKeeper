#!/usr/bin/env bash
set -euo pipefail

UUID="${1:-}"  # empty if not passed
TMPDIR="/tmp/emulator${UUID:+_$UUID}"

mkdir -p "$TMPDIR"

ANDROID_SDK_ROOT="/opt/android"

AVD_NAME="api${ANDROID_API}"
CLONE_AVD="${AVD_NAME}_clone${UUID:+_$UUID}"
CLONE_DIR="$HOME/.android/avd/${CLONE_AVD}.avd"
CLONE_INI="$HOME/.android/avd/${CLONE_AVD}.ini"

ORIG_AVD_DIR="$HOME/.android/avd/${AVD_NAME}.avd"
ORIG_INI_FILE="$HOME/.android/avd/${AVD_NAME}.ini"

[[ -d "$ORIG_AVD_DIR" ]] || { echo "Original AVD-Directory not found: $ORIG_AVD_DIR" >&2; exit 1; }
[[ -f "$ORIG_INI_FILE" ]] || { echo "Original .ini file not found: $ORIG_INI_FILE" >&2; exit 1; }

echo "Cloning AVD $AVD_NAME → $CLONE_AVD..."

rm -rf "$CLONE_DIR"
cp -r "$ORIG_AVD_DIR" "$CLONE_DIR"

rm -f "$CLONE_INI"
cp "$ORIG_INI_FILE" "$CLONE_INI"

sed -i "s/${AVD_NAME}/${CLONE_AVD}/g" "$CLONE_INI" "$CLONE_DIR/config.ini"

find_free_emulator_port() {
  for port in $(seq 5556 2 5584); do
    if ! lsof -i ":$port" >/dev/null 2>&1 && ! lsof -i ":$((port + 1))" >/dev/null 2>&1; then
      echo "$port"
      return
    fi
  done
  echo "No free port in [5555–5585]" >&2
  exit 1
}

PORT=$(find_free_emulator_port)
EMULATOR_ID="emulator-${PORT}"

echo "$PORT" > "$TMPDIR/port"
echo "$EMULATOR_ID" > "$TMPDIR/id"

echo "Starting emulator $CLONE_AVD on port $PORT..."
"$ANDROID_SDK_ROOT"/emulator/emulator @"$CLONE_AVD" \
  -no-window -no-audio -gpu swiftshader_indirect -no-snapshot -no-boot-anim \
  -port "$PORT" \
  2>&1 &

echo $! > "$TMPDIR/pid"

echo "Waiting for $EMULATOR_ID..."
adb -s "$EMULATOR_ID" wait-for-device

boot_completed=""
until [[ "$boot_completed" == "1" ]]; do
  sleep 5
  boot_completed=$(adb -s "$EMULATOR_ID" shell getprop sys.boot_completed | tr -d '\r')
  echo "$EMULATOR_ID boot_completed=$boot_completed"
done

echo "Emulator $EMULATOR_ID ready."

rm -rf "$CLONE_DIR" "$CLONE_INI"
