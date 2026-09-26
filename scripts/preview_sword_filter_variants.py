"""Compare stone finishes and two-color alloy patterns on the normal sword."""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools"
MATERIALS = ROOT / "src/main/resources/data/forgeweave/forgeweave/material"
OUTPUT = ROOT / "artifacts/sword-filter-variants.png"


def color(name: str) -> tuple[int, int, int]:
    value = json.loads((MATERIALS / f"{name}.json").read_text())["color"]
    return tuple(bytes.fromhex(value.lstrip("#")))


STONE = color("stone")
UNOBTAINIUM = color("unobtainium")
VIBRANIUM = color("vibranium")
ALLOY = color("unobtainium_vibranium_alloy")


def clamp(value: float) -> int:
    return max(0, min(255, round(value)))


def finish_layer(image: Image.Image, variant: str) -> Image.Image:
    result = Image.new("RGBA", image.size)
    out = result.load()
    source = image.convert("RGBA").load()
    for y in range(16):
        for x in range(16):
            r, g, b, alpha = source[x, y]
            if not alpha:
                continue
            light = math.sqrt(.241 * r * r + .691 * g * g + .068 * b * b) / 255
            grain = ((x * 29 + y * 17 + x * y * 13) % 11) / 10 - .5
            if variant == "stone_matte":
                base = STONE
                factor = .84 + .10 * light
            elif variant == "stone_rough":
                base = STONE
                factor = .82 + .13 * light + grain * .21
            elif variant == "stone_polished":
                base = STONE
                factor = .78 + .35 * light * light
            elif variant == "alloy_current":
                base = ALLOY
                factor = 1
            elif variant == "alloy_highlight":
                base = UNOBTAINIUM if light > .68 else VIBRANIUM
                factor = .90 + .17 * light
            elif variant == "alloy_bands":
                base = UNOBTAINIUM if (x + y) % 6 < 3 else VIBRANIUM
                factor = .92 + .14 * light
            elif variant == "alloy_crystal":
                base = UNOBTAINIUM if ((x * 7 + y * 3 + x * y) % 13) < 5 else VIBRANIUM
                factor = .88 + .19 * light
            elif variant == "alloy_gradient":
                mix = min(1, max(0, (x + y - 4) / 20))
                base = tuple(round(a * (1 - mix) + b * mix) for a, b in zip(VIBRANIUM, UNOBTAINIUM))
                factor = .88 + .19 * light
            else:
                raise ValueError(variant)
            out[x, y] = tuple(clamp(channel * factor * original / 255)
                              for channel, original in zip(base, (r, g, b))) + (alpha,)
    return result


def sword(variant: str) -> Image.Image:
    result = Image.new("RGBA", (16, 16))
    for layer in ("handle", "head", "binding"):
        source = Image.open(TEXTURES / f"broadsword_{layer}.png")
        result.alpha_composite(finish_layer(source, variant))
    return result


def main() -> None:
    entries = [
        ("Pedra", [
            ("Fosca", "stone_matte", "Baixo brilho, cor uniforme"),
            ("Rugosa", "stone_rough", "Granulação irregular"),
            ("Polida", "stone_polished", "Arestas claras"),
        ]),
        ("Liga: unobtainium + vibranium", [
            ("Tint unico", "alloy_current", "Azul medio do JSON"),
            ("Luz e sombra", "alloy_highlight", "Roxo nas luzes, verde nas sombras"),
            ("Faixas", "alloy_bands", "Verde e roxo em bandas"),
            ("Cristal", "alloy_crystal", "Manchas pequenas e irregulares"),
            ("Gradiente", "alloy_gradient", "Transição ao longo da lâmina"),
        ]),
    ]
    width, height = 1120, 690
    sheet = Image.new("RGB", (width, height), "#161b22")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 20)
    small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    draw.text((24, 18), "Espada normal  |  vibranium verde + unobtainium roxo", font=font, fill="#f8fafc")
    for group, (title, variants) in enumerate(entries):
        y = 70 if group == 0 else 350
        draw.text((24, y), title, font=font, fill="#e2e8f0")
        for index, (label, variant, detail) in enumerate(variants):
            x = 24 + index * 218
            draw.rounded_rectangle((x, y + 38, x + 205, y + 260), radius=10, fill="#28313d")
            scaled = sword(variant).resize((128, 128), Image.Resampling.NEAREST)
            sheet.paste(scaled, (x + 38, y + 49), scaled)
            draw.text((x + 12, y + 184), label, font=font, fill="white")
            draw.text((x + 12, y + 218), detail[:26], font=small, fill="#b8c4d2")
    OUTPUT.parent.mkdir(exist_ok=True)
    sheet.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
