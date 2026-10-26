#!/usr/bin/env bash
# Runs the screenshot harness (scripts/screenshots.sh's job) inside a private, hidden display, so it
# can run unattended from a desktop session without ever opening a window on it. The harness exit
# code is passed through: 0 means every frame and every self-check passed.
#
#   scripts/screenshots-headless.sh               headless sway + Xwayland (GPU rendering)
#   scripts/screenshots-headless.sh --software    Xvfb (llvmpipe), for boxes without sway
#   scripts/screenshots-headless.sh -PharnessNoJei    any other argument goes to Gradle
#
# The client never sees the caller's display: DISPLAY, WAYLAND_DISPLAY and the compositor sockets
# are unset first, and the client gets only the display of the compositor this script starts.
set -euo pipefail
cd "$(dirname "$0")/.."

software=false
gradle_args=()
for arg in "$@"; do
    case "$arg" in
        --software) software=true ;;
        *) gradle_args+=("$arg") ;;
    esac
done

desktop_display="${DISPLAY:-}"
unset DISPLAY WAYLAND_DISPLAY HYPRLAND_INSTANCE_SIGNATURE SWAYSOCK I3SOCK

# No daemon and no configuration cache: either could hand the client an environment captured in an
# earlier build, desktop DISPLAY included. 1920x1080 is full-screen at the 854x480 aspect the
# first-person poses are framed against.
gradle_args+=(--no-daemon --no-configuration-cache runScreenshotHarness -PharnessWindow=1920x1080)

# The harness's own game directory (build.gradle's screenshotHarness run). A fresh world every run:
# a second run would otherwise land in the first one's world with every scene still standing.
game_dir=run/harness
mkdir -p "$game_dir/config"
rm -rf "$game_dir"/saves/forgeweave_screenshot_harness*
# NeoForge's early loading window hands its GL context to Minecraft's window at the end of mod
# loading, and on a bare X display that handoff can time out (issue #101). Nobody watches it here.
fml="$game_dir/config/fml.toml"
if [ -f "$fml" ] && grep -q '^earlyWindowControl' "$fml"; then
    sed -i 's/^earlyWindowControl.*/earlyWindowControl = false/' "$fml"
else
    echo 'earlyWindowControl = false' >> "$fml"
fi

if $software || ! command -v sway >/dev/null 2>&1; then
    command -v xvfb-run >/dev/null 2>&1 || { echo "Neither sway nor xvfb-run is installed." >&2; exit 1; }
    echo "Running the harness under Xvfb (software rendering)"
    exec xvfb-run --auto-servernum --server-args='-screen 0 1920x1080x24' ./gradlew "${gradle_args[@]}"
fi

work=$(mktemp -d)
sway_pid=
cleanup() {
    [ -n "$sway_pid" ] && kill "$sway_pid" 2>/dev/null && wait "$sway_pid" 2>/dev/null
    rm -rf "$work"
}
trap cleanup EXIT

# One headless output, no borders, and the client full-screen on it. The exec line reports the
# displays sway gave its own children, which is how this script learns the Xwayland display.
printf 'printf "%%s\\n" "$DISPLAY" > "%s/display"\n' "$work" > "$work/report.sh"
cat > "$work/config" <<EOF
xwayland enable
default_border none
default_floating_border none
output HEADLESS-1 resolution 1920x1080
for_window [class="^Minecraft"] fullscreen enable
exec sh "$work/report.sh"
EOF
WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 sway -c "$work/config" > "$work/sway.log" 2>&1 &
sway_pid=$!

for _ in $(seq 100); do
    [ -s "$work/display" ] && break
    kill -0 "$sway_pid" 2>/dev/null || { cat "$work/sway.log" >&2; echo "sway exited early" >&2; exit 1; }
    sleep 0.1
done
headless_display=$(cat "$work/display" 2>/dev/null || true)
if [ -z "$headless_display" ] || [ "$headless_display" = "$desktop_display" ]; then
    echo "Headless sway did not report a display of its own (got '$headless_display'); refusing to run." >&2
    exit 1
fi
echo "Running the harness on headless sway, DISPLAY=$headless_display"

status=0
DISPLAY="$headless_display" ./gradlew "${gradle_args[@]}" || status=$?
exit "$status"
