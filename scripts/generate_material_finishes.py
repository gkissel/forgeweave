"""Bake the chosen Forged finishes for every tool and loose part."""

from pathlib import Path
import re

from PIL import Image

from preview_material_families import TITLES, family_of, render
from preview_material_filters import DEFAULT, MATERIALS, ROOT, sprite, tool_layers


SELECTED = {
    "cristal": "Prismatico",
    "energia": "Pulsante",
    "ligas_duplas": "Veios",
    "madeira": "Veios largos",
    "metal": "Metal ZIP",
    "organico": "Poros",
    "pedra": "Rugosa",
    "slime": "Bolhas",
}
OUTPUT = ROOT / "src/main/resources/assets/forgeweave/textures/material_finishes"
ARMOR = {"helmet", "chestplate", "leggings", "boots"}
DRAW_TOOLS = {"shortbow", "longbow", "crossbow"}


def selected_variant(family, tool=None):
    finish = "Gotas" if family == "slime" and tool == "pickaxe" else SELECTED[family]
    return TITLES[family].index(finish)


def part_names():
    provider = (ROOT / "src/main/java/dev/gkissel/forgeweave/data/ForgeweaveItemModelProvider.java").read_text()
    return tuple(re.findall(
        r'singleLayerModel\(ForgeweaveItems\.(?:PART_[A-Z_]+|SHARD),\s*'
        r'(?:derivedItem|itemTexture)\("([^"]+)"\)\)', provider))


def part_sprite(part):
    for directory in (DEFAULT / "derived/item", DEFAULT / "item"):
        path = directory / f"{part}.png"
        if path.is_file():
            return Image.open(path).convert("RGBA")
    raise FileNotFoundError(part)


def draw_sprite(tool, layer, stage):
    staged = layer.startswith("string") or (layer.startswith("limb") and stage >= 2)
    return sprite("Forged", tool, f"{layer}_draw{stage}" if staged else layer)


def main():
    layers = tool_layers()
    tools = tuple(tool for tool in layers if tool not in ARMOR)
    parts = part_names()
    materials = sorted(path.stem for path in MATERIALS.glob("*.json"))
    expected = set()
    for tool in tools:
        for material in materials:
            family = family_of(material)
            variant = selected_variant(family, tool)
            for layer in layers[tool]:
                target = OUTPUT / tool / material / f"{layer}.png"
                target.parent.mkdir(parents=True, exist_ok=True)
                render(sprite("Forged", tool, layer), material, family, variant).save(target)
                expected.add(target)
                if tool in DRAW_TOOLS:
                    for stage in (1, 2, 3):
                        target = OUTPUT / tool / material / f"{layer}_draw{stage}.png"
                        render(draw_sprite(tool, layer, stage), material, family, variant).save(target)
                        expected.add(target)
    for part in parts:
        source = part_sprite(part)
        for material in materials:
            family = family_of(material)
            variant = selected_variant(family)
            target = OUTPUT / "parts" / material / f"{part}.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            render(source, material, family, variant).save(target)
            expected.add(target)
    for path in OUTPUT.rglob("*.png"):
        if path not in expected:
            path.unlink()
    print(f"{len(materials)} materials, {len(tools)} tools, {len(parts)} parts, {len(expected)} sprites")


if __name__ == "__main__":
    main()
