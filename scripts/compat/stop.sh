#!/usr/bin/env bash
# stop.sh <server-dir> : send "stop" and wait up to 120 s for a clean exit; kill it after that.
D=$1; pid=$(cat "$D/server.pid" 2>/dev/null) || exit 0
# A dead server leaves no reader on the FIFO, and a plain write would then block forever.
kill -0 "$pid" 2>/dev/null || { echo "not running"; exit 0; }
timeout 10 bash -c 'echo stop > "$1"' _ "$D/console.in"
for _ in $(seq 1 120); do kill -0 "$pid" 2>/dev/null || { echo "stopped cleanly"; exit 0; }; sleep 1; done
echo "still running after 120s, killing it"; kill -9 -- "-$pid" 2>/dev/null || kill -9 "$pid"; exit 1
