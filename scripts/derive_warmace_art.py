#!/usr/bin/env python3
"""Derives the warmace's art from the 1.12 clone's hammer (issue #161, maintainer decision on #198).

The warmace has no upstream counterpart in either clone -- 1.12 has no mace-alike and the 1.20
branch predates vanilla's own mace -- so per the #198 decision its art is derived from the closest
upstream equivalent rather than freshly authored: 1.12's hammer, the one blunt heavy head on a
tough haft. Every output lands under `textures/derived/` with a NOTICE.md row, and the designer
replaces all of it at M9.

Three of the four sprites are the upstream files unchanged; one carries a minimal reshape:

| Output | Upstream source | Transform |
| --- | --- | --- |
| `derived/tools/warmace_handle.png` | `items/hammer/handle.png` | none -- the tough haft is already the warmace's |
| `derived/tools/warmace_binding.png` | `items/hammer/front.png` | none -- upstream's front plate sits exactly where Forgeweave's binding layer goes |
| `derived/tools/warmace_head.png` | `items/hammer/head.png` | RESHAPE (below) |
| `derived/item/war_mace_head.png` | `items/hammer/head.png` | the same reshape; upstream likewise reuses one head sprite as both the part icon and the tool layer |

The reshape, in one line: the head is alpha-composited with its own horizontal mirror, the mirror
aligned on the original's bounding box. Upstream's head is a rhombus elongated across the haft --
a hammer's broad striking face. Overlaying its mirror fills the two opposite notches and closes the
silhouette into a round knob, which is what separates a mace from a hammer at 16x16. Nothing is
drawn: every pixel in the result is an upstream pixel in an upstream colour, which is also why the
greyscale ramp the material tint rides stays exactly upstream's.

Forgeweave tools are three-layer (handle/head/binding) where upstream's hammer is four
(handle/head/front/back), so upstream's `back.png` -- the far half of the striking face -- has no
layer to land on and is dropped. That is also what keeps this from colliding with #157's hammer,
which derives the same upstream sprites without the reshape.

Usage: python3 scripts/derive_warmace_art.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = Path.home() / "development/minecraft/references/tinkers-1.12"
UPSTREAM_ITEMS = UPSTREAM / "resources/assets/tconstruct/textures/items"

DERIVED = ROOT / "src/main/resources/assets/forgeweave/textures/derived"
DERIVED_ITEM = DERIVED / "item"
DERIVED_TOOLS = DERIVED / "tools"


def reshape_to_mace(head: Image.Image) -> Image.Image:
    """Upstream's hammer head overlaid with its own bounding-box-aligned mirror; see the module docstring."""
    box = head.getbbox()
    mirrored = ImageOps.mirror(head)
    aligned = Image.new("RGBA", head.size)
    aligned.paste(mirrored.crop(mirrored.getbbox()), (box[0], box[1]))
    out = head.copy()
    out.alpha_composite(aligned)
    return out


def main() -> None:
    if not UPSTREAM_ITEMS.is_dir():
        raise SystemExit(f"1.12 clone not found at {UPSTREAM} -- see CLAUDE.md for how to re-create it")

    head = Image.open(UPSTREAM_ITEMS / "hammer/head.png").convert("RGBA")
    mace_head = reshape_to_mace(head)

    outputs = [
        (DERIVED_TOOLS / "warmace_handle.png", Image.open(UPSTREAM_ITEMS / "hammer/handle.png").convert("RGBA")),
        (DERIVED_TOOLS / "warmace_head.png", mace_head),
        (DERIVED_TOOLS / "warmace_binding.png", Image.open(UPSTREAM_ITEMS / "hammer/front.png").convert("RGBA")),
        (DERIVED_ITEM / "war_mace_head.png", mace_head),
    ]
    for output, image in outputs:
        output.parent.mkdir(parents=True, exist_ok=True)
        image.save(output)
        print(f"wrote {output}")


if __name__ == "__main__":
    main()
