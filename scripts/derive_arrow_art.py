"""Derives the material arrow's three layers and its broken shaft from the 1.12 clone (issue #653,
parity audit T17).

Upstream's `models/item/tools/arrow.tcon.json` stacks three sprites -- layer0
`items/arrow/shaft.png`, layer1 `items/arrow/head.png`, layer2 `items/arrow/fletching.png`, tinted
per part material at bake time -- and declares `broken0` as `items/arrow/shaft_broken.png` (the one
upstream tool that breaks its shaft rather than its head). Forgeweave draws the same four files
under `ToolArt`'s layer names for `ToolConstants#ARROW` (`shaft`, `head`, `fletching`), so each
output is the upstream file unchanged:

| Output (`derived/tools/`) | Upstream source (`items/arrow/`) |
| --- | --- |
| `arrow_shaft.png` | `shaft.png` |
| `arrow_head.png` | `head.png` |
| `arrow_fletching.png` | `fletching.png` |
| `arrow_shaft_broken.png` | `shaft_broken.png` |

The material-specific variants (`head_cactus`, `head_paper`, `head_contrast`,
`fletching_feather`) are upstream's per-material texture overrides, a render path Forgeweave
replaces with tinting; they are not derived.

Usage: python3 scripts/derive_arrow_art.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12"
UPSTREAM_ARROW = UPSTREAM / "resources/assets/tconstruct/textures/items/arrow"

DERIVED_TOOLS = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools"

OUTPUTS = {
    "arrow_shaft.png": "shaft.png",
    "arrow_head.png": "head.png",
    "arrow_fletching.png": "fletching.png",
    "arrow_shaft_broken.png": "shaft_broken.png",
}

def main() -> None:
    if not UPSTREAM_ARROW.is_dir():
        raise SystemExit(f"1.12 clone not found at {UPSTREAM} -- see CLAUDE.md for how to re-create it")
    DERIVED_TOOLS.mkdir(parents=True, exist_ok=True)
    for output, source in OUTPUTS.items():
        image = Image.open(UPSTREAM_ARROW / source).convert("RGBA")
        path = DERIVED_TOOLS / output
        image.save(path)
        print(f"wrote {path}")

if __name__ == "__main__":
    main()
