#!/usr/bin/env python3
"""Regenerates Forgeweave's composite pattern-item textures (issue #43; M3 parts added by #151).

Each part pattern's icon is the shared blank-pattern base with that part's silhouette darkened
onto it -- "etched" imprint art, replacing the old two-layer (pattern + faint greyscale overlay)
item model. This is a Python port of upstream 1.12's runtime compositing math in
`library/client/texture/PatternTexture.java` (NOTICE.md), run once here and committed as static
PNGs instead of composited at runtime (Forgeweave has no dynamic-texture system). The algorithm
applies just the same to #151's freshly-authored `vein_hammer_head.png` (read from the standard
item texture folder, not `derived/`) -- the *base* part has no upstream counterpart, but this
composite still runs the derived algorithm over the derived `pattern.png` base, so
`pattern_vein_hammer_head.png` still gets a NOTICE.md row like every other pattern here.

Usage: python3 scripts/generate_pattern_textures.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURE_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"
ORIGINAL_ITEM_DIR = ROOT / "src/main/resources/assets/forgeweave/textures/item"
PATTERN_BASE = TEXTURE_DIR / "pattern.png"

# (part silhouette texture, composite output texture, source directory for the part texture)
PARTS = [
    ("pickaxe_head.png", "pattern_pickaxe_head.png", TEXTURE_DIR),
    ("shovel_head.png", "pattern_shovel_head.png", TEXTURE_DIR),
    ("axe_head.png", "pattern_axe_head.png", TEXTURE_DIR),
    ("tool_binding.png", "pattern_tool_binding.png", TEXTURE_DIR),
    ("tool_handle.png", "pattern_tool_handle.png", TEXTURE_DIR),
    # M3 roster (issue #151) -- all derived parts live in TEXTURE_DIR already (copied straight
    # from the 1.12 clone); vein_hammer_head is the one part with no upstream art, so its base
    # texture lives in ORIGINAL_ITEM_DIR instead.
    ("sword_blade.png", "pattern_sword_blade.png", TEXTURE_DIR),
    ("wide_guard.png", "pattern_wide_guard.png", TEXTURE_DIR),
    ("hand_guard.png", "pattern_hand_guard.png", TEXTURE_DIR),
    ("cross_guard.png", "pattern_cross_guard.png", TEXTURE_DIR),
    ("sign_plate.png", "pattern_sign_plate.png", TEXTURE_DIR),
    ("pan.png", "pattern_pan.png", TEXTURE_DIR),
    ("knife_blade.png", "pattern_knife_blade.png", TEXTURE_DIR),
    ("large_sword_blade.png", "pattern_large_sword_blade.png", TEXTURE_DIR),
    ("tough_tool_rod.png", "pattern_tough_tool_rod.png", TEXTURE_DIR),
    ("tough_binding.png", "pattern_tough_binding.png", TEXTURE_DIR),
    ("large_plate.png", "pattern_large_plate.png", TEXTURE_DIR),
    ("hammer_head.png", "pattern_hammer_head.png", TEXTURE_DIR),
    ("excavator_head.png", "pattern_excavator_head.png", TEXTURE_DIR),
    ("scythe_head.png", "pattern_scythe_head.png", TEXTURE_DIR),
    ("kama_head.png", "pattern_kama_head.png", TEXTURE_DIR),
    ("broad_axe_head.png", "pattern_broad_axe_head.png", TEXTURE_DIR),
    ("vein_hammer_head.png", "pattern_vein_hammer_head.png", ORIGINAL_ITEM_DIR),
    # M3 issue #161: the warmace's head, itself derived from the clone's hammer head
    # (scripts/derive_warmace_art.py), so it lives in TEXTURE_DIR like every other derived part.
    ("war_mace_head.png", "pattern_war_mace_head.png", TEXTURE_DIR),
]

ALPHA_THRESHOLD = 64
EDGE_MULT = 0.6
INTERIOR_MULT = 0.5


def composite(pattern: Image.Image, part: Image.Image) -> Image.Image:
    width, height = pattern.size
    pattern_px = pattern.load()
    part_px = part.load()
    out = Image.new("RGBA", (width, height))
    out_px = out.load()

    border_x = width // 8
    border_y = height // 8

    def part_alpha(x: int, y: int) -> int:
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
    for part_name, output_name, part_dir in PARTS:
        part = Image.open(part_dir / part_name).convert("RGBA")
        composite(pattern, part).save(TEXTURE_DIR / output_name)
        print(f"wrote {output_name}")


if __name__ == "__main__":
    main()
