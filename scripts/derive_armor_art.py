"""Derives the plate armor render art from the 1.20 clone (issue #679, M4-4; docs/SCOPE.md D18).

Tinkers' 2 has no armor, so everything here comes from the 1.20.1 branch (pinned de26560d, MIT --
the M4 derivation source by name). Everything is copied unmodified -- the grayscale bases, one
NOTICE.md row each:

   | Output | Upstream source (`assets/tconstruct/textures/`) |
   | --- | --- |
   | `models/armor/derived/plating_layer_1.png` | `tinker_armor/plate/plating_armor.png` |
   | `models/armor/derived/plating_layer_2.png` | `tinker_armor/plate/plating_leggings.png` |
   | `models/armor/derived/maille_layer_1.png` | `tinker_armor/plate/maille_armor.png` |
   | `models/armor/derived/maille_layer_2.png` | `tinker_armor/plate/maille_leggings.png` |
   | `derived/tools/<piece>_plating.png` | `item/tool/armor/plate/<piece>/plating.png` |
   | `derived/tools/<piece>_plating_broken.png` | `item/tool/armor/plate/<piece>/plating_broken.png` |
   | `derived/tools/<piece>_maille.png` | `item/tool/armor/plate/<piece>/maille.png` |

   Upstream's `MaterialArmorTextureSupplier` bakes those four worn-layer bases per material through
   its sprite palettes at datagen; its `plating_*_metal_contrast.png` / `maille_*_{metal,cloth}.png`
   siblings are per-material-*type* overrides of that path and are not ported -- Forgeweave tints
   the gray base with `Material.color` at render time (`ForgeweaveItemClientExtensions`'s
   `getArmorLayerTintColor`, issue #726; ADR-0002's flat tint, the same treatment every tool layer
   gets), so no per-material worn file exists or is needed. The item sprites go under
   `derived/tools/` so `ToolArt#layer` resolves them like any other tool layer (the plating ones
   duplicate `derived/item/plating_<piece>.png` from #677 byte for byte -- same upstream file).

Usage: python3 scripts/derive_armor_art.py (only when the upstream pin or the base list changes).
Requires Pillow (`pip install pillow`), and the 1.20 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.20"
UPSTREAM_TEXTURES = UPSTREAM / "src/main/resources/assets/tconstruct/textures"

ASSETS = ROOT / "src/main/resources/assets/forgeweave/textures"
ARMOR_LAYERS = ASSETS / "models/armor/derived"
DERIVED_TOOLS = ASSETS / "derived/tools"

PIECES = ("helmet", "chestplate", "leggings", "boots")
# Vanilla's armor layout: layer_1 is the helmet/chestplate/boots sheet, layer_2 the leggings sheet.
BASES = {
    "plating_layer_1.png": "tinker_armor/plate/plating_armor.png",
    "plating_layer_2.png": "tinker_armor/plate/plating_leggings.png",
    "maille_layer_1.png": "tinker_armor/plate/maille_armor.png",
    "maille_layer_2.png": "tinker_armor/plate/maille_leggings.png",
}


def main() -> None:
    if not UPSTREAM_TEXTURES.is_dir():
        raise SystemExit(f"1.20 clone not found at {UPSTREAM} -- see CLAUDE.md for how to re-create it")
    ARMOR_LAYERS.mkdir(parents=True, exist_ok=True)
    for output, source in BASES.items():
        Image.open(UPSTREAM_TEXTURES / source).convert("RGBA").save(ARMOR_LAYERS / output)
        print(f"wrote {ARMOR_LAYERS / output}")
    for piece in PIECES:
        for sprite, output in (("plating", f"{piece}_plating"), ("plating_broken", f"{piece}_plating_broken"),
                               ("maille", f"{piece}_maille")):
            src = UPSTREAM_TEXTURES / "item/tool/armor/plate" / piece / f"{sprite}.png"
            Image.open(src).convert("RGBA").save(DERIVED_TOOLS / f"{output}.png")
            print(f"wrote {DERIVED_TOOLS / output}.png")

if __name__ == "__main__":
    main()
