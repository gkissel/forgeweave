# java.sh: sourced by run_matrix.sh and mk.sh. Sets and exports FW_JAVA, the java binary for the NeoForge
# installer and the server, and stops if it is older than Java 25 (what NeoForge 26.1.2 needs).
# FW_JAVA wins, then $JAVA_HOME/bin/java, then java on PATH.
FW_JAVA=${FW_JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/java}}
export FW_JAVA=${FW_JAVA:-java}
fw_java_version=$("$FW_JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p') || true
if [ "${fw_java_version%%.*}" -ge 25 ] 2>/dev/null; then :; else
  echo "$FW_JAVA is Java ${fw_java_version:-?}, NeoForge 26.1.2 needs 25 or newer: set FW_JAVA or JAVA_HOME" >&2
  exit 1
fi
