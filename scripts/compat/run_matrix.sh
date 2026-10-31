#!/usr/bin/env bash
# run_matrix.sh <release-jar> [pack...] : the dedicated-server compat matrix (see README.md).
# With no packs it runs every scripts/compat/packs/g*.txt. Writes build/compat/summary.md and exits 1 on
# any FAIL. A pack whose local jars are missing, or whose file has a `# SKIP on <version>: <reason>` line,
# is a SKIP, not a FAIL.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
[ $# -ge 1 ] && [ -f "$1" ] || { echo "usage: $0 <release-jar> [pack...]" >&2; exit 64; }
. "$HERE/java.sh"
JAR=$(realpath "$1"); shift
if [ $# -eq 0 ]; then set -- "$HERE"/packs/g*.txt; fi
OUT=${FW_COMPAT_OUT:-$REPO/build/compat}
CACHE=${FW_COMPAT_CACHE:-$HOME/.cache/forgeweave-compat}
NEO=$(sed -n 's/^neo_version=//p' "$REPO/gradle.properties")
export FW_SERVER_BASE=${FW_SERVER_BASE:-$CACHE/neoforge-$NEO}
FIXTURES=${FW_COMPAT_FIXTURES:-$REPO/src/test/resources/fixtures/save_compat}
FORCELOAD='forceload add -16 -16 47 31'
mkdir -p "$OUT"; SUMMARY=$OUT/summary.md; FAILS=0
printf '| Pack | Pass | Result | Detail |\n| --- | --- | --- | --- |\n' > "$SUMMARY"

if [ ! -f "$FW_SERVER_BASE/run.sh" ]; then
  echo "installing NeoForge $NEO server into $FW_SERVER_BASE"
  mkdir -p "$FW_SERVER_BASE"
  inst=$FW_SERVER_BASE/installer.jar
  curl -fsSL -o "$inst" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar" &&
    (cd "$FW_SERVER_BASE" && "$FW_JAVA" -jar installer.jar --installServer . > install.log 2>&1) ||
    { echo "NeoForge install failed, see $FW_SERVER_BASE/install.log" >&2; exit 1; }
fi

row() { # row <pass> <PASS|FAIL|SKIP> <detail>
  local detail=${3//|/\\|}
  printf '| %s | %s | %s | %s |\n' "$NAME" "$1" "$2" "${detail//$'\n'/; }" >> "$SUMMARY"
  echo "[$NAME] $1: $2 ${3//$'\n'/; }"
  if [ "$2" = FAIL ]; then FAILS=$((FAILS + 1)); fi
}

send() { # send <cmds-file>... : every non-comment line to the console
  grep -hv '^#' "$@" | grep . | while IFS= read -r c; do echo "$c" > "$SRV/console.in"; sleep 0.3; done
  sleep 3
}

boot() { "$HERE/start.sh" "$SRV" > "$DIR/$1.boot.txt" 2>&1; }

finish() { # finish <label> <phase> [config-before] : stop, keep the log, check it; sets NOTE and OK
  "$HERE/stop.sh" "$SRV" >> "$DIR/$1.boot.txt" 2>&1 || { OK=FAIL; NOTE+="no clean stop. "; }
  cp "$SRV/logs/latest.log" "$DIR/logs/$1.log"
  python3 -I "$HERE/pack.py" check "$PACK" "$DIR/logs/$1.log" "$2" ${3:+"$3"} > "$DIR/$1.check.txt" || OK=FAIL
  NOTE+="$(sed -n 's/^INFO //p; s/^FAIL /FAIL: /p' "$DIR/$1.check.txt" | head -4)"
}

pass() { # pass <label> <phase> <reference-dump> [compare-cmd] : boot, dump, stop, check, compare
  local label=$1 phase=$2 ref=$3 compare=${4:-cmp -s}
  OK=PASS NOTE=
  if ! boot "$label"; then row "$label" FAIL "no boot: $(tail -1 "$DIR/$label.boot.txt")"; "$HERE/stop.sh" "$SRV" > /dev/null 2>&1; return 1; fi
  "$HERE/dump.sh" "$SRV" "$DIR/dump-$label.txt" "${PLACE[@]}" > /dev/null
  finish "$label" "$phase"
  if [ "$compare" = 'cmp -s' ]; then
    diff "$ref" "$DIR/dump-$label.txt" > "$DIR/$label.diff.txt" ||
      { OK=FAIL; NOTE="dump differs from $(basename "$ref") in $(grep -c '^>' "$DIR/$label.diff.txt") positions. $NOTE"; }
  elif ! $compare "$ref" "$DIR/dump-$label.txt" > "$DIR/$label.diff.txt" 2>&1; then
    OK=FAIL; NOTE="$(tail -1 "$DIR/$label.diff.txt"). $NOTE"
  fi
  row "$label" "$OK" "${LEAD:-}$NOTE"
}

set_key() { # set_key <toml> <key-regex> <value> : prints how many keys it set
  sed -i -E "s/^(\s*($2) = )(true|false)\s*\$/\1$3/" "$SRV/config/forgeweave/$1"
  grep -cE "^\s*($2) = $3\s*\$" "$SRV/config/forgeweave/$1"
}

spark_pass() {
  OK=PASS NOTE=
  boot spark || { row spark FAIL "no boot"; return; }
  send "$HERE/fixtures/smeltery.cmds"
  n=$(wc -l < "$SRV/logs/latest.log")
  echo 'spark profiler start --timeout 60 --save-to-file' > "$SRV/console.in"
  for _ in $(seq 1 180); do
    profile=$(tail -n +"$n" "$SRV/logs/latest.log" | sed -n 's/.*Data has been written to: //p' | head -1)
    [ -n "$profile" ] && break; sleep 1
  done
  tps=$("$HERE/cmd.sh" "$SRV" 'spark tps' 3 | grep -A1 'TPS from last' | tail -1 | sed -E 's/.*\]\s+//' | cut -d, -f3 | tr -d ' *')
  share=$( [ -n "$profile" ] && python3 -I "$HERE/spark_fw.py" "$profile" | sed -nE 's/^Server thread: .*\(([0-9.]+)%\)$/\1/p')
  finish spark on
  python3 -I -c "import sys; sys.exit(0 if float(sys.argv[1] or 0) >= float(sys.argv[2]) else 1)" "$tps" "${FW_SPARK_MIN_TPS:-19.5}" || OK=FAIL
  python3 -I -c "import sys; sys.exit(0 if sys.argv[1] and float(sys.argv[1]) <= float(sys.argv[2]) else 1)" "$share" "${FW_SPARK_MAX_SHARE:-2}" || OK=FAIL
  row spark "$OK" "TPS 1m ${tps:-?}, forgeweave ${share:-?}% of the server thread. $NOTE"
}

upgrade_pass() { # the corpus placed under the previous release, then loaded by this one
  local prev; prev=$(python3 -I "$HERE/pack.py" jar "$PACK" upgrade-from) || { row upgrade FAIL "previous jar not resolved"; return; }
  local SRV=$DIR/upgrade/server; OK=PASS NOTE=
  "$HERE/mk.sh" "$SRV" "$prev" "$PACK" > "$DIR/upgrade.mk.txt" 2>&1 || { row upgrade FAIL "could not build: $(tail -1 "$DIR/upgrade.mk.txt")"; return; }
  boot upgrade-prev || { row upgrade FAIL "previous release did not boot"; return; }
  echo "$FORCELOAD" > "$SRV/console.in"; sleep 2; send "${PLACE[@]}"
  "$HERE/dump.sh" "$SRV" "$DIR/dump-upgrade-prev.txt" "${PLACE[@]}" > /dev/null
  "$HERE/stop.sh" "$SRV" > /dev/null 2>&1; cp "$SRV/logs/latest.log" "$DIR/logs/upgrade-prev.log"
  local kept; kept=$(python3 -I "$HERE/check_items.py" "$DIR/place.idx" "$DIR/dump-upgrade-prev.txt" | tail -1)
  rm "$SRV/mods/$(basename "$prev")"; cp "$JAR" "$SRV/mods/"
  # The previous release's config predates newer keys (beta.12 has no emiPlugin), so NeoForge corrects
  # it on this boot. pack.py allows that WARN only if every old key kept its value.
  rm -rf "$DIR/upgrade-config-before"; cp -r "$SRV/config/forgeweave" "$DIR/upgrade-config-before"
  boot upgrade || { row upgrade FAIL "no boot on the upgraded world"; return; }
  "$HERE/dump.sh" "$SRV" "$DIR/dump-upgrade.txt" "${PLACE[@]}" > /dev/null
  finish upgrade on "$DIR/upgrade-config-before"
  cmp -s "$DIR/dump-upgrade-prev.txt" "$DIR/dump-upgrade.txt" || { OK=FAIL; NOTE="dump differs from the $(basename "$prev") one. $NOTE"; }
  row upgrade "$OK" "from $(basename "$prev") ($kept under it). $NOTE"
}

run_pack() {
  PACK=$(realpath "$1"); NAME=$(basename "$PACK" .txt); DIR=$OUT/$NAME; SRV=$DIR/server
  local skip; skip=$(sed -n 's/^# SKIP on [^:]*: //p' "$PACK" | head -1)
  if [ -n "$skip" ]; then row build SKIP "$skip"; return; fi
  rm -rf "$DIR"; mkdir -p "$DIR/logs"
  "$HERE/mk.sh" "$SRV" "$JAR" "$PACK" > "$DIR/mk.txt" 2>&1; rc=$?
  if [ $rc -eq 2 ]; then row build SKIP "$(tail -1 "$DIR/mk.txt")"; return; fi
  if [ $rc -ne 0 ]; then row build FAIL "$(tail -1 "$DIR/mk.txt")"; return; fi
  python3 -I "$HERE/fixtures_to_cmds.py" "$FIXTURES" "$DIR/place.cmds" "$DIR/place.idx" > /dev/null
  PLACE=("$DIR/place.cmds")
  while read -r f; do PLACE+=("$HERE/fixtures/$f"); done < <(python3 -I "$HERE/pack.py" get "$PACK" fixture)

  OK=PASS NOTE=
  boot boot || { row boot FAIL "no boot: $(tail -1 "$DIR/boot.boot.txt")"; "$HERE/stop.sh" "$SRV" > /dev/null 2>&1; return; }
  echo "$FORCELOAD" > "$SRV/console.in"; sleep 2; send "${PLACE[@]}"
  "$HERE/dump.sh" "$SRV" "$DIR/dump-placed.txt" "${PLACE[@]}" > /dev/null
  finish boot on
  python3 -I "$HERE/check_items.py" "$DIR/place.idx" "$DIR/dump-placed.txt" "$FIXTURES" > "$DIR/corpus.txt" || OK=FAIL
  row boot "$OK" "$(grep '^missing' "$DIR/corpus.txt"). $NOTE"

  pass restart on "$DIR/dump-placed.txt" || return
  n=$(set_key compat-server.toml '[A-Za-z]+' false)
  # The checklist (line 139) lists 20; #1136's emiPlugin came after it.
  [ "$n" = 21 ] || row toggles FAIL "compat-server.toml has $n boolean toggles, expected the checklist's 20 plus emiPlugin"
  pass toggles-off off "$DIR/dump-restart.txt"
  set_key compat-server.toml '[A-Za-z]+' true > /dev/null
  pass toggles-on on "$DIR/dump-restart.txt"
  [ "$(set_key content-server.toml toolLeveling false)" = 1 ] || row tool-leveling-off FAIL "no toolLeveling key"
  pass tool-leveling-off on "$DIR/dump-restart.txt"
  set_key content-server.toml toolLeveling true > /dev/null

  local drop; drop=$(python3 -I "$HERE/pack.py" get "$PACK" without)
  if [ -n "$drop" ]; then
    mkdir -p "$DIR/removed"
    (cd "$SRV/mods" && for g in $drop; do mv -- $g "$DIR/removed/"; done)
    LEAD="removed $(ls "$DIR/removed" | paste -sd' '). " pass without without "$DIR/dump-restart.txt" "python3 -I $HERE/vanished.py"
    mv "$DIR/removed/"* "$SRV/mods/"
  fi
  python3 -I "$HERE/pack.py" get "$PACK" spark | grep -q . && spark_pass
  python3 -I "$HERE/pack.py" get "$PACK" upgrade-from | grep -q . && upgrade_pass
}

for p in "$@"; do run_pack "$p"; done
echo; cat "$SUMMARY"
[ "$FAILS" -eq 0 ]
