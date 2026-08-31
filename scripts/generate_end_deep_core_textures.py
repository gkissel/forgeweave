#!/usr/bin/env python3
"""Recolors the Nether Core's textures into the End Core and Deep Core (issue #845).

Both new tiers have no upstream equivalent -- SCOPE.md's own open question invented them -- so, same
as #143 did for the Nether Core itself, their art is a hue/saturation shift of the tier below rather
than fresh art (CLAUDE.md: new art for these is the maintainer's designer's call under the Forged
pipeline, not something to improvise here; this recolor is explicitly provisional). Reuses
recolor_raw_ore.py's own hue-shift function: each opaque pixel is rotated to a target hue and its
saturation scaled by the ratio of a target average to the source average, which keeps the source's
shading/highlights intact.

Target hue/sat for each tier is lifted straight from the fluid its pour-to-transform recipe consumes,
so a tier reads as "the fluid that made it": End Core from `forgeweave:molten_dragon_breath`'s tint
(0x9B3DA5 -> hue 294 deg, sat 0.63), Deep Core from `forgeweave:deep_blood`'s tint (0x1B4B4E -> hue
184 deg, sat 0.65). Value is left alone for both -- unlike raw_ardite's override in
recolor_raw_ore.py, the Nether Core's own brick shading is already legible and did not need a
brightness boost to read as a new tier.

Usage: python3 scripts/generate_end_deep_core_textures.py
"""
from pathlib import Path

from recolor_raw_ore import recolor

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived/block"

TIERS = {
    "end_core": (294.2, 0.63),
    "deep_core": (183.5, 0.65),
}

SUFFIXES = ["front_active", "front_inactive", "side"]


def main() -> None:
    for tier, (hue, sat) in TIERS.items():
        for suffix in SUFFIXES:
            src = ASSETS / f"nether_core_{suffix}.png"
            out = ASSETS / f"{tier}_{suffix}.png"
            recolor(src, out, hue, sat, None)


if __name__ == "__main__":
    main()
