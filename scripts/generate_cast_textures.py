#!/usr/bin/env python3
"""Regenerates Forgeweave's five composite part-cast item textures (issue #140).

Each part cast's icon is the shared blank-cast base (`cast.png`, itself a straight upstream port --
NOTICE.md) with a hole punched through it in that part's silhouette and a darkened bevel ringing the
hole -- a "mold cavity" look matching the ingot and nugget casts (`cast_ingot.png`, `cast_nugget.png`,
also straight upstream ports). This replaces the old two-layer model (opaque gold base + the part's
own grey/white sprite drawn flat on top), which rendered as a yellow square with a white silhouette
floating on it instead of a recessed mold.

This is an approximation of upstream 1.12's runtime compositing in
`library/client/texture/CastTexture.java` (NOTICE.md), which punches the same kind of hole and shades
its rim per-direction from the part's alpha edges; this script uses a simpler uniform bevel rather than
reproducing that per-direction shading exactly. Run once here and committed as static PNGs instead of
composited at runtime (Forgeweave has no dynamic-texture system).

Usage: python3 scripts/generate_cast_textures.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

TEXTURE_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived/item"
CAST_BASE = TEXTURE_DIR / "cast.png"

# (part silhouette texture, composite output texture)
PARTS = [
    ("pickaxe_head.png", "cast_pickaxe_head.png"),
    ("shovel_head.png", "cast_shovel_head.png"),
    ("axe_head.png", "cast_axe_head.png"),
    ("tool_binding.png", "cast_tool_binding.png"),
    ("tool_handle.png", "cast_tool_handle.png"),
]

BEVEL_MULT = 0.78


def composite(cast: Image.Image, part: Image.Image) -> Image.Image:
    width, height = cast.size
    cast_px = cast.load()
    part_px = part.load()

    mask = [[part_px[x, y][3] > 0 for x in range(width)] for y in range(height)]

    def in_hole(x: int, y: int) -> bool:
        return 0 <= x < width and 0 <= y < height and mask[y][x]

    out = Image.new("RGBA", (width, height))
    out_px = out.load()

    for y in range(height):
        for x in range(width):
            if mask[y][x]:
                out_px[x, y] = (0, 0, 0, 0)
                continue

            r, g, b, a = cast_px[x, y]
            bordering_hole = (
                in_hole(x - 1, y) or in_hole(x + 1, y) or in_hole(x, y - 1) or in_hole(x, y + 1)
            )
            if bordering_hole:
                out_px[x, y] = (int(r * BEVEL_MULT), int(g * BEVEL_MULT), int(b * BEVEL_MULT), a)
            else:
                out_px[x, y] = (r, g, b, a)

    return out


def main() -> None:
    cast = Image.open(CAST_BASE).convert("RGBA")
    for part_name, output_name in PARTS:
        part = Image.open(TEXTURE_DIR / part_name).convert("RGBA")
        composite(cast, part).save(TEXTURE_DIR / output_name)
        print(f"wrote {output_name}")


if __name__ == "__main__":
    main()
