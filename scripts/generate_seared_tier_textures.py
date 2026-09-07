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
pane) and replaces hue and saturation with the tier's: Nether from the hue #143's hand-tinted
`nether_core_side.png` used, End and Deep from the fluids their pour-to-transform recipes consume,
the same numbers the retired `generate_end_deep_core_textures.py` used. A pixel that already has
colour (the fire in the core's lit front) is left alone. Seared textures are pure greyscale, which
is why `recolor_raw_ore.py`'s saturation *ratio* cannot be reused here (a ratio over zero
saturation is undefined).

The cores come out of the same pass (maintainer request, 2026-09-06: the core and its walls must
match exactly): `<tier>_core_side.png` is `seared_bricks.png` tinted, so it is pixel for pixel
`seared_bricks_<tier>.png`, and `<tier>_core_front_active/inactive.png` are the Standard Core's own
fronts tinted.

Since the designer's own tier art landed (2026-09-06/07, the `searednether`, `searedend` and
`seareddeep` batches) every tier works the same way: `seared_bricks_<tier>.png`, `<tier>_core_side.png`
(the same sprite), the two core fronts, the Nether Core's hot "v2" lit front and the Nether tank's
side and top are hand-drawn Forged files this script never touches (`HAND_DRAWN`). Every other wall
texture of a tier is derived *from that tier's brick* rather than tinted: `palette()` maps each
upstream grey to the designer's colours by structure -- the mortar line (upstream's grey 38 and
darker) takes the designer's mortar colours (the Nether's glowing orange, the End's purple, the
Deep's dark teal), and the brick body follows the designer's body ramp by luminance (dark purple,
pale yellow, grey stone) -- so glass, tank, drain and the rest wear the designer's palette with
upstream's shapes. `tint()` stays for a tier that has no designer brick yet (none today).

Output: `derived/block/<base>_<tier>.png` for every base below and every tier. NOTICE.md carries a
row per generated output, citing the base's own upstream source; the hand-drawn files are original
art and carry none.

Usage: python3 scripts/generate_seared_tier_textures.py
Requires Pillow (`pip install pillow`).
"""
import colorsys
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived/block"

# tier -> (hue in degrees, saturation), for a tier with no designer brick yet (none today)
TIERS = {}

# tier -> (mortar colours darkest to brightest, body colours darkest to brightest), read off the
# designer's seared_bricks_<tier>.png. The Nether's mortar glows brighter than its body; the End's
# mortar is the dark purple between pale yellow bricks.
PALETTES = {
    "nether": (
        [(197, 63, 8), (206, 84, 15), (213, 108, 26)],
        [(38, 16, 20), (38, 18, 23), (41, 21, 25), (46, 23, 27), (48, 24, 28),
         (56, 24, 30), (50, 28, 36), (59, 27, 32), (62, 30, 36), (68, 36, 42)]),
    "end": (
        [(154, 53, 115), (170, 86, 126), (178, 119, 150), (198, 155, 182)],
        [(214, 214, 149), (223, 203, 217), (221, 228, 165), (235, 228, 230),
         (232, 244, 178), (236, 251, 175), (238, 246, 201)]),
    # The Deep's cyan sculk dots are decoration on the brick face, not part of the ramp.
    "deep": (
        [(13, 18, 23), (17, 27, 33), (5, 42, 50)],
        [(56, 55, 55), (65, 65, 65), (75, 76, 79), (88, 88, 88), (110, 110, 110)]),
}
# Upstream's seared brick: mortar is grey 38 (a few cracks at 28/36), the body runs 46..161.
MORTAR_MAX = 38
BODY_RANGE = (46, 161)

# Hand-drawn Forged files under derived/block/ that this script must never overwrite.
HAND_DRAWN = {"seared_bricks_nether", "nether_core_side", "nether_core_front_active",
              "nether_core_front_inactive", "nether_core_v2_front_active",
              "seared_bricks_end", "end_core_side", "end_core_front_active", "end_core_front_inactive",
              "seared_bricks_deep", "deep_core_side", "deep_core_front_active", "deep_core_front_inactive",
              "seared_tank_side_nether", "seared_tank_top_nether"}

# Every texture a tiered wall block's model binds; see ForgeweaveBlockStateProvider's tiered* helpers.
BASES = [
    "seared_stone", "seared_cobblestone", "seared_paver", "seared_bricks", "seared_cracked_bricks",
    "seared_fancy_bricks", "seared_square_bricks", "seared_triangle_bricks", "seared_small_bricks",
    "seared_road", "seared_tile", "seared_creeper", "seared_glass",
    "seared_tank_side", "seared_tank_top", "seared_gauge_side", "seared_window_side", "seared_window_top",
    "seared_drain_front", "seared_drain_back", "seared_duct_front", "seared_chute_side", "seared_chute_top",
]


# A pixel at or above this saturation is already coloured (the lit core's fire) and is kept as is.
COLOURED = 0.15


def palette_colour(grey: int, mortar: list, body: list) -> tuple[int, int, int]:
    """The designer's colour for an upstream grey value: a mortar colour for the mortar line (the
    darkest greys map to the darkest mortar colour), the body ramp by luminance for everything else."""
    if grey <= MORTAR_MAX:
        return mortar[min(len(mortar) - 1, max(0, round((grey - 28) / (MORTAR_MAX - 28) * (len(mortar) - 1))))]
    low, high = BODY_RANGE
    return body[round((min(max(grey, low), high) - low) / (high - low) * (len(body) - 1))]


def palette(src: Path, out: Path, mortar: list, body: list) -> None:
    im = Image.open(src).convert("RGBA")
    px = im.load()
    result = Image.new("RGBA", im.size)
    out_px = result.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                out_px[x, y] = (0, 0, 0, 0)
            elif r == g == b:
                out_px[x, y] = palette_colour(r, mortar, body) + (a,)
            else:
                out_px[x, y] = (r, g, b, a)  # already coloured (the lit core's fire): kept
    result.save(out)
    print(f"wrote {out.relative_to(ASSETS.parents[5])}")


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
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s >= COLOURED:
                out_px[x, y] = (r, g, b, a)
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(hue_deg / 360, sat, v)
            out_px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    result.save(out)
    print(f"wrote {out.relative_to(ASSETS.parents[5])}")


def main() -> None:
    for base in BASES:
        for tier, (mortar, body) in PALETTES.items():
            if f"{base}_{tier}" not in HAND_DRAWN:
                palette(ASSETS / f"{base}.png", ASSETS / f"{base}_{tier}.png", mortar, body)
        for tier, (hue, sat) in TIERS.items():
            tint(ASSETS / f"{base}.png", ASSETS / f"{base}_{tier}.png", hue, sat)
    for tier, (hue, sat) in TIERS.items():
        tint(ASSETS / "seared_bricks.png", ASSETS / f"{tier}_core_side.png", hue, sat)
        for face in ("front_active", "front_inactive"):
            tint(ASSETS / f"standard_core_{face}.png", ASSETS / f"{tier}_core_{face}.png", hue, sat)


if __name__ == "__main__":
    main()
