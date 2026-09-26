"""Bake the chosen Forged finishes for the sword, pickaxe and warmace."""

from pathlib import Path

from preview_material_families import TITLES, family_of, render
from preview_material_filters import MATERIALS, ROOT, sprite, tool_layers


SELECTED = {
    "cristal": "Facetado",
    "energia": "Pulsante",
    "ligas_duplas": "Veios",
    "madeira": "Fibras finas",
    "metal": "Escovada",
    "organico": "Trama",
    "pedra": "Rugosa",
    "slime": "Gel",
}
OUTPUT = ROOT / "src/main/resources/assets/forgeweave/textures/material_finishes"
TOOLS = ("warmace", "broadsword", "pickaxe")


def main():
    layers = tool_layers()
    materials = sorted(path.stem for path in MATERIALS.glob("*.json"))
    expected = set()
    for tool in TOOLS:
        for material in materials:
            family = family_of(material)
            variant = TITLES[family].index(SELECTED[family])
            for layer in layers[tool]:
                target = OUTPUT / tool / material / f"{layer}.png"
                target.parent.mkdir(parents=True, exist_ok=True)
                render(sprite("Forged", tool, layer), material, family, variant).save(target)
                expected.add(target)
    for path in OUTPUT.rglob("*.png"):
        if path not in expected:
            path.unlink()
    print(f"{len(materials)} materials, {len(TOOLS)} tools, {len(expected)} sprites")


if __name__ == "__main__":
    main()
