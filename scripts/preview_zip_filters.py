"""Preview the three material renderers in the user supplied ZIP."""

from __future__ import annotations

from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw, ImageFont

from preview_material_filters import ROOT, sprite, tool_layers


OUT = ROOT / "artifacts"
MC_JAR = Path("/home/gustavo/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar")


def render(source, mode, tint, overlay):
    result = Image.new("RGBA", source.size)
    pixels = []
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = source.getpixel((x, y))
            if not a:
                pixels.append((0, 0, 0, 0))
                continue
            if mode == "Composicao":
                shade = ((r * 299 + g * 587 + b * 114) // 1000 / 255) ** 2
                cr, cg, cb, _ = overlay.getpixel((x, y))
                pixels.append((int(cr * shade), int(cg * shade), int(cb * shade), a))
                continue
            r, g, b = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255)
            if mode == "Metal":
                light = int((.241 * source.getpixel((x, y))[0] ** 2 +
                             .691 * source.getpixel((x, y))[1] ** 2 +
                             .068 * source.getpixel((x, y))[2] ** 2) ** .5) / 255
                if light > .8:  # iron's brightness=0.3; shininess=0; hue shift=0.
                    import colorsys
                    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                    r, g, b = (round(c * 255) for c in colorsys.hsv_to_rgb(h, s, min(1, v + light * light * .3)))
            pixels.append((r, g, b, a))
    result.putdata(pixels)
    return result


def main():
    with ZipFile(MC_JAR) as jar:
        overlay = Image.open(BytesIO(jar.read("assets/minecraft/textures/block/netherrack.png"))).convert("RGBA")
    overlay = overlay.crop((0, 0, 16, 16))
    tools = {key: tool_layers()[key] for key in ("warmace", "broadsword", "pickaxe")}
    cases = (("wood", "Tinta", (142, 91, 58)),
             ("stone", "Tinta", (119, 119, 119)),
             ("iron", "Metal", (216, 216, 216)),
             ("netherrack", "Composicao", (123, 42, 42)))
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    sheet = Image.new("RGB", (660, 420), "#181e27")
    draw = ImageDraw.Draw(sheet)
    draw.text((18, 14), "FILTROS PRESENTES NO ZIP  |  Forged 16x16", font=font, fill="white")
    for col, name in enumerate(("warmace", "espada", "picareta")):
        draw.text((255 + col * 125, 49), name, font=font, fill="#dce5f0")
    for row, (material, mode, tint) in enumerate(cases):
        y = 85 + row * 82
        draw.rectangle((10, y - 5, 650, y + 70), fill="#293440")
        draw.text((18, y + 10), material, font=font, fill="white")
        draw.text((18, y + 35), mode, font=font, fill="#b7cadf")
        for col, (tool, layers) in enumerate(tools.items()):
            image = Image.new("RGBA", (16, 16))
            for layer in layers:
                image.alpha_composite(render(sprite("Forged", tool, layer).convert("RGBA"), mode, tint, overlay))
            big = image.resize((64, 64), Image.Resampling.NEAREST)
            sheet.paste(big, (260 + col * 125, y), big)
    OUT.mkdir(exist_ok=True)
    output = OUT / "filtros-do-zip.png"
    sheet.save(output)
    print(output)


if __name__ == "__main__":
    main()
