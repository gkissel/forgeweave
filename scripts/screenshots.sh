#!/usr/bin/env bash
# Runs the screenshot harness (docs/SCOPE.md issue #112): launches a real Minecraft client, opens
# every M1+ station screen in turn, and writes a PNG of each to build/screenshots/ for pre-release
# review. Not a CI gate -- a tool to run by hand before tagging a release; see docs/releasing.md.
#
# Requires a display. On a headless box, install xvfb (e.g. `apt install xvfb`) and this script
# runs the client under `xvfb-run` automatically; with a real display it runs directly.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${DISPLAY:-}" ] || [ -n "${WAYLAND_DISPLAY:-}" ]; then
    ./gradlew runScreenshotHarness
elif command -v xvfb-run >/dev/null 2>&1; then
    xvfb-run --auto-servernum ./gradlew runScreenshotHarness
else
    echo "No display and xvfb-run not found. Install xvfb (e.g. 'apt install xvfb') or run this" >&2
    echo "from a machine with a display." >&2
    exit 1
fi

echo "Screenshots written to build/screenshots/"
