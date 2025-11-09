#!/usr/bin/env bash
set -euo pipefail

: "${EMULATOR_PORT:=5554}"
: "${ANDROID_API:=28}"
: "${ANDROID_AVD_HOME:=/opt/avd}"
: "${EMULATOR_DEFAULT_ARGS:=-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on}"

NAME="ci-avd-${ANDROID_API}"
RUNTIME_AVD="$HOME/.android/avd/${NAME}.avd"
RUNTIME_INI="$HOME/.android/avd/${NAME}.ini"
LOGFILE="/tmp/emulator_${EMULATOR_PORT}.log"

adb start-server >/dev/null 2>&1 || true
mkdir -p "$HOME/.android/avd"

rm -rf "$RUNTIME_AVD"
cp -a "${ANDROID_AVD_HOME}/base-avd.avd" "$RUNTIME_AVD"
{
  echo "avd.ini.encoding=UTF-8"
  echo "path=$RUNTIME_AVD"
  echo "path.rel=avd/${NAME}.avd"
  echo "target=android-${ANDROID_API}"
} > "$RUNTIME_INI"

BOOT_TIMEOUT=300
if [ ! -e /dev/kvm ]; then
  BOOT_TIMEOUT=600
fi

echo "Starting emulator on port ${EMULATOR_PORT} (Timeout: ${BOOT_TIMEOUT}s, KVM: $([ -e /dev/kvm ] && echo yes || echo no))"
echo "Emulator log: ${LOGFILE}"

nohup with-kvm emulator -avd "$NAME" -port "$EMULATOR_PORT" \
      ${EMULATOR_DEFAULT_ARGS} \
      -wipe-data -no-snapshot-load -no-snapshot-save \
      >"${LOGFILE}" 2>&1 &

SERIAL="emulator-${EMULATOR_PORT}"
echo "Waiting for boot: ${SERIAL}"

set +e
timeout "${BOOT_TIMEOUT}s" bash -c "
  until adb -s ${SERIAL} shell getprop sys.boot_completed 2>/dev/null | grep -q '^1$'; do
    sleep 2
  done
"
BOOT_RC=$?
set -e

if [ ${BOOT_RC} -ne 0 ]; then
  echo "Emulator did not boot within ${BOOT_TIMEOUT}s (exit code: ${BOOT_RC})."
  echo "===== BEGIN EMULATOR LOG (head) ====="
  sed -n '1,120p' "${LOGFILE}" || true
  echo "===== END EMULATOR LOG (head) ====="
  echo "===== EMULATOR LOG (tail) ====="
  tail -n 80 "${LOGFILE}" || true
  echo "===== END EMULATOR LOG (tail) ====="
  echo "Running emulator processes:"
  ps aux | grep -i emulator | grep -v grep || true
  exit ${BOOT_RC}
fi

echo "Emulator boot completed."

adb -s "${SERIAL}" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb devices
echo "ANDROID_SERIAL=${SERIAL}"
