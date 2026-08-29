#!/usr/bin/env python3
"""Regenerates Forgeweave's composite pattern-item textures (issue #43; M3 parts added by #151).

Each part pattern's icon is the shared blank-pattern base with that part's silhouette darkened
onto it -- "etched" imprint art, replacing the old two-layer (pattern + faint greyscale overlay)
item model. This is a Python port of upstream 1.12's runtime compositing math in
`library/client/texture/PatternTexture.java` (NOTICE.md), run once here and committed as static
PNGs instead of composited at runtime (Forgeweave has no dynamic-texture system).

Issue #337: upstream also offsets each part's silhouette before compositing it onto the pattern,
via the `"offset": {x, y}` each part model (`models/item/parts/*.tmat.json`) carries and
`CustomTextureCreator.java` (~215-240) feeds into `PatternTexture#setOffset`. Without it, parts
drawn off-center in their own 16x16 canvas (kama_head, hammer_head, pickaxe_head, shovel_head,
others) produced off-center imprints. `PatternTexture.java` applies the offset by looking up the
part pixel for output position (x, y) at (x - offsetX, y - offsetY); `composite()` below does the
same, and -- since that's the *only* lookup into the part's alpha this script does per output
pixel -- applies it uniformly to both the primary opacity test and the edge-neighbor test (upstream
mixes offset and un-offset coordinates between those two checks, an apparent quirk of the original
that only softens edge shading slightly; not worth reproducing here).

Usage: python3 scripts/generate_pattern_textures.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins (for the
large plate's dedicated pattern art only -- every other input is already a Forgeweave-committed
derived texture).
"""
import shutil
from pathlib import Path

from PIL import Image

from sprite_sets import legacy_input, save_legacy_if_different

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12"
TEXTURE_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"
PATTERN_BASE = TEXTURE_DIR / "pattern.png"

# Issue #796: since a Forged sprite replaced it, the shared blank pattern the Legacy pack's part
# composites below are built from. Falls back to PATTERN_BASE itself once a Forged pattern.png has
# no Legacy override left to give -- see legacy_input's docstring.
LEGACY_SUBDIR = "derived/item"

# (part silhouette texture, composite output texture, source directory, (offsetX, offsetY))
#
# Offsets are ported verbatim from the upstream 1.12 clone's `models/item/parts/*.tmat.json`
# "offset" fields (pinned commit c01173c0408352c50a2e8c5017552323ce42f5b4). Parts with no
# "offset" field upstream (or no tmat file at all -- plain part silhouettes) keep (0, 0).
PARTS = [
    ("pickaxe_head.png", "pattern_pickaxe_head.png", TEXTURE_DIR, (-2, 2)),  # pick_head.tmat.json
    ("shovel_head.png", "pattern_shovel_head.png", TEXTURE_DIR, (-3, 3)),
    ("axe_head.png", "pattern_axe_head.png", TEXTURE_DIR, (-2, 4)),
    ("tool_binding.png", "pattern_tool_binding.png", TEXTURE_DIR, (0, 0)),  # binding.tmat.json: no offset
    ("tool_handle.png", "pattern_tool_handle.png", TEXTURE_DIR, (0, 0)),  # tool_rod.tmat.json: no offset
    # M3 roster (issue #151) -- all derived parts live in TEXTURE_DIR already (copied straight
    # from the 1.12 clone).
    ("sword_blade.png", "pattern_sword_blade.png", TEXTURE_DIR, (0, 0)),  # no offset
    ("wide_guard.png", "pattern_wide_guard.png", TEXTURE_DIR, (0, 0)),  # no offset
    # hand_guard/cross_guard.tmat.json's texture references are crossed upstream (hand_guard.tmat
    # points at the longsword's guard art, cross_guard.tmat at the rapier's), but the offsets
    # below are read from the tmat file matching each *part slot name*, which is what matters here.
    ("hand_guard.png", "pattern_hand_guard.png", TEXTURE_DIR, (4, -4)),
    ("cross_guard.png", "pattern_cross_guard.png", TEXTURE_DIR, (3, -3)),
    ("sign_plate.png", "pattern_sign_plate.png", TEXTURE_DIR, (0, 2)),  # sign_head.tmat.json
    ("pan.png", "pattern_pan.png", TEXTURE_DIR, (-2, 2)),  # pan_head.tmat.json
    ("knife_blade.png", "pattern_knife_blade.png", TEXTURE_DIR, (0, 0)),  # no offset
    ("large_sword_blade.png", "pattern_large_sword_blade.png", TEXTURE_DIR, (-1, 1)),
    ("tough_tool_rod.png", "pattern_tough_tool_rod.png", TEXTURE_DIR, (0, 0)),  # no offset
    ("tough_binding.png", "pattern_tough_binding.png", TEXTURE_DIR, (0, 0)),  # no offset
    # large_plate.png is NOT in this table: issue #337 -- upstream ships a dedicated hand-drawn
    # `pattern_large_plate.png` (the creeper-face art) instead of compositing one at runtime
    # (`CustomTextureCreator.java` ~219-223 prefers dedicated pattern art when it exists). main()
    # copies that file verbatim below rather than generating it here.
    ("hammer_head.png", "pattern_hammer_head.png", TEXTURE_DIR, (-3, 3)),
    ("excavator_head.png", "pattern_excavator_head.png", TEXTURE_DIR, (-3, 3)),
    ("scythe_head.png", "pattern_scythe_head.png", TEXTURE_DIR, (-2, 5)),
    ("kama_head.png", "pattern_kama_head.png", TEXTURE_DIR, (-2, 4)),
    ("broad_axe_head.png", "pattern_broad_axe_head.png", TEXTURE_DIR, (-3, 4)),
    # The four parts below have no upstream .tmat.json (freshly authored or reshaped art, #151 /
    # #198 / #159 / #279), so their offsets are hand-chosen here to center each part's silhouette
    # bounding box on the 16x16 canvas (verified against `<part>.png`'s actual alpha bbox) rather
    # than ported from an upstream file that doesn't exist.
    # vein_hammer_head: bbox center (10.5, 5.5) -> matches the hammer/excavator family's (-3, 3).
    ("vein_hammer_head.png", "pattern_vein_hammer_head.png", TEXTURE_DIR, (-3, 3)),
    # war_mace_head: bbox center (11.0, 5.0) -> (-3, 3) centers it exactly, same hammer lineage
    # (scripts/derive_warmace_art.py derives it from the hammer head).
    ("war_mace_head.png", "pattern_war_mace_head.png", TEXTURE_DIR, (-3, 3)),
    # curved_blade: Spartan Weaponry's saber blade since #375 (scripts/derive_spartan_blade_art.py),
    # replacing the 1.12 cutlass blade #279 had put here. Both were drawn in the tool position, up
    # in the canvas's top-right corner, and both center the same: bbox (4, 1)-(15, 9), center
    # (10.0, 5.5) -> (-2, 2). The offset is unchanged from #279 even though the art is not.
    ("curved_blade.png", "pattern_curved_blade.png", TEXTURE_DIR, (-2, 2)),
    # katana_blade: Spartan Weaponry's katana blade since #375, replacing #279's authored one, and
    # so derived art in TEXTURE_DIR now rather than the lone authored part base it used to be. It
    # sits one row lower and one column right of the blade it replaces: bbox (5, 1)-(15, 9), center
    # (10.5, 5.5) -> (-2, 2), where the authored blade's (9.5, 4.5) wanted (-2, 4).
    ("katana_blade.png", "pattern_katana_blade.png", TEXTURE_DIR, (-2, 2)),
    # Issue #271: the sharpening kit, the one part that belongs to no tool (upstream's SharpeningKit
    # is registered as a tool part but never appears in a ToolCore's required components). Its
    # sharpening_kit.tmat.json carries no "offset" field upstream, so (0, 0), and the art needs none:
    # it fills the canvas edge to edge (alpha bbox (0, 1)-(16, 14), center (7.5, 7.0)).
    ("sharpening_kit.png", "pattern_sharpening_kit.png", TEXTURE_DIR, (0, 0)),
    # Issue #605: the shard, upstream's other stencil-crafted "part that belongs to no tool"
    # (TinkerTools#registerItems registers it on the line right after the sharpening kit's).
    # shard.tmat.json carries no "offset" field upstream, so (0, 0).
    ("shard.png", "pattern_shard.png", TEXTURE_DIR, (0, 0)),
    # M3.5 (issue #393). bow_limb.tmat.json carries the largest offset in upstream's whole part
    # table -- its art is the shortbow's bottom limb, drawn in the bow's own corner of the canvas.
    ("bow_limb.png", "pattern_bow_limb.png", TEXTURE_DIR, (4, -2)),
    ("bow_string.png", "pattern_bow_string.png", TEXTURE_DIR, (0, 0)),  # bow_string.tmat.json: no offset
    # #626 (parity audit T17): the arrow's three parts. None of their tmat files carries an
    # "offset" field upstream (arrow_head/arrow_shaft/fletching.tmat.json are all a bare layer0),
    # so all three composite at (0, 0). arrow_shaft's layer0 is items/arrow/shaft.png -- the
    # assembled arrow's shaft layer, the same tool-layer reuse bow_limb documents above.
    ("arrow_head.png", "pattern_arrow_head.png", TEXTURE_DIR, (0, 0)),
    ("arrow_shaft.png", "pattern_arrow_shaft.png", TEXTURE_DIR, (0, 0)),
    ("fletching.png", "pattern_fletching.png", TEXTURE_DIR, (0, 0)),
    # #677 (M4-2): the armor parts, from the 1.20 clone (scripts/derive_armor_part_art.py), which has
    # no tmat offsets at all (its patterns are flat GUI icons). Offsets hand-chosen to center each
    # silhouette's alpha bbox on the canvas, the vein_hammer_head precedent above.
    ("plating_helmet.png", "pattern_plating_helmet.png", TEXTURE_DIR, (0, 2)),  # bbox center (8.0, 6.0)
    ("plating_chestplate.png", "pattern_plating_chestplate.png", TEXTURE_DIR, (0, 0)),  # (8.0, 7.5)
    ("plating_leggings.png", "pattern_plating_leggings.png", TEXTURE_DIR, (0, 1)),  # (8.0, 6.5)
    ("plating_boots.png", "pattern_plating_boots.png", TEXTURE_DIR, (0, 0)),  # (8.0, 8.0)
    ("maille.png", "pattern_maille.png", TEXTURE_DIR, (0, 0)),  # (8.0, 8.5)
]

# Upstream ships hand-drawn pattern art for the large plate instead of compositing one; copied
# verbatim by main() below. See PARTS' large_plate comment and NOTICE.md.
LARGE_PLATE_PATTERN_SOURCE = (
    UPSTREAM / "resources/assets/tconstruct/textures/items/pattern_large_plate.png"
)
LARGE_PLATE_PATTERN_OUTPUT = TEXTURE_DIR / "pattern_large_plate.png"

ALPHA_THRESHOLD = 64
EDGE_MULT = 0.6
INTERIOR_MULT = 0.5


def composite(pattern: Image.Image, part: Image.Image, offset: tuple[int, int]) -> Image.Image:
    width, height = pattern.size
    offset_x, offset_y = offset
    pattern_px = pattern.load()
    part_px = part.load()
    out = Image.new("RGBA", (width, height))
    out_px = out.load()

    border_x = width // 8
    border_y = height // 8

    def part_alpha(x: int, y: int) -> int:
        # Upstream's PatternTexture#colorPixel: part pixel for output (x, y) is looked up at
        # (x - offsetX, y - offsetY).
        x -= offset_x
        y -= offset_y
        if 0 <= x < width and 0 <= y < height:
            return part_px[x, y][3]
        return 0

    for y in range(height):
        for x in range(width):
            r, g, b, a = pattern_px[x, y]
            if a == 0:
                out_px[x, y] = (r, g, b, a)
                continue

            if x < border_x or x > width - border_x or y < border_y or y > height - border_y:
                out_px[x, y] = (r, g, b, a)
                continue

            if part_alpha(x, y) < ALPHA_THRESHOLD:
                out_px[x, y] = (r, g, b, a)
                continue

            edge = (
                part_alpha(x - 1, y) < ALPHA_THRESHOLD
                or part_alpha(x, y + 1) < ALPHA_THRESHOLD
                or part_alpha(x + 1, y) < ALPHA_THRESHOLD
                or part_alpha(x, y - 1) < ALPHA_THRESHOLD
            )
            mult = EDGE_MULT if edge else INTERIOR_MULT
            out_px[x, y] = (
                min(255, int(r * mult)),
                min(255, int(g * mult)),
                min(255, int(b * mult)),
                255,
            )

    return out


def main() -> None:
    pattern = Image.open(PATTERN_BASE).convert("RGBA")
    for part_name, output_name, part_dir, offset in PARTS:
        part = Image.open(part_dir / part_name).convert("RGBA")
        composite(pattern, part, offset).save(TEXTURE_DIR / output_name)
        print(f"wrote {output_name}")

    shutil.copyfile(LARGE_PLATE_PATTERN_SOURCE, LARGE_PLATE_PATTERN_OUTPUT)
    print(f"wrote {LARGE_PLATE_PATTERN_OUTPUT.name} (byte-for-byte copy, not composited)")

    # Issue #796: the Legacy pack's pass over the same table, reading each input through
    # legacy_input (the Legacy pack's own override if it has one, else the Forged/default file the
    # first loop above just used) and only actually writing a Legacy file where the result differs
    # from what that first loop produced. See scripts/sprite_sets.py's module docstring.
    legacy_pattern = Image.open(legacy_input(LEGACY_SUBDIR, "pattern.png")).convert("RGBA")
    for part_name, output_name, _part_dir, offset in PARTS:
        legacy_part = Image.open(legacy_input(LEGACY_SUBDIR, part_name)).convert("RGBA")
        legacy_composite = composite(legacy_pattern, legacy_part, offset)
        save_legacy_if_different(legacy_composite, LEGACY_SUBDIR, output_name)
    large_plate_pattern = Image.open(LARGE_PLATE_PATTERN_SOURCE).convert("RGBA")
    save_legacy_if_different(large_plate_pattern, LEGACY_SUBDIR, LARGE_PLATE_PATTERN_OUTPUT.name)


if __name__ == "__main__":
    main()
