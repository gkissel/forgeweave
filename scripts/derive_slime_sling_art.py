"""Derive the six coloured Slimesling sprites from upstream 1.12 (issue #649, T57/T22).

Upstream ships one greyscale ``items/gadgets/slimesling.png`` and tints it at render time through an
``ItemColors`` handler that samples ``BlockSlime.SlimeType#getBallColor`` off the stack's metadata.
Forgeweave ships one item per colour with the ball colour multiplied into the pixels here instead,
the same baked-tint adaptation ``derive_slime_boots_art.py`` made for the boots at #452. Green
writes over the pre-split ``slime_sling.png`` (which #453 had copied untinted -- upstream never
renders the raw greyscale). Re-run after changing the upstream sprite; see NOTICE.md.

Usage: python3 scripts/derive_slime_sling_art.py [path/to/tinkers-1.12]
"""

import os
import sys

from PIL import Image

# BlockSlime.SlimeType's ballColor per colour, the value its getColor(stack) handler returns.
BALL_COLORS = {
    "slime_sling": (0x69, 0xBC, 0x5E),  # GREEN keeps the pre-split id
    "blue_slime_sling": (0x74, 0xC5, 0xC8),
    "purple_slime_sling": (0xCC, 0x68, 0xFF),
    "blood_slime_sling": (0xB8, 0x00, 0x00),
    "magma_slime_sling": (0xFF, 0xAB, 0x49),
    "pink_slime_sling": (0xBC, 0x9E, 0xB4),
}

UPSTREAM_SPRITE = "resources/assets/tconstruct/textures/items/gadgets/slimesling.png"
DEFAULT_UPSTREAM = os.path.expanduser("~/development/minecraft/references/tinkers-1.12")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DERIVED = os.path.join(REPO, "src/main/resources/assets/forgeweave/textures/derived/item")


def tint(source, target, color):
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (
                r * color[0] // 255,
                g * color[1] // 255,
                b * color[2] // 255,
                a,
            )
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target)
    print("wrote", os.path.relpath(target, REPO))


def main():
    upstream = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_UPSTREAM
    source = os.path.join(upstream, UPSTREAM_SPRITE)
    for name, color in BALL_COLORS.items():
        tint(source, os.path.join(DERIVED, name + ".png"), color)


if __name__ == "__main__":
    main()
