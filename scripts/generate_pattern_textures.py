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

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12"
TEXTURE_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"
PATTERN_BASE = TEXTURE_DIR / "pattern.png"

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
    # #198 / #159 / #160), so their offsets are hand-chosen here to center each part's silhouette
    # bounding box on the 16x16 canvas (verified against `<part>.png`'s actual alpha bbox) rather
    # than ported from an upstream file that doesn't exist.
    # vein_hammer_head: bbox center (10.5, 5.5) -> matches the hammer/excavator family's (-3, 3).
    ("vein_hammer_head.png", "pattern_vein_hammer_head.png", TEXTURE_DIR, (-3, 3)),
    # war_mace_head: bbox center (11.0, 5.0) -> (-3, 3) centers it exactly, same hammer lineage
    # (scripts/derive_warmace_art.py derives it from the hammer head).
    ("war_mace_head.png", "pattern_war_mace_head.png", TEXTURE_DIR, (-3, 3)),
    # curved_blade: bbox is already centered at (8, 8) (reshaped from the already-centered
    # sword_blade.png, scripts/derive_m3_weapon_art.py) -- no offset needed.
    ("curved_blade.png", "pattern_curved_blade.png", TEXTURE_DIR, (0, 0)),
    # katana_blade: same pixels as large_sword_blade.png (scripts/derive_m3_weapon_art.py), but
    # its own bbox is already centered at (8, 8); large_sword_blade's upstream (-1, 1) offset is
    # a real upstream authoring choice for that part, not something katana_blade needs to copy.
    ("katana_blade.png", "pattern_katana_blade.png", TEXTURE_DIR, (0, 0)),
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


if __name__ == "__main__":
    main()
