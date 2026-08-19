"""Derives the shuriken's four blade layers from the 1.12 clone (issue #448, parity audit T17).

Upstream's `models/item/tools/shuriken.tcon.json` stacks four quadrant sprites -- layer0
`items/shuriken/shuriken.png` through layer3 `items/shuriken/shuriken4.png`, one blade each, tinted
per blade material at bake time. Forgeweave draws the same four files under `ToolArt`'s layer names
for `ToolConstants#SHURIKEN` (`head`, `head2`, `head3`, `head4` -- four `SHURIKEN_BLADE` slots), so
each output is the upstream file unchanged:

| Output (`derived/tools/`) | Upstream source (`items/shuriken/`) |
| --- | --- |
| `shuriken_head.png` | `shuriken.png` |
| `shuriken_head2.png` | `shuriken2.png` |
| `shuriken_head3.png` | `shuriken3.png` |
| `shuriken_head4.png` | `shuriken4.png` |

No broken variants: upstream's model declares no `broken<N>` key for the shuriken (an empty one
reads "Ammo: Empty" instead), and `ToolArt#BROKEN_LAYERS` accordingly has no shuriken row.

Usage: python3 scripts/derive_shuriken_art.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12"
UPSTREAM_SHURIKEN = UPSTREAM / "resources/assets/tconstruct/textures/items/shuriken"

DERIVED_TOOLS = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools"

OUTPUTS = {
    "shuriken_head.png": "shuriken.png",
    "shuriken_head2.png": "shuriken2.png",
    "shuriken_head3.png": "shuriken3.png",
    "shuriken_head4.png": "shuriken4.png",
}

def main() -> None:
    if not UPSTREAM_SHURIKEN.is_dir():
        raise SystemExit(f"1.12 clone not found at {UPSTREAM} -- see CLAUDE.md for how to re-create it")
    DERIVED_TOOLS.mkdir(parents=True, exist_ok=True)
    for output, source in OUTPUTS.items():
        image = Image.open(UPSTREAM_SHURIKEN / source).convert("RGBA")
        path = DERIVED_TOOLS / output
        image.save(path)
        print(f"wrote {path}")

if __name__ == "__main__":
    main()
