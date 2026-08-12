#!/usr/bin/env python3
"""Generates the vein hammer head part's original 16x16 icon (issue #151).

The vein hammer is one of the two modern-branch shapes docs/SCOPE.md M3 authorizes (the other is
the dagger's knife blade, which upstream 1.12 already has art for under `textures/items/parts/
knife_blade.png`). Upstream 1.20's own vein hammer reuses the regular hammer's `hammer_head` part
outright (`tinkering/tool_definitions/vein_hammer.json`), so neither branch has art to derive for a
*distinct* vein hammer head -- this is freshly authored, not a NOTICE.md row.

Shape: a geologist's pick -- a flat mallet face (for the "hammer" half) with a tapering pick spike
(for the "vein" half, evoking prospecting/vein-finding), plus a small cyan ore-sensing crystal chip
embedded in the face. Distinct silhouette from `hammer_head.png`'s diagonal pick-block shape.
Palette matches the derived head parts' plain greyscale metal ramp (outline/shadow/mid/highlight),
plus the one accent color.

Usage: python3 scripts/generate_vein_hammer_head.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/assets/forgeweave/textures/item/vein_hammer_head.png"
)

TRANSPARENT = (0, 0, 0, 0)
OUTLINE = (68, 68, 68, 255)
SHADOW = (140, 140, 140, 255)
MID = (185, 185, 185, 255)
HIGHLIGHT = (225, 225, 225, 255)
CRYSTAL = (76, 201, 210, 255)
CRYSTAL_DARK = (44, 150, 158, 255)

# 16x16 grid, one character per pixel:
#   . transparent   # outline   s shadow   m mid   h highlight   c crystal accent   d crystal shadow
ROWS = [
    "................",
    "................",
    "................",
    "..........####..",
    ".........#hhh#..",
    "......####hhh#..",
    ".....#mm#mccm#..",
    "....#mm#smddm#..",
    "...#mm#ssmmm#...",
    "..#mm#ssssm#....",
    ".#ss#ssssm#.....",
    ".##ssssm#.......",
    "..###m#.........",
    "...###..........",
    "................",
    "................",
]

PALETTE = {
    ".": TRANSPARENT,
    "#": OUTLINE,
    "s": SHADOW,
    "m": MID,
    "h": HIGHLIGHT,
    "c": CRYSTAL,
    "d": CRYSTAL_DARK,
}


def main() -> None:
    image = Image.new("RGBA", (16, 16), TRANSPARENT)
    pixels = image.load()
    for y, row in enumerate(ROWS):
        for x, char in enumerate(row):
            pixels[x, y] = PALETTE[char]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT)
    print(f"wrote {OUTPUT}")


if __name__ == "__main__":
    main()
