#!/usr/bin/env bash
# dump.sh <server-dir> <out-file> <cmds-file>... : `data get block` every position a `setblock` line in the
# cmds files placed, one "x y z|answer" line each, in file order.
D=$1 OUT=$2; shift 2
: > "$OUT"
grep -h '^setblock ' "$@" | while read -r _ x y z _; do
  n=$(wc -l < "$D/logs/latest.log")
  echo "data get block $x $y $z" > "$D/console.in"
  line=
  for _ in $(seq 1 50); do
    sleep 0.2
    # AttachmentHolder errors come from chunks loading (a removed mod's attachments), never from the
    # command, and would otherwise be taken as this position's answer.
    line=$(tail -n +$((n + 1)) "$D/logs/latest.log" | grep -v 'attachment.AttachmentHolder/' | grep -m1 -E "$x, $y, $z has the following block data|is not a block entity|Server thread/(ERROR|WARN)") && break
  done
  echo "$x $y $z|${line#*]: }" >> "$OUT"
done
wc -l < "$OUT"
