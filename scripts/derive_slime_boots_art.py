#!/usr/bin/env python3
"""Derive the slime boots' item sprite and armour layer from upstream 1.12 (issue #452, T21).

Upstream ships both textures greyscale and tints them at render time from
``BlockSlime.SlimeType#getBallColor``, because ``ItemSlimeBoots`` has one item per slime colour.
Forgeweave ships only the green pair (no coloured slime balls exist yet -- parity audit T57), so the
green tint ``0x69bc5e`` is multiplied into the pixels here instead of registered as a colour handler
plus a dyeable armour layer. Re-run after changing either upstream source; see NOTICE.md.

Usage: python3 scripts/derive_slime_boots_art.py [path/to/tinkers-1.12]
"""

import os
import sys

from PIL import Image

GREEN_BALL_COLOR = (0x69, 0xBC, 0x5E)

DEFAULT_UPSTREAM = os.path.expanduser("~/development/minecraft/references/tinkers-1.12")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "src/main/resources/assets/forgeweave/textures")

PAIRS = [
    ("resources/assets/tconstruct/textures/items/armor/slime_boots.png", "derived/item/slime_boots.png"),
    ("resources/assets/tconstruct/textures/models/armor/slime_layer_1.png", "models/armor/derived/slime_layer_1.png"),
]


def tint(source, target):
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (
                r * GREEN_BALL_COLOR[0] // 255,
                g * GREEN_BALL_COLOR[1] // 255,
                b * GREEN_BALL_COLOR[2] // 255,
                a,
            )
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)
    print("wrote", os.path.relpath(target, REPO))


def main():
    upstream = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_UPSTREAM
    for source, target in PAIRS:
        tint(os.path.join(upstream, source), os.path.join(ASSETS, target))


if __name__ == "__main__":
    main()
