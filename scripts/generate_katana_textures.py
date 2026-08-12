"""Generates the katana's four original 16x16 textures (issue #160).

The katana is one of the three "new modern-era shapes, ours" docs/SCOPE.md M3 lists (with the
scimitar and the warmace): neither the 1.12 clone nor the 1.20 branch has a katana, so there is no
upstream art to derive and none of these four files gets a NOTICE.md row. They are freshly authored
here, in the same plain greyscale ramp the derived part/tool sprites use, so the existing
material-tint machinery (`ForgeweaveItemColors`) colours them exactly like every other part.

Four files, all under the standard (non-`derived/`) texture folders per CLAUDE.md:

  item/katana_blade.png     the loose part sprite -- a long, single-edged, gently curved blade,
                            deliberately a different silhouette from the derived `sword_blade.png`
                            (which is a short symmetric double-edged diamond profile)
  item/katana_head.png      the same blade, positioned for the assembled tool (layer1)
  item/katana_handle.png    the wrapped grip/tsuka, lower-left (layer0)
  item/katana_binding.png   the small round guard/tsuba across the blade's base (layer2)

Layer order matches the other tools' models (`ForgeweaveItemModelProvider#toolModel`):
layer0 = handle, layer1 = head, layer2 = binding -- so the guard draws last, over the blade base,
which is where a tsuba physically sits.

Usage: python3 scripts/generate_katana_textures.py
Requires Pillow (`pip install pillow`).
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/forgeweave/textures"
# The part sprite is an inventory item like any other part; the three tool layers are assembled-tool
# art, so they live beside the other freshly-authored tool layers under `tools/` (ToolArt
# #ORIGINAL_ART, and the `tools/` atlas source in assets/minecraft/atlases/blocks.json).
PART_DIR = ASSETS / "item"
TOOL_DIR = ASSETS / "tools"

TRANSPARENT = (0, 0, 0, 0)
# The part sprites' ramp (see derived/item/sword_blade.png): one outline value, one lit edge, one
# shaded body. The tool-layer sprites use the slightly lighter outline the derived tool layers do
# (derived/tools/pickaxe_*.png), so an assembled katana reads at the same weight as a pickaxe.
PART_OUTLINE = (68, 68, 68, 255)
TOOL_OUTLINE = (79, 79, 79, 255)
EDGE = (251, 251, 251, 255)
BODY = (216, 216, 216, 255)
SHADE = (196, 196, 196, 255)

# A single-edged blade, four columns wide: `#` outline, `e` lit cutting edge, `b` body, `s` shaded
# spine. Only the cutting-edge side carries an outline -- the other side is the shade value, the same
# trick the derived tool layers use (derived/tools/pickaxe_handle.png's `acd`). Outlining both sides
# of a 4px diagonal leaves it half black, which reads as a dark bar once the item model extrudes it
# and the first-person view fills the frame with it.
BLADE_PART = [
    "................",
    "...........###..",
    "..........#ebs..",
    "..........#ebs..",
    ".........#ebs...",
    "........#ebs....",
    ".......#ebs.....",
    "......#ebs......",
    ".....#ebs.......",
    "....#ebs........",
    "....#ebs........",
    "...#ebs.........",
    "...#bss.........",
    "....##..........",
    "................",
    "................",
]

# The same blade shifted up-right for the assembled tool, where the grip owns the lower-left corner.
BLADE_LAYER = [
    "..............#.",
    ".............#e#",
    "............#ebs",
    "...........#ebs.",
    "..........#ebs..",
    ".........#ebs...",
    ".........#ebs...",
    "........#ebs....",
    ".......#ebs.....",
    ".......##.......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

HANDLE_LAYER = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "........##......",
    ".......#ebs.....",
    "......#ebs......",
    ".....#ebs.......",
    "....#ebs........",
    "...#ebs.........",
    "...#bbs.........",
    "...####.........",
    "................",
]

# The tsuba: a short bar running down-right, i.e. square across the blade's own up-right axis, and
# crossing it exactly at the handle/blade seam. Symmetric about that crossing so it reads as a guard
# rather than as a spur off one side, and -- drawn last of the three layers -- it covers the seam.
BINDING_LAYER = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "....###.........",
    "....#ebs........",
    ".....#ebs.......",
    "......#ebs......",
    ".......#ebs.....",
    "........###.....",
    "................",
    "................",
    "................",
    "................",
    "................",
]

FILES = [
    ("katana_blade.png", BLADE_PART, PART_OUTLINE),
    ("katana_head.png", BLADE_LAYER, TOOL_OUTLINE),
    ("katana_handle.png", HANDLE_LAYER, TOOL_OUTLINE),
    ("katana_binding.png", BINDING_LAYER, TOOL_OUTLINE),
]


def write(name: str, rows: list[str], outline: tuple[int, int, int, int]) -> None:
    palette = {".": TRANSPARENT, "#": outline, "e": EDGE, "b": BODY, "s": SHADE}
    image = Image.new("RGBA", (16, 16), TRANSPARENT)
    pixels = image.load()
    for y, row in enumerate(rows):
        if len(row) != 16:
            raise ValueError(f"{name} row {y} is {len(row)} px wide, expected 16")
        for x, char in enumerate(row):
            pixels[x, y] = palette[char]
    directory = PART_DIR if name == "katana_blade.png" else TOOL_DIR
    directory.mkdir(parents=True, exist_ok=True)
    image.save(directory / name)
    print(f"wrote {directory / name}")


def main() -> None:
    if len(FILES) != 4:
        raise ValueError("expected the part sprite plus the three tool layers")
    for name, rows, outline in FILES:
        write(name, rows, outline)


if __name__ == "__main__":
    main()
