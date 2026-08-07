#!/usr/bin/env python3
"""Regenerates Forgeweave's five composite pattern-item textures (issue #43).

Each part pattern's icon is the shared blank-pattern base with that part's silhouette darkened
onto it -- "etched" imprint art, replacing the old two-layer (pattern + faint greyscale overlay)
item model. This is a Python port of upstream 1.12's runtime compositing math in
`library/client/texture/PatternTexture.java` (NOTICE.md), run once here and committed as static
PNGs instead of composited at runtime (Forgeweave has no dynamic-texture system).

Usage: python3 scripts/generate_pattern_textures.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

TEXTURE_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures/derived/item"
PATTERN_BASE = TEXTURE_DIR / "pattern.png"

# (part silhouette texture, composite output texture)
PARTS = [
    ("pickaxe_head.png", "pattern_pickaxe_head.png"),
    ("shovel_head.png", "pattern_shovel_head.png"),
    ("axe_head.png", "pattern_axe_head.png"),
    ("tool_binding.png", "pattern_tool_binding.png"),
    ("tool_handle.png", "pattern_tool_handle.png"),
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
    for part_name, output_name in PARTS:
        part = Image.open(TEXTURE_DIR / part_name).convert("RGBA")
        composite(pattern, part).save(TEXTURE_DIR / output_name)
        print(f"wrote {output_name}")


if __name__ == "__main__":
    main()
