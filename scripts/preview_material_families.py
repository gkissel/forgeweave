"""Compare visual finishes per material family on three Forged tool sprites.

Outputs one contact sheet per family. These are visual prototypes, not runtime art.
Material colors come from the shipped JSONs; alloy constituents keep separate colors.
"""

from __future__ import annotations

import argparse
import colorsys
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from preview_material_filters import MATERIALS, ROOT, sprite, tool_layers


OUT = ROOT / "artifacts"
TOOL_LABELS = {"warmace": "maça", "broadsword": "espada", "pickaxe": "picareta"}
FAMILIES = {
    "madeira": ("wood", "firewood", "ironwood"),
    "pedra": ("stone", "flint", "obsidian"),
    "metal": ("iron", "cobalt", "vibranium"),
    "cristal": ("amethyst", "emerald", "certus_quartz"),
    "organico": ("bone", "cactus", "naga_scale"),
    "slime": ("slime", "blueslime", "pink_slime"),
    "energia": ("draconium_core", "wyvern", "awakened"),
    "ligas_duplas": ("vibranium_allthemodium_alloy", "unobtainium_vibranium_alloy",
                     "unobtainium_allthemodium_alloy"),
}
TITLES = {
    "madeira": ("Veios largos", "Fibras finas", "Aneis", "Casca", "Tinta ZIP"),
    "pedra": ("Rugosa", "Lascada", "Granulada", "Polida", "Tinta ZIP"),
    "metal": ("Fundida", "Escovada", "Polida", "Temperada", "Metal ZIP"),
    "cristal": ("Facetado", "Degrade leve", "Prismatico", "Lapidado", "Metal ZIP"),
    "organico": ("Estrias", "Escamas", "Poros", "Trama", "Metal ZIP"),
    "slime": ("Gel", "Bolhas", "Gotas", "Nucleo", "Metal ZIP"),
    "energia": ("Pulsante", "Arcos", "Nucleo", "Radiante", "Metal ZIP"),
    "ligas_duplas": ("Diagonal", "Centro/borda", "Veios", "Degrade", "Metal ZIP"),
}
CONSTITUENTS = {
    "vibranium_allthemodium_alloy": ("vibranium", "allthemodium"),
    "unobtainium_vibranium_alloy": ("vibranium", "unobtainium"),
    "unobtainium_allthemodium_alloy": ("unobtainium", "allthemodium"),
}


def family_of(material: str) -> str:
    if material in CONSTITUENTS:
        return "ligas_duplas"
    if material == "ice":
        return "cristal"
    if material == "blaze":
        return "energia"
    if any(word in material for word in ("slime", "osgloglas")):
        return "slime"
    if any(word in material for word in ("wood", "leaf", "vine", "reed", "paper", "chorus", "endrod")):
        return "madeira"
    if any(word in material for word in ("stone", "basalt", "flint", "obsidian", "netherrack",
                                          "quakestone", "prismarine", "brim", "shale")):
        return "pedra"
    if any(word in material for word in ("crystal", "quartz", "gem", "emerald", "amethyst",
                                          "fluorite", "zanite", "gravitite", "fluix", "prosperity")):
        return "cristal"
    if any(word in material for word in ("bone", "fur", "leather", "chitin", "feather", "cactus",
                                          "sponge", "string", "naga_scale", "dragon_bone")):
        return "organico"
    if material in {"draconium", "draconium_awakened", "draconium_core", "wyvern",
                    "awakened", "chaotic", "dark_matter", "red_matter", "infinity",
                    "cosmic_neutronium", "awakened_supremium"}:
        return "energia"
    return "metal"


def color(material: str) -> tuple[int, int, int]:
    return tuple(bytes.fromhex(json.loads((MATERIALS / f"{material}.json").read_text())["color"][1:]))


def mix(first, second, amount):
    return tuple(round(a * (1 - amount) + b * amount) for a, b in zip(first, second))


def finish(family, variant, x, y, light):
    """Return a visible, pixel-aligned value change for a 16-pixel sprite."""
    if family == "madeira":
        patterns = (
            (x + (y // 3)) % 4 < 2,
            (x * 2 + y) % 5 < 2,
            (abs(x - 7) + abs(y - 7)) % 5 < 2,
            (x // 2 + y // 2) % 3 == 0,
            (x + y * 2) % 6 < 2,
        )
        return .74 if patterns[variant] else 1.23
    if family == "organico":
        patterns = (
            (x + y) % 4 == 0,
            ((x // 3) + (y // 3)) % 2 == 0,
            (x * 7 + y * 11) % 13 < 4,
            abs(x - y) % 4 < 2,
            (x + 2 * y) % 5 < 2,
        )
        return .70 if patterns[variant] else 1.18
    if family == "slime":
        patterns = (
            (x + y) % 5 == 0,
            (x * 7 + y * 11) % 17 < 4,
            (x - y) % 5 < 2,
            abs(x - 7) + abs(y - 7) < 6,
            x + y < 13,
        )
        return 1.46 if patterns[variant] else .81
    if family == "pedra":
        patterns = ((x * 7 + y * 11 + x * y) % 13 < 5,
                    (x + y * 3) % 7 < 2,
                    (x * 11 + y * 5) % 17 < 5,
                    (x + y) % 5 < 2)
        return .69 if patterns[variant] else 1.13
    if family == "cristal":
        if variant == 1:
            return .88 + .24 * (x + y) / 30
        return 1.34 if (x * (variant + 1) + y) % 5 < 2 else .80
    if family == "energia":
        return 1.45 if (x * (variant + 3) + y * 2) % 7 < 3 else .72
    if family == "metal":
        return (1.0, .78 if (x + y) % 4 < 2 else 1.17,
                .78 + .45 * light, .73 if (x * 7 + y * 3) % 11 < 3 else 1.13)[variant]
    return 1


def render(source: Image.Image, material: str, family: str, variant: int) -> Image.Image:
    tint = color(material)
    if variant == 4:
        from preview_zip_filters import render as zip_render
        mode = "Tinta" if family in {"madeira", "pedra"} else "Metal"
        return zip_render(source.convert("RGBA"), mode, tint, None)
    sides = tuple(map(color, CONSTITUENTS[material])) if material in CONSTITUENTS else None
    result = Image.new("RGBA", source.size)
    out = []
    pixels = source.convert("RGBA").load()
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                out.append((0, 0, 0, 0))
                continue
            light = math.sqrt(.241*r*r + .691*g*g + .068*b*b) / 255
            if sides:
                if variant == 0:
                    tint = sides[1] if x + y > 14 else sides[0]
                elif variant == 1:
                    tint = sides[1] if light > .58 else sides[0]
                elif variant == 2:
                    tint = sides[1] if (x + y * 2) % 6 < 2 else sides[0]
                elif variant == 3:
                    tint = mix(*sides, .20 + .60 * (x + y) / 30)
            base = [r*tint[0]/255, g*tint[1]/255, b*tint[2]/255]
            hue, saturation, value = colorsys.rgb_to_hsv(*(c/255 for c in base))
            if family == "ligas_duplas":
                value *= .94 + .12 * light
            else:
                value *= finish(family, variant, x, y, light)
                if family == "pedra":
                    saturation *= .8
                if family == "slime":
                    saturation = min(1, saturation * 1.16)
            red, green, blue = colorsys.hsv_to_rgb(hue, saturation, max(0, min(1, value)))
            out.append((round(red*255), round(green*255), round(blue*255), a))
    result.putdata(out)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all-materials", action="store_true",
                        help="render every material JSON, split into pages of 12")
    args = parser.parse_args()
    families = FAMILIES
    if args.all_materials:
        grouped = {family: [] for family in FAMILIES}
        for path in sorted(MATERIALS.glob("*.json")):
            grouped[family_of(path.stem)].append(path.stem)
        families = {family: tuple(materials) for family, materials in grouped.items()}
    all_tools = tool_layers()
    tools = {name: all_tools[name] for name in ("warmace", "broadsword", "pickaxe")}
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    OUT.mkdir(exist_ok=True)
    for family, all_materials in families.items():
      for page in range(0, len(all_materials), 12):
        materials = all_materials[page:page+12]
        cell, left, top = 86, 226, 64
        block = 49 + len(TITLES[family]) * 51 + 9
        sheet = Image.new("RGB", (left + len(tools)*cell + 15,
                                  top + len(materials)*block + 15), "#161b22")
        draw = ImageDraw.Draw(sheet)
        draw.text((12, 12), f"{family.replace('_', ' ').upper()}  |  {len(TITLES[family])} filtros x {len(tools)} ferramentas  |  Forged",
                  font=font, fill="white")
        for col, tool in enumerate(tools):
            draw.text((left + col*cell, 41), TOOL_LABELS[tool], font=small, fill="#cbd5e1")
        for row, material in enumerate(materials):
            y0 = top + row*block
            draw.rectangle((6, y0, sheet.width-8, y0+block-6), fill="#28313d")
            draw.text((12, y0+7), material.replace("_", " "), font=font, fill="white")
            draw.text((12, y0+27), "#" + bytes(color(material)).hex().upper(), font=small,
                      fill="#b8c4d2")
            for variant, title in enumerate(TITLES[family]):
                y = y0 + 43 + variant*51
                draw.text((12, y+15), title, font=small, fill="#d1d9e3")
                for col, (tool, layers) in enumerate(tools.items()):
                    image = Image.new("RGBA", (16, 16))
                    for layer in layers:
                        image.alpha_composite(render(sprite("Forged", tool, layer), material,
                                                     family, variant))
                    enlarged = image.resize((48, 48), Image.Resampling.NEAREST)
                    sheet.paste(enlarged, (left + col*cell + 6, y), enlarged)
        suffix = f"-pagina-{page//12+1}" if args.all_materials else ""
        output = OUT / f"filtros-{family}{suffix}.png"
        sheet.save(output)
        print(output)


if __name__ == "__main__":
    main()
