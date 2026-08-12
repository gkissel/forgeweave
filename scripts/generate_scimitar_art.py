#!/usr/bin/env python3
"""Generates the scimitar's lacerate status-effect icon (issue #159).

Issue #198 replaced the scimitar's blade/handle/binding art -- which used to be freshly authored
here -- with upstream-derived art; see `scripts/derive_m3_weapon_art.py` and NOTICE.md. What's left
in this script is the one piece of scimitar-adjacent art that stays freshly authored on purpose:

* `textures/mob_effect/lacerate.png` -- the 18x18 status icon for the scimitar's lacerate DoT

Status-effect icons are never tinted by `ForgeweaveItemColors` (unlike tool/part art), so there is no
upstream greyscale ramp to derive from here in the first place -- it is drawn in its own red so a
player can tell the stack apart at a glance, the same standing pattern textures and other UI-only
glyphs have.

Usage: python3 scripts/generate_scimitar_art.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave"

TRANSPARENT = (0, 0, 0, 0)

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
    write(BLEED, BLEED_PALETTE, ASSETS / "textures/mob_effect/lacerate.png")


if __name__ == "__main__":
    main()
