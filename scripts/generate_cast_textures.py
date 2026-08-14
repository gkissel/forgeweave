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

ROOT = Path(__file__).resolve().parent.parent
TEXTURE_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"
CAST_BASE = TEXTURE_DIR / "cast.png"

# Every part base is derived art under TEXTURE_DIR. That was not always so: katana_blade was
# freshly authored under `textures/item/` between issues #279 and #375, and this script used to
# fall back to that directory for it. Issue #375 re-sourced the blade from Spartan Weaponry, so the
# fallback had nothing left to find and went with it.

# (part silhouette texture, composite output texture)
PARTS = [
    ("pickaxe_head.png", "cast_pickaxe_head.png"),
    ("shovel_head.png", "cast_shovel_head.png"),
    ("axe_head.png", "cast_axe_head.png"),
    ("tool_binding.png", "cast_tool_binding.png"),
    ("tool_handle.png", "cast_tool_handle.png"),
    # #222 -- the M3 roster (docs/SCOPE.md M3 issue #151/#159/#160/#161), same treatment: each
    # part's own already-derived item texture (NOTICE.md) punched into the blank cast base.
    ("sword_blade.png", "cast_sword_blade.png"),
    ("wide_guard.png", "cast_wide_guard.png"),
    ("hand_guard.png", "cast_hand_guard.png"),
    ("cross_guard.png", "cast_cross_guard.png"),
    ("sign_plate.png", "cast_sign_plate.png"),
    ("pan.png", "cast_pan.png"),
    ("knife_blade.png", "cast_knife_blade.png"),
    ("large_sword_blade.png", "cast_large_sword_blade.png"),
    ("tough_tool_rod.png", "cast_tough_tool_rod.png"),
    ("tough_binding.png", "cast_tough_binding.png"),
    ("large_plate.png", "cast_large_plate.png"),
    ("hammer_head.png", "cast_hammer_head.png"),
    ("excavator_head.png", "cast_excavator_head.png"),
    ("scythe_head.png", "cast_scythe_head.png"),
    ("kama_head.png", "cast_kama_head.png"),
    ("broad_axe_head.png", "cast_broad_axe_head.png"),
    ("vein_hammer_head.png", "cast_vein_hammer_head.png"),
    ("war_mace_head.png", "cast_war_mace_head.png"),
    ("curved_blade.png", "cast_curved_blade.png"),
    ("katana_blade.png", "cast_katana_blade.png"),
    # #271 -- the sharpening kit. Upstream casts it like any other tool part (TinkerSmeltery's
    # registerToolpartMeltingCasting loops every registered IToolPart whose canBeCasted() holds, and
    # SharpeningKit never overrides it), so it gets the same gold cast as the rest.
    ("sharpening_kit.png", "cast_sharpening_kit.png"),
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
