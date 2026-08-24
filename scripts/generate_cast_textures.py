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

Issue #628: the large plate is the one part upstream ships DEDICATED hand-drawn cast art for
(`cast_large_plate.png`, carrying a creeper face) instead of relying on the runtime composite --
`CustomTextureCreator.java` skips `CastTexture` for it the same way it does for the pattern (see
`generate_pattern_textures.py`'s `LARGE_PLATE_PATTERN_SOURCE`). Compositing it here like every other
part clobbered the face with a plain punched-plate silhouette; it is copied byte-for-byte instead, the
same treatment as the pattern. A survey of every other `PARTS` entry below against upstream's
`textures/items/` tree found no other dedicated `cast_<part>.png` file -- `cast_gear.png`,
`cast_gem.png`, `cast_ingot.png`, `cast_nugget.png`, `cast_plate.png` also exist upstream, but those
are the generic material-shape casts (already ported straight, NOTICE.md issue #272), not tool-part
casts this script produces.

Usage: python3 scripts/generate_cast_textures.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins (for the large
plate's dedicated cast art only -- every other input is already a Forgeweave-committed derived
texture).
"""
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12"
TEXTURE_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"
CAST_BASE = TEXTURE_DIR / "cast.png"

LARGE_PLATE_CAST_SOURCE = UPSTREAM / "resources/assets/tconstruct/textures/items/cast_large_plate.png"
LARGE_PLATE_CAST_OUTPUT = TEXTURE_DIR / "cast_large_plate.png"

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
    # large_plate.png handled separately below -- upstream ships dedicated cast art for it (#628).
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
    # #393: the bow limb is the only M3.5 part with a cast -- no BOWSTRING material melts, so
    # upstream never registers one for the bow string (see ForgeweaveItems#CAST_BOW_LIMB).
    ("bow_limb.png", "cast_bow_limb.png"),
    # #626: the arrow head casts like any other head part (every castable metal has HEAD stats and
    # the auto-added PROJECTILE stat, so canUseMaterial holds). The shaft and fletching do not --
    # no molten material carries a SHAFT or FLETCHING block, the bow-string situation exactly.
    ("arrow_head.png", "cast_arrow_head.png"),
    # #471/T40: the shard, same treatment as the sharpening kit above -- Shard#canUseMaterial is
    # unconditionally true, so it casts for every castable metal, not just head-stat materials.
    ("shard.png", "cast_shard.png"),
    # #677 (M4-2): the armor parts. The 1.20 clone ships dedicated `item/cast/<piece>_plating.png`
    # art for these; Forgeweave keeps the 1.12-lineage composite every other part cast uses so the
    # cast set stays one style.
    ("plating_helmet.png", "cast_plating_helmet.png"),
    ("plating_chestplate.png", "cast_plating_chestplate.png"),
    ("plating_leggings.png", "cast_plating_leggings.png"),
    ("plating_boots.png", "cast_plating_boots.png"),
    ("maille.png", "cast_maille.png"),
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

    shutil.copyfile(LARGE_PLATE_CAST_SOURCE, LARGE_PLATE_CAST_OUTPUT)
    print(f"wrote {LARGE_PLATE_CAST_OUTPUT.name} (byte-for-byte copy, not composited)")


if __name__ == "__main__":
    main()
