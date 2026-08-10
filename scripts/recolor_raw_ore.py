#!/usr/bin/env python3
"""Recolors a vanilla raw-ore-family texture into Forgeweave's raw cobalt/ardite icons (issue #140).

Raw cobalt and raw ardite have no 1.12 counterpart (Tinkers' Construct 2 predates raw ore items), so
per the maintainer's issue #140 directive they are recolors of vanilla textures instead of upstream
derivations: raw cobalt from `minecraft:raw_gold`, raw ardite from `minecraft:netherite_scrap`. Both
source PNGs ship inside the Minecraft client jar, not this repo -- extract them first, e.g. from the
NeoForge client-extra jar already present after a `./gradlew build`:

    unzip -p build/moddev/artifacts/neoforge-*-client-extra-aka-minecraft-resources.jar \\
        assets/minecraft/textures/item/raw_gold.png > /tmp/raw_gold.png

Each opaque pixel is hue-shifted to a target hue and its saturation/value scaled by the ratio of a
target average to the source average, which preserves the source's per-pixel shading/highlights while
recoloring it. Target hue/sat came from this repo's own cobalt_ingot.png / ardite_ingot.png (both
straight upstream ports); target value is only overridden for ardite because netherite_scrap is much
darker than a metal ingot and stayed muddy brown without a brightness boost.

Usage:
    python3 scripts/recolor_raw_ore.py <source.png> <output.png> <hue_deg> <sat> [val]
"""
import colorsys
import sys
from pathlib import Path

from PIL import Image


def recolor(src_path: Path, out_path: Path, hue_deg: float, sat: float, val: float | None) -> None:
    im = Image.open(src_path).convert("RGBA")
    width, height = im.size
    px = im.load()

    sats, vals = [], []
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            sats.append(s)
            vals.append(v)
    sat_ratio = sat / (sum(sats) / len(sats)) if sats else 1.0
    val_ratio = (val / (sum(vals) / len(vals))) if val is not None and vals else 1.0

    target_h = hue_deg / 360.0
    out = Image.new("RGBA", (width, height))
    out_px = out.load()
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if a == 0:
                out_px[x, y] = (0, 0, 0, 0)
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            new_s = min(1.0, max(0.0, s * sat_ratio))
            new_v = min(1.0, max(0.0, v * val_ratio))
            nr, ng, nb = colorsys.hsv_to_rgb(target_h, new_s, new_v)
            out_px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    out.save(out_path)
    print(f"wrote {out_path}")


def main() -> None:
    if len(sys.argv) not in (5, 6):
        print(__doc__)
        sys.exit(1)
    src, out, hue, sat = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4])
    val = float(sys.argv[5]) if len(sys.argv) == 6 else None
    recolor(Path(src), Path(out), hue, sat, val)


if __name__ == "__main__":
    main()
