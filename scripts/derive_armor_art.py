"""Derives the plate armor render art from the 1.20 clone (issue #679, M4-4; docs/SCOPE.md D18).

Tinkers' 2 has no armor, so everything here comes from the 1.20.1 branch (pinned de26560d, MIT --
the M4 derivation source by name). Two kinds of output:

1. Copied unmodified -- the grayscale bases, one NOTICE.md row each:

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
   flat (ADR-0002), the same treatment every tool layer gets. The item sprites go under
   `derived/tools/` so `ToolArt#layer` resolves them like any other tool layer (the plating ones
   duplicate `derived/item/plating_<piece>.png` from #677 byte for byte -- same upstream file).

2. Generated -- the per-material worn layers `models/armor/derived/{plating,maille}_<material>_layer_{1,2}.png`,
   each base multiplied by that material's `color` from `data/forgeweave/forgeweave/material/<material>.json`
   (`out = gray * channel // 255`, Pillow's `ImageChops.multiply`; alpha untouched). Plating layers for every material with a
   `plating` block, maille layers for every material with `maille: true`; materials the clone has no
   PNG for (ardite, netherite, nahuatl) come out of the same loop. `ArmorPieceItem#getArmorTexture`
   picks the file by the part's material at render time. No NOTICE.md rows of their own: every pixel
   is the base row's, times a Forgeweave datapack colour (`ArmorArtTest` pins the math).

Usage: python3 scripts/derive_armor_art.py (after any material colour or armor-schema change).
Requires Pillow (`pip install pillow`), and the 1.20 clone at the path CLAUDE.md pins.
"""
import json
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.20"
UPSTREAM_TEXTURES = UPSTREAM / "src/main/resources/assets/tconstruct/textures"

ASSETS = ROOT / "src/main/resources/assets/forgeweave/textures"
ARMOR_LAYERS = ASSETS / "models/armor/derived"
DERIVED_TOOLS = ASSETS / "derived/tools"
MATERIALS = ROOT / "src/main/resources/data/forgeweave/forgeweave/material"

PIECES = ("helmet", "chestplate", "leggings", "boots")
# Vanilla's armor layout: layer_1 is the helmet/chestplate/boots sheet, layer_2 the leggings sheet.
BASES = {
    "plating_layer_1.png": "tinker_armor/plate/plating_armor.png",
    "plating_layer_2.png": "tinker_armor/plate/plating_leggings.png",
    "maille_layer_1.png": "tinker_armor/plate/maille_armor.png",
    "maille_layer_2.png": "tinker_armor/plate/maille_leggings.png",
}


def tint_image(base: Image.Image, color: int) -> Image.Image:
    """The gray base times a 0xRRGGBB colour, channel by channel (gray * c // 255); alpha untouched."""
    solid = Image.new("RGBA", base.size, ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255))
    return ImageChops.multiply(base, solid)


def material_colors() -> dict[str, tuple[int, bool, bool]]:
    """material id -> (colour, has plating stats, has maille stats), off the datapack JSONs."""
    result = {}
    for path in sorted(MATERIALS.glob("*.json")):
        data = json.loads(path.read_text())
        result[path.stem] = (int(data["color"].lstrip("#"), 16), "plating" in data, data.get("maille") is True)
    return result


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
    bases = {name: Image.open(ARMOR_LAYERS / name).convert("RGBA") for name in BASES}
    for material, (color, plating, maille) in material_colors().items():
        for part, wanted in (("plating", plating), ("maille", maille)):
            if not wanted:
                continue
            for layer in (1, 2):
                out = ARMOR_LAYERS / f"{part}_{material}_layer_{layer}.png"
                tint_image(bases[f"{part}_layer_{layer}.png"], color).save(out)
                print(f"wrote {out}")


if __name__ == "__main__":
    main()
