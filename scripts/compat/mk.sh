#!/usr/bin/env bash
# mk.sh <server-dir> <forgeweave-jar> <pack> : build a fresh dedicated server for a pack.
# Needs FW_SERVER_BASE: a NeoForge server install (run.sh + libraries/), see run_matrix.sh.
# Exits 2 when the pack names a local jar that no FW_COMPAT_JAR_DIRS directory holds.
# The server's run.sh calls the Java 25 java.sh finds (FW_JAVA, JAVA_HOME), not java on PATH.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
D=$1 FW=$2 PACK=$3
: "${FW_SERVER_BASE:?set FW_SERVER_BASE to a NeoForge server install}"
. "$HERE/java.sh"
rm -rf "$D"; mkdir -p "$D/mods"
ln -s "$FW_SERVER_BASE/libraries" "$D/libraries"
# NeoForge's run.sh is `exec java @user_jvm_args.txt @libraries/.../unix_args.txt "$@"`.
sed "s|^exec java |exec \"$FW_JAVA\" |" "$FW_SERVER_BASE/run.sh" > "$D/run.sh"; chmod +x "$D/run.sh"
grep -qF "exec \"$FW_JAVA\" " "$D/run.sh" || { echo "no 'exec java' line in $FW_SERVER_BASE/run.sh to point at $FW_JAVA"; exit 1; }
echo "-Xmx${FW_SERVER_XMX:-6G}" > "$D/user_jvm_args.txt"
echo eula=true > "$D/eula.txt"
cat > "$D/server.properties" <<P
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
server-port=${FW_SERVER_PORT:-25599}
enable-command-block=true
max-tick-time=-1
P
cp "$FW" "$D/mods/"
python3 -I "$HERE/pack.py" mods "$PACK" "$D/mods"
