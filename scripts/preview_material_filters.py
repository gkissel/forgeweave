"""Render a contact sheet of every tool with current and proposed material finishes.

This is a visual prototype. It reads Forgeweave's material colors and 16px tool layers;
it does not alter game assets or runtime rendering.
"""

from __future__ import annotations

import colorsys
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
DEFAULT = RESOURCES / "assets/forgeweave/textures"
LEGACY = RESOURCES / "resourcepacks/legacy/assets/forgeweave/textures"
MATERIALS = RESOURCES / "data/forgeweave/forgeweave/material"
OUTPUT = ROOT / "artifacts/material-filters-all-tools.png"
SELECTED = (
    "wood", "stone", "cobalt", "vibranium", "unobtainium",
    "unobtainium_vibranium_alloy", "vibranium_allthemodium_alloy",
    "atomic_matter_alloy",
)
TOOL_LABELS = {"warmace": "maça", "broadsword": "espada", "pickaxe": "picareta"}
LAYER_ORDER = ("maille", "plating", "shaft", "handle", "head", "head2", "head3",
               "head4", "body", "limb", "limb2", "binding", "string", "fletching")


def tool_layers() -> dict[str, list[str]]:
    names = set()
    for directory in (DEFAULT / "derived/tools", DEFAULT / "tools"):
        for path in directory.glob("*.png"):
            if "_broken" in path.stem or "_draw" in path.stem:
                continue
            for suffix in LAYER_ORDER:
                if path.stem.endswith("_" + suffix):
                    names.add(path.stem[: -len(suffix) - 1])
                    break
    result = {}
    for name in sorted(names):
        layers = [suffix for suffix in LAYER_ORDER if any(
            (directory / f"{name}_{suffix}.png").exists()
            for directory in (DEFAULT / "derived/tools", DEFAULT / "tools"))]
        if layers and not name.startswith(("arrow_", "shuriken_")):
            result[name] = layers
    return result


def sprite(set_name: str, name: str, layer: str) -> Image.Image:
    filename = f"{name}_{layer}.png"
    directories = (LEGACY / "derived/tools", LEGACY / "tools",
                   DEFAULT / "derived/tools", DEFAULT / "tools") if set_name == "Legacy" else (
                   DEFAULT / "derived/tools", DEFAULT / "tools")
    for directory in directories:
        path = directory / filename
        if path.exists():
            return Image.open(path).convert("RGBA")
    raise FileNotFoundError(filename)


def finish(source: Image.Image, color: tuple[int, int, int], family: str,
           proposed: bool) -> Image.Image:
    pixels = []
    for red, green, blue, alpha in source.get_flattened_data():
        if alpha == 0:
            pixels.append((0, 0, 0, 0))
            continue
        channels = [red, green, blue]
        base = [round(channels[i] * color[i] / 255) for i in range(3)]
        if proposed:
            light = math.sqrt(.241 * red**2 + .691 * green**2 + .068 * blue**2) / 255
            hue, saturation, value = colorsys.rgb_to_hsv(*(c / 255 for c in base))
            if family == "metal":
                hue = (hue - (.5 - light * light) * .055) % 1
                if light > .9:
                    saturation = max(0, saturation - light * light * .27)
                if light > .8:
                    value = min(1, value + light * light * .14)
            elif family == "wood":
                saturation = min(1, saturation * 1.08)
                value = max(0, min(1, value * (.91 + .11 * light)))
            elif family == "stone":
                saturation *= .68
                value = max(0, min(1, value * (.94 + .06 * light)))
            base = [round(c * 255) for c in colorsys.hsv_to_rgb(hue, saturation, value)]
        pixels.append((*base, alpha))
    result = Image.new("RGBA", source.size)
    result.putdata(pixels)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("materials", nargs="*", help="Material IDs to preview (default: curated comparison)")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    selected = tuple(args.materials) if args.materials else SELECTED
    for material in selected:
        if not (MATERIALS / f"{material}.json").is_file():
            parser.error(f"unknown material: {material}")
    all_tools = tool_layers()
    tools = {name: all_tools[name] for name in ("warmace", "broadsword", "pickaxe")}
    scale = 3
    cell = 86
    left = 258
    top = 72
    block = 4 * 62 + 28
    width = left + len(tools) * cell + 24
    height = top + len(selected) * block + 24
    sheet = Image.new("RGB", (width, height), "#161b22")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    draw.text((12, 12), "Filtros de material  |  sprites 16px ampliados 3x", font=font, fill="white")
    for col, tool in enumerate(tools):
        draw.text((left + col * cell + 2, 48), TOOL_LABELS[tool], font=small, fill="#cbd5e1")
    for row, material in enumerate(selected):
        data = json.loads((MATERIALS / f"{material}.json").read_text())
        display_color = data["color"]
        hex_color = display_color.lstrip("#")
        color = tuple(bytes.fromhex(hex_color))
        family = "wood" if material == "wood" else "stone" if material == "stone" else "metal"
        y0 = top + row * block
        draw.rectangle((8, y0, width - 12, y0 + block - 8), fill="#222a34")
        draw.text((14, y0 + 10), material.replace("_", " "), font=font, fill="#f8fafc")
        draw.text((14, y0 + 31), f"{display_color}  |  {family}", font=small, fill="#aab5c4")
        for line, (set_name, proposed) in enumerate((("Forged", False), ("Forged", True),
                                                      ("Legacy", False), ("Legacy", True))):
            y = y0 + line * 62 + 4
            draw.text((14, y + 18), f"{set_name} {'filtro' if proposed else 'atual'}", font=small,
                      fill="#cbd5e1")
            for col, (tool, layers) in enumerate(tools.items()):
                composed = Image.new("RGBA", (16, 16))
                for layer in layers:
                    composed.alpha_composite(finish(sprite(set_name, tool, layer), color, family, proposed))
                enlarged = composed.resize((16 * scale, 16 * scale), Image.Resampling.NEAREST)
                sheet.paste(enlarged, (left + col * cell + 7, y + 5), enlarged)
    args.output.parent.mkdir(exist_ok=True)
    sheet.save(args.output)
    print(f"{len(tools)} tools, {len(selected)} materials: {args.output}")


if __name__ == "__main__":
    main()
