#!/usr/bin/env python3
"""Generates the scimitar's original art (issue #159).

The scimitar is one of the three "new modern-era shapes, ours" (docs/SCOPE.md M3 content manifest):
neither the 1.12 clone nor the 1.20 branch has a scimitar, a curved blade part, or anything to
derive from, so every file written here is freshly authored and gets **no** NOTICE.md row -- same
standing as issue #151's `vein_hammer_head.png`. (The battleaxe, by contrast, does have upstream art;
see `generate_battleaxe_head.py` and NOTICE.md.)

Written here:

* `textures/item/curved_blade.png`  -- the new head part's inventory icon
* `textures/tools/scimitar_head.png`    -- same silhouette as the tool's head layer
* `textures/tools/scimitar_handle.png`  -- grip layer
* `textures/tools/scimitar_binding.png` -- quillon/guard layer
* `textures/mob_effect/lacerate.png` -- the 18x18 status icon for the scimitar's lacerate DoT

Style is matched to the derived parts so the roster reads as one set: a 0x44 outline over a plain
greyscale ramp (196/219/241/255), tinted per-material at runtime by `ForgeweaveItemColors`. Silhouette
is deliberately unlike `sword_blade.png`'s straight double-edged blade -- a single-edged blade that
*widens* toward the tip, which is the scimitar read.

The lacerate icon is the one file here that is not greyscale: status-effect icons are never tinted,
so it is drawn in its own red so a player can tell the stack apart at a glance.

Usage: python3 scripts/generate_scimitar_art.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave"

TRANSPARENT = (0, 0, 0, 0)
PALETTE = {
    ".": TRANSPARENT,
    "#": (68, 68, 68, 255),    # outline, upstream's own
    "s": (196, 196, 196, 255),  # shadow / blunt back edge
    "m": (219, 219, 219, 255),  # mid
    "l": (241, 241, 241, 255),  # light
    "h": (255, 255, 255, 255),  # highlight / cutting edge
}

# The curved blade: tip at the upper right, widening toward it, back edge shaded.
BLADE = [
    "................",
    "..........####..",
    ".........#hhms#.",
    "........#hms##..",
    ".......#hms#....",
    ".......#hm#.....",
    "......#hm#......",
    "......#m#.......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

# The grip, running down-left from under the guard, one pixel per row like every other tool layer.
HANDLE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......#m#.......",
    ".....#l#........",
    "....#m#.........",
    "...#l#..........",
    "..#m#...........",
    ".#l#............",
    ".##.............",
    "................",
]

# The quillons, sitting across the blade at the top of the grip.
BINDING = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".........#......",
    ".......#hm#.....",
    "........#.......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

# 18x18, the size vanilla's status-effect sprites are. Three claw slashes -- one per lacerate stack.
BLEED_PALETTE = {
    ".": TRANSPARENT,
    "#": (74, 12, 14, 255),
    "r": (152, 30, 32, 255),
    "b": (204, 52, 48, 255),
    "l": (238, 108, 96, 255),
}
BLEED = [
    "..................",
    "..#....#.....#....",
    ".#b#..#b#...#b#...",
    ".#l#..#l#...#l#...",
    ".#b#..#b#...#b#...",
    ".#b#..#b#...#b#...",
    ".#l#..#l#...#l#...",
    ".#b#..#b#...#b#...",
    "..#b#..#b#...#b#..",
    "..#l#..#l#...#l#..",
    "..#b#..#b#...#b#..",
    "...#r#..#r#...#r#.",
    "...#r#..#r#...#r#.",
    "....##...##....##.",
    "....#.....#.......",
    "..................",
    "..................",
    "..................",
]


def write(rows: list[str], palette: dict[str, tuple[int, int, int, int]], path: Path) -> None:
    size = len(rows)
    image = Image.new("RGBA", (size, size), TRANSPARENT)
    pixels = image.load()
    for y, row in enumerate(rows):
        if len(row) != size:
            raise ValueError(f"{path.name} row {y} is {len(row)} wide, expected {size}")
        for x, char in enumerate(row):
            pixels[x, y] = palette[char]
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print(f"wrote {path}")


def main() -> None:
    write(BLADE, PALETTE, ASSETS / "textures/item/curved_blade.png")
    write(BLADE, PALETTE, ASSETS / "textures/tools/scimitar_head.png")
    write(HANDLE, PALETTE, ASSETS / "textures/tools/scimitar_handle.png")
    write(BINDING, PALETTE, ASSETS / "textures/tools/scimitar_binding.png")
    write(BLEED, BLEED_PALETTE, ASSETS / "textures/mob_effect/lacerate.png")


if __name__ == "__main__":
    main()
