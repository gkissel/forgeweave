#!/usr/bin/env python3
"""Procedurally generates Track B's ore family art (issue #839, epic #824 Track B): the ore block,
storage block, raw-storage block, ingot, nugget and raw item for each of the 12 materials in
dev.gkissel.forgeweave.trackb.TrackBOre.

Original art, not a derivation of any Tinkers'/Mantle/Spartan Weaponry clone (CLAUDE.md's provenance
rules) and not a recolor of a vanilla texture either -- Forgeweave has no in-repo precedent for a
from-scratch ore family (armor_station_top.png is the only prior original block texture), so this
script builds each sprite from flat shapes and a small palette derived from each material's own base
color, deterministic per material id (a fixed RNG seed) so re-running this script reproduces
byte-identical output. Committed as static PNGs under the standard (non-derived) `textures/block/`
and `textures/item/` folders, matching how `scripts/recolor_raw_ore.py`'s raw_cobalt/raw_ardite output
already lives outside `derived/`.

Usage: python3 scripts/generate_track_b_ore_textures.py
Requires Pillow (`pip install pillow`).
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/item"

SIZE = 16

# (id, color, host) -- host picks the ore block's base rock. Must match TrackBOre.ALL.
ORES = [
    ("cinderstone", 0x8A8A86, "stone"),
    ("fulmenite", 0xC8D94A, "deepslate"),
    ("duskspar", 0x8A5FD9, "deepslate"),
    ("voltcinder", 0x38D9D0, "netherrack"),
    ("murkiron", 0x3A5C56, "deepslate"),
    ("hardcinder", 0xC23B2B, "netherrack"),
    ("nightshale", 0x3B3F7A, "deepslate"),
    ("warspar", 0xA4283F, "deepslate"),
    ("hollowstone", 0xD8D3C2, "deepslate"),
    ("resonite", 0x3FAE9E, "deepslate"),
    ("starfall_stone", 0xBCD6F2, "stone"),
    ("voidglass", 0x2A1740, "deepslate"),
]

HOST_BASE = {
    "stone": (125, 125, 125),
    "deepslate": (77, 77, 82),
    "netherrack": (107, 58, 48),
}


def hex_to_rgb(color: int) -> tuple[int, int, int]:
    return (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF


def shade(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    r, g, b = rgb
    return (max(0, min(255, int(r * factor))), max(0, min(255, int(g * factor))), max(0, min(255, int(b * factor))))


def noisy(rgb: tuple[int, int, int], rng: random.Random, spread: int = 10) -> tuple[int, int, int]:
    return tuple(max(0, min(255, c + rng.randint(-spread, spread))) for c in rgb)


def new_canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def ore_block(ore_color: tuple[int, int, int], host_base: tuple[int, int, int], rng: random.Random) -> Image.Image:
    """Speckled stone/deepslate/netherrack base with 3-4 ore-colored clusters, like a vanilla ore block."""
    img = new_canvas()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = (*noisy(host_base, rng, 8), 255)
    clusters = rng.randint(3, 4)
    for _ in range(clusters):
        cx, cy = rng.randint(2, SIZE - 3), rng.randint(2, SIZE - 3)
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if rng.random() < 0.6:
                    x, y = cx + dx, cy + dy
                    if 0 <= x < SIZE and 0 <= y < SIZE:
                        px[x, y] = (*noisy(ore_color, rng, 14), 255)
    return img


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


def raw_block(color: tuple[int, int, int], rng: random.Random) -> Image.Image:
    """A rockier, duller panel -- same bevel idiom as metal_block but muted and with a mottled fill."""
    dull = shade(color, 0.75)
    img = new_canvas()
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            base = noisy(dull, rng, 16)
            if x == 0 or y == 0:
                base = shade(base, 1.2)
            elif x == SIZE - 1 or y == SIZE - 1:
                base = shade(base, 0.75)
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


def raw_item(color: tuple[int, int, int], rng: random.Random) -> Image.Image:
    """An irregular chunk, vanilla-raw-ore-scaled, duller than the ingot with a few bright flecks."""
    dull = shade(color, 0.8)
    img = new_canvas()
    px = img.load()
    cells = [
        (5, 5), (6, 5), (7, 5), (8, 6), (9, 6),
        (4, 6), (5, 6), (6, 6), (7, 6), (8, 7),
        (4, 7), (5, 7), (6, 7), (7, 7), (9, 7),
        (5, 8), (6, 8), (7, 8), (8, 8),
        (6, 9), (7, 9),
    ]
    for x, y in cells:
        base = noisy(dull, rng, 14)
        if rng.random() < 0.2:
            base = shade(color, 1.4)
        px[x, y] = (*base, 255)
    return img


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    for ore_id, color_hex, host in ORES:
        color = hex_to_rgb(color_hex)
        host_base = HOST_BASE[host]
        rng = random.Random(ore_id)

        ore_block(color, host_base, rng).save(BLOCK_DIR / f"{ore_id}_ore.png")
        metal_block(color, rng).save(BLOCK_DIR / f"{ore_id}_block.png")
        raw_block(color, rng).save(BLOCK_DIR / f"raw_{ore_id}_block.png")
        ingot(color, rng).save(ITEM_DIR / f"{ore_id}_ingot.png")
        nugget(color, rng).save(ITEM_DIR / f"{ore_id}_nugget.png")
        raw_item(color, rng).save(ITEM_DIR / f"raw_{ore_id}.png")
        print(f"wrote {ore_id} (6 sprites)")


if __name__ == "__main__":
    main()
