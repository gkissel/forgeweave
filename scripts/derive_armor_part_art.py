#!/usr/bin/env python3
"""Derives the five armor part sprites from the 1.20 clone (issue #677, M4-2; docs/SCOPE.md D2).

Tinkers' 2 has no armor, so these come from the 1.20.1 branch (pinned de26560d, MIT -- the M4
derivation source by name). Each plating's part sprite is the assembled piece's own plating layer
(`item/tool/armor/plate/<piece>/plating.png`, the file `models/item/plate_<piece>.json` names), the
same tool-layer reuse the bow limb and arrow shaft make; the maille is the dedicated part sprite
`item/tool/parts/maille.png`. All are grayscale bases upstream palettes per material at bake time;
Forgeweave tints them flat like every other PartItem, so only the gray base is copied, not
`maille_metal.png` / the `_broken` variants. Each output is the upstream file unchanged:

| Output (`derived/item/`) | Upstream source (`assets/tconstruct/textures/item/tool/`) |
| --- | --- |
| `plating_helmet.png` | `armor/plate/helmet/plating.png` |
| `plating_chestplate.png` | `armor/plate/chestplate/plating.png` |
| `plating_leggings.png` | `armor/plate/leggings/plating.png` |
| `plating_boots.png` | `armor/plate/boots/plating.png` |
| `maille.png` | `parts/maille.png` |

Usage: python3 scripts/derive_armor_part_art.py, then the pattern/cast/clay-cast generators.
Requires Pillow (`pip install pillow`), and the 1.20 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.20"
UPSTREAM_TOOL = UPSTREAM / "src/main/resources/assets/tconstruct/textures/item/tool"

DERIVED_ITEM = ROOT / "src/main/resources/assets/forgeweave/textures/derived/item"

OUTPUTS = {
    "plating_helmet.png": "armor/plate/helmet/plating.png",
    "plating_chestplate.png": "armor/plate/chestplate/plating.png",
    "plating_leggings.png": "armor/plate/leggings/plating.png",
    "plating_boots.png": "armor/plate/boots/plating.png",
    "maille.png": "parts/maille.png",
}


def main() -> None:
    if not UPSTREAM_TOOL.is_dir():
        raise SystemExit(f"1.20 clone not found at {UPSTREAM} -- see CLAUDE.md for how to re-create it")
    for output, source in OUTPUTS.items():
        path = DERIVED_ITEM / output
        Image.open(UPSTREAM_TOOL / source).convert("RGBA").save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
