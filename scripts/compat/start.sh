#!/usr/bin/env bash
# start.sh <server-dir> : boot the server with a FIFO console (console.in) and wait for "Done (".
D=$1; cd "$D"
rm -f console.in; mkfifo console.in
rm -f logs/latest.log
start=$(date +%s)
setsid bash -c 'exec 3<>console.in; exec ./run.sh nogui <&3 >console.out 2>&1' &
echo $! > server.pid
for _ in $(seq 1 "${FW_BOOT_TIMEOUT:-900}"); do
  if grep -q 'Done (' logs/latest.log 2>/dev/null; then echo "booted in $(( $(date +%s) - start ))s"; exit 0; fi
  if ! kill -0 "$(cat server.pid)" 2>/dev/null; then echo "server died"; tail -40 console.out; exit 1; fi
  sleep 1
done
echo "boot timed out"; exit 2
