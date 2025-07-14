#!/usr/bin/env bash
set -euo pipefail

UUID="${1:-}"
TMPDIR="/tmp/emulator${UUID:+_$UUID}"

if [[ -f "$TMPDIR/pid" ]]; then
  PID=$(cat "$TMPDIR/pid")
  echo "Stopping emulator PID $PID"
  kill "$PID" || true
  wait "$PID" || true
else
  echo "No PID found at $TMPDIR/pid"
fi

rm -rf "$TMPDIR"
