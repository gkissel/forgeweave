#!/usr/bin/env bash
# Runs the screenshot harness (docs/SCOPE.md issue #112): launches a real Minecraft client, opens
# every M1+ station screen in turn, and writes a PNG of each to build/screenshots/ for pre-release
# review. Not a CI gate -- a tool to run by hand before tagging a release; see docs/releasing.md.
#
# Requires a display. On a headless box, install xvfb (e.g. `apt install xvfb`) and this script
# runs the client under `xvfb-run` automatically; with a real display it runs directly.
set -euo pipefail
cd "$(dirname "$0")/.."

# NeoForge's early splash window hands its GL context off to Minecraft's own window at the end of
# mod loading. On a bare X/Xvfb display with no compositor that handoff times out and the client
# dies with "trouble handing off the window" before any screen renders. Skipping the splash entirely
# costs nothing here -- this run is headless and nobody is watching a progress bar (issue #101).
EARLY_WINDOW_OFF="-Dfml.earlyprogresswindow=false"

if [ -n "${DISPLAY:-}" ] || [ -n "${WAYLAND_DISPLAY:-}" ]; then
    ./gradlew runScreenshotHarness $EARLY_WINDOW_OFF
elif command -v xvfb-run >/dev/null 2>&1; then
    xvfb-run --auto-servernum ./gradlew runScreenshotHarness $EARLY_WINDOW_OFF
else
    echo "No display and xvfb-run not found. Install xvfb (e.g. 'apt install xvfb') or run this" >&2
    echo "from a machine with a display." >&2
    exit 1
fi

echo "Screenshots written to build/screenshots/"
