#!/usr/bin/env python3
"""Regenerates the clay counterpart of every cast item texture (issue #292).

Upstream 1.12 ships no clay-cast sprite at all: `SmelteryClientEvents#registerModels` bakes the very
same composited cast model a second time and multiplies every quad by the colour `0xa77498`
(`ToolClientEvents#replacePatternModel`'s `color` argument, `ModelHelper.colorQuad`), so a clay cast
is literally the gold cast rendered through a mauve tint. Forgeweave has no runtime model tinting for
items, so the multiply is baked into a static PNG here -- the same offline-instead-of-at-load-time
treatment `generate_cast_textures.py` already gives upstream's `CastTexture` compositing.

Reads every `cast_*.png` in the derived texture folder (all of them are upstream-derived, NOTICE.md)
and writes `clay_<name>.png` beside it.

Usage: python3 scripts/generate_clay_cast_textures.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

from sprite_sets import legacy_input, save_legacy_if_different

TEXTURE_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived/item"
LEGACY_SUBDIR = "derived/item"  # issue #796, see scripts/sprite_sets.py

# Upstream's clay-cast quad colour (SmelteryClientEvents#registerModels).
CLAY_TINT = (0xA7, 0x74, 0x98)


def tint(cast: Image.Image) -> Image.Image:
    width, height = cast.size
    src = cast.load()
    out = Image.new("RGBA", (width, height))
    dst = out.load()

    for y in range(height):
        for x in range(width):
            r, g, b, a = src[x, y]
            dst[x, y] = (r * CLAY_TINT[0] // 255, g * CLAY_TINT[1] // 255, b * CLAY_TINT[2] // 255, a)

    return out


def main() -> None:
    names = [cast.name for cast in sorted(TEXTURE_DIR.glob("cast_*.png"))]
    for name in names:
        output = TEXTURE_DIR / f"clay_{name}"
        tint(Image.open(TEXTURE_DIR / name).convert("RGBA")).save(output)
        print(f"wrote {output.name}")

    # Issue #796: the Legacy pack's pass over the same names, reusing whatever cast_*.png
    # generate_cast_textures.py's own Legacy pass left behind (or the shared Forged one, via
    # legacy_input's fallback) rather than re-deriving the cast/part inputs itself.
    for name in names:
        legacy_cast = Image.open(legacy_input(LEGACY_SUBDIR, name)).convert("RGBA")
        save_legacy_if_different(tint(legacy_cast), LEGACY_SUBDIR, f"clay_{name}")


if __name__ == "__main__":
    main()
