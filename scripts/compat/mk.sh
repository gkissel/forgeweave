#!/usr/bin/env bash
# mk.sh <server-dir> <forgeweave-jar> <pack> : build a fresh dedicated server for a pack.
# Needs FW_SERVER_BASE: a NeoForge server install (run.sh + libraries/), see run_matrix.sh.
# Exits 2 when the pack names a local jar that no FW_COMPAT_JAR_DIRS directory holds.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
D=$1 FW=$2 PACK=$3
: "${FW_SERVER_BASE:?set FW_SERVER_BASE to a NeoForge server install}"
rm -rf "$D"; mkdir -p "$D/mods"
ln -s "$FW_SERVER_BASE/libraries" "$D/libraries"
cp "$FW_SERVER_BASE/run.sh" "$D/"
echo "-Xmx${FW_SERVER_XMX:-6G}" > "$D/user_jvm_args.txt"
echo eula=true > "$D/eula.txt"
# A port the kernel hands out as free, so concurrent runs don't collide. The server binds only this one:
# rcon and query stay off, and the runner talks to it through the console FIFO and the log.
# ponytail: free when built, not reserved; another process could take it before the boot. Set FW_SERVER_PORT then.
PORT=${FW_SERVER_PORT:-$(python3 -I -c 'import socket; s = socket.socket(); s.bind(("", 0)); print(s.getsockname()[1])')}
echo "server port $PORT"
cat > "$D/server.properties" <<P
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
server-port=$PORT
enable-rcon=false
enable-query=false
enable-command-block=true
max-tick-time=-1
P
cp "$FW" "$D/mods/"
python3 -I "$HERE/pack.py" mods "$PACK" "$D/mods"
