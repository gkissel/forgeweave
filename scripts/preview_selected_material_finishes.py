"""Show the eight material finishes selected for the next texture implementation."""

from PIL import Image, ImageDraw, ImageFont

from preview_material_families import FAMILIES, OUT, TITLES, render
from preview_material_filters import sprite, tool_layers


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


def main():
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    tools = {key: tool_layers()[key] for key in ("warmace", "broadsword", "pickaxe")}
    sheet = Image.new("RGB", (590, 80 + len(SELECTED) * 94), "#171d26")
    draw = ImageDraw.Draw(sheet)
    draw.text((16, 12), "ACABAMENTOS ESCOLHIDOS  |  Forged 16x16", font=font, fill="white")
    for col, label in enumerate(("warmace", "espada", "picareta")):
        draw.text((263 + col * 103, 47), label, font=font, fill="#cbd5e1")
    for row, (family, title) in enumerate(SELECTED.items()):
        y = 80 + row * 94
        draw.rectangle((8, y, 582, y + 86), fill="#29333f")
        draw.text((17, y + 10), family.replace("_", " ").title(), font=font, fill="white")
        draw.text((17, y + 36), title, font=font, fill="#b9cee2")
        material = FAMILIES[family][0]
        variant = TITLES[family].index(title)
        draw.text((17, y + 62), material, font=font, fill="#9baab9")
        for col, (tool, layers) in enumerate(tools.items()):
            image = Image.new("RGBA", (16, 16))
            for layer in layers:
                image.alpha_composite(render(sprite("Forged", tool, layer), material, family, variant))
            big = image.resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(big, (265 + col * 103, y + 10), big)
    OUT.mkdir(exist_ok=True)
    output = OUT / "filtros-escolhidos.png"
    sheet.save(output)
    print(output)


if __name__ == "__main__":
    main()
