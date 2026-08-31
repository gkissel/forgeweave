#!/usr/bin/env python3
"""Procedurally generates Track B's 18 alloy tool materials' art (issue #840, epic #824 Track B): the
ingot, nugget and storage block for each material in dev.gkissel.forgeweave.trackb.TrackBAlloy.

Same approach as scripts/generate_track_b_ore_textures.py (issue #839): original art built from flat
shapes and a small palette derived from each material's own base color, deterministic per material id
(a fixed RNG seed) so re-running this script reproduces byte-identical output. Committed as static PNGs
under the standard (non-derived) `textures/block/` and `textures/item/` folders. Unlike the ore family,
alloys have no ore/raw-storage block, so this script only emits the three shapes an alloy-only metal
needs (pig_iron/knightslime's own "ingot/nugget/block" shape).

Usage: python3 scripts/generate_track_b_alloy_textures.py
Requires Pillow (`pip install pillow`).
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/item"

SIZE = 16

# (id, color) -- must match TrackBAlloy.ALL's ids and colors (research doc §7.3's "Alloy" table).
ALLOYS = [
    ("ironbrand", 0xB5502C),
    ("quakestone", 0x8FA35E),
    ("shardline", 0xA9D8E0),
    ("embercast", 0xE0611A),
    ("riftalloy", 0x7A3FA0),
    ("tideiron", 0x2F7A7A),
    ("cinderforge", 0xD1350B),
    ("dreadalloy", 0x2B3B2B),
    ("sunsteel", 0xE6C64A),
    ("hollowsteel", 0x9FB6C2),
    ("truesteel", 0xC7D6E8),
    ("stormalloy", 0x5C5B7A),
    ("glowveil", 0x4AE6C6),
    ("daybrass", 0xC9A227),
    ("faultsteel", 0x7A6852),
    ("skipalloy", 0x6FD1D1),
    ("mendalloy", 0x7FBF6B),
    ("mendstone", 0xC2A878),
]


def hex_to_rgb(color: int) -> tuple[int, int, int]:
    return (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF


def shade(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    r, g, b = rgb
    return (max(0, min(255, int(r * factor))), max(0, min(255, int(g * factor))), max(0, min(255, int(b * factor))))


def noisy(rgb: tuple[int, int, int], rng: random.Random, spread: int = 10) -> tuple[int, int, int]:
    return tuple(max(0, min(255, c + rng.randint(-spread, spread))) for c in rgb)


def new_canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def metal_block(color: tuple[int, int, int], rng: random.Random) -> Image.Image:
    """A flat panel with a lighter top-left bevel and a darker bottom-right bevel, like a storage block."""
    img = new_canvas()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            base = noisy(color, rng, 6)
            if x == 0 or y == 0:
                base = shade(base, 1.35)
            elif x == SIZE - 1 or y == SIZE - 1:
                base = shade(base, 0.65)
            px[x, y] = (*base, 255)
    return img


def ingot(color: tuple[int, int, int], rng: random.Random) -> Image.Image:
    """A trapezoid ingot bar, vanilla-iron-ingot silhouette, flat-shaded in the material color."""
    img = new_canvas()
    px = img.load()
    top_rows = {5: (5, 10), 6: (4, 11)}
    body_rows = range(7, 12)
    for y in range(5, 12):
        if y in top_rows:
            x0, x1 = top_rows[y]
        elif y in body_rows:
            x0, x1 = 3, 12
        else:
            continue
        for x in range(x0, x1):
            base = noisy(color, rng, 8)
            if y == 5 or x == x0:
                base = shade(base, 1.3)
            elif y == 11 or x == x1 - 1:
                base = shade(base, 0.7)
            px[x, y] = (*base, 255)
    return img


def nugget(color: tuple[int, int, int], rng: random.Random) -> Image.Image:
    """A small cluster of irregular flecks, vanilla-nugget-scaled."""
    img = new_canvas()
    px = img.load()
    cells = [(6, 6), (7, 6), (8, 7), (6, 8), (7, 8), (9, 8), (7, 9)]
    for x, y in cells:
        base = noisy(color, rng, 10)
        if (x + y) % 2 == 0:
            base = shade(base, 1.25)
        px[x, y] = (*base, 255)
    return img


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    for alloy_id, color_hex in ALLOYS:
        color = hex_to_rgb(color_hex)
        rng = random.Random(alloy_id)

        metal_block(color, rng).save(BLOCK_DIR / f"{alloy_id}_block.png")
        ingot(color, rng).save(ITEM_DIR / f"{alloy_id}_ingot.png")
        nugget(color, rng).save(ITEM_DIR / f"{alloy_id}_nugget.png")
        print(f"wrote {alloy_id} (3 sprites)")


if __name__ == "__main__":
    main()
