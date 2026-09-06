#!/usr/bin/env python3
"""Recolours every seared wall/floor texture into the Nether, End and Deep tiers (SearedTier).

The walls of a smeltery follow its core's tier (maintainer request, 2026-09-05/06): each block a
smeltery wall or floor accepts -- the twelve plain seared blocks, seared glass, the tank, gauge
and window, the drain, duct and chute -- carries a `tier` blockstate, and every tier above
standard needs its own texture for each of those faces. This script makes them all from the
standard-tier files already on disk under `derived/block/`, so a tier texture is the upstream
texture recoloured, never fresh art (CLAUDE.md: new art is the designer's call; this is the same
provisional recolour idiom `generate_end_deep_core_textures.py` uses for the cores themselves).

The recolour keeps each opaque pixel's value (its shading) and alpha (the tank windows, the glass
pane) and replaces hue and saturation with the tier's: Nether from `nether_core_side.png`'s own
median hue and mean saturation (#143's hand-tinted art), End and Deep from the fluids their
pour-to-transform recipes consume, the same numbers `generate_end_deep_core_textures.py` uses.
Seared textures are pure greyscale, which is why `recolor_raw_ore.py`'s saturation *ratio* cannot
be reused here (a ratio over zero saturation is undefined).

Output: `derived/block/<base>_<tier>.png` for every base below and every tier. NOTICE.md carries a
row per output, citing the base's own upstream source.

Usage: python3 scripts/generate_seared_tier_textures.py
Requires Pillow (`pip install pillow`).
"""
import colorsys
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived/block"

# tier -> (hue in degrees, saturation)
TIERS = {
    "nether": (6.7, 0.45),
    "end": (294.2, 0.63),
    "deep": (183.5, 0.65),
}

# Every texture a tiered wall block's model binds; see ForgeweaveBlockStateProvider's tiered* helpers.
BASES = [
    "seared_stone", "seared_cobblestone", "seared_paver", "seared_bricks", "seared_cracked_bricks",
    "seared_fancy_bricks", "seared_square_bricks", "seared_triangle_bricks", "seared_small_bricks",
    "seared_road", "seared_tile", "seared_creeper", "seared_glass",
    "seared_tank_side", "seared_tank_top", "seared_gauge_side", "seared_window_side", "seared_window_top",
    "seared_drain_front", "seared_drain_back", "seared_duct_front", "seared_chute_side", "seared_chute_top",
]


def tint(src: Path, out: Path, hue_deg: float, sat: float) -> None:
    im = Image.open(src).convert("RGBA")
    px = im.load()
    result = Image.new("RGBA", im.size)
    out_px = result.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                out_px[x, y] = (0, 0, 0, 0)
                continue
            _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue_deg / 360, sat, v)
            out_px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    result.save(out)
    print(f"wrote {out.relative_to(ASSETS.parents[5])}")


def main() -> None:
    for base in BASES:
        for tier, (hue, sat) in TIERS.items():
            tint(ASSETS / f"{base}.png", ASSETS / f"{base}_{tier}.png", hue, sat)


if __name__ == "__main__":
    main()
