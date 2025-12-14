#!/usr/bin/env bash
set -euo pipefail

EMULATOR_PORT="${EMULATOR_PORT:-5554}"
EMULATOR_TIMEOUT="${EMULATOR_TIMEOUT:-300}"
BASE_AVD_DIR="/opt/avd-template/base-avd.avd"
AVD_NAME="ci_running_avd"

echo "--- Preparing Android Emulator ---"

export ANDROID_AVD_HOME="${HOME}/.android/avd"
mkdir -p "${ANDROID_AVD_HOME}" "${HOME}/.android"
touch "${HOME}/.android/emu-update-last-check.ini"

rm -rf "${ANDROID_AVD_HOME}/${AVD_NAME}.avd" "${ANDROID_AVD_HOME}/${AVD_NAME}.ini"

cp -a "${BASE_AVD_DIR}" "${ANDROID_AVD_HOME}/${AVD_NAME}.avd"

cat > "${ANDROID_AVD_HOME}/${AVD_NAME}.ini" <<EOF
avd.ini.encoding=UTF-8
path=${ANDROID_AVD_HOME}/${AVD_NAME}.avd
target=android-${ANDROID_API_LEVEL:-36}
EOF

EMU_ARGS="-avd ${AVD_NAME} -port ${EMULATOR_PORT} -no-window -no-audio -no-boot-anim -no-snapshot-load -no-snapshot-save -wipe-data"

if [ -e /dev/kvm ] && [ -w /dev/kvm ]; then
    echo "KVM detected. Using hardware acceleration."
    EMU_ARGS="$EMU_ARGS -accel on -gpu swiftshader_indirect"
else
    echo "KVM not available. Using software rendering."
    EMU_ARGS="$EMU_ARGS -accel off -gpu swiftshader_indirect"
fi

adb start-server >/dev/null 2>&1 || true

echo "Starting emulator process..."
LOGFILE="/tmp/emulator.log"
nohup emulator $EMU_ARGS > "$LOGFILE" 2>&1 &
EMU_PID=$!

echo "Emulator started (PID: $EMU_PID). Waiting for boot..."

START_TIME=$(date +%s)
BOOT_COMPLETED=false

while true; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))

    if [ "$ELAPSED" -gt "$EMULATOR_TIMEOUT" ]; then
        echo "Error: Timeout reached ($EMULATOR_TIMEOUT seconds)."
        break
    fi

    if ! kill -0 "$EMU_PID" >/dev/null 2>&1; then
        echo "Error: Emulator process died."
        break
    fi

    if adb -s "emulator-${EMULATOR_PORT}" shell getprop sys.boot_completed 2>/dev/null | grep -q '1'; then
        BOOT_COMPLETED=true
        break
    fi

    sleep 2
done

if [ "$BOOT_COMPLETED" = true ]; then
    echo "Emulator is ready."
    adb -s "emulator-${EMULATOR_PORT}" shell settings put global window_animation_scale 0
    adb -s "emulator-${EMULATOR_PORT}" shell settings put global transition_animation_scale 0
    adb -s "emulator-${EMULATOR_PORT}" shell settings put global animator_duration_scale 0
    adb -s "emulator-${EMULATOR_PORT}" shell input keyevent 82
else
    echo "Emulator failed to boot. Tail of log:"
    tail -n 50 "$LOGFILE"
    exit 1
fi