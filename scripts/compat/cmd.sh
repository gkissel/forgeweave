#!/usr/bin/env bash
# cmd.sh <server-dir> <command> [wait-seconds] : send a console command, print the log lines it caused.
D=$1; n=$(wc -l < "$D/logs/latest.log")
echo "$2" > "$D/console.in"; sleep "${3:-2}"
tail -n +$((n + 1)) "$D/logs/latest.log"
