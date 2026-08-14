#!/usr/bin/env python3
"""Derives the broken-tool layer art (issue #284).

Upstream 1.12 ships a broken variant for exactly one layer of each tool, and its tool models name
that layer explicitly: `models/item/tools/<tool>.tcon.json` carries a `broken<N>` texture key beside
its `layer<N>` keys, and `BakedToolModel#getOverrides` swaps `layer<N>` for it once
`ToolHelper#isBroken`. Every one of those files breaks the head/blade layer except the hammer, which
breaks its handle -- see the table below, read straight off those JSONs at the pinned commit. The
Forgeweave side of the same table lives in `ToolArt#BROKEN_LAYERS`; `BrokenToolModelTest` pins the
two together.

| Forgeweave output (`derived/tools/`) | Upstream source (`items/`) |
| --- | --- |
| `battleaxe_head_broken.png` | `battleaxe/broken_head.png` (upstream calls the layer `backhead`) |
| `battlesign_head_broken.png` | `battlesign/broken_head.png` |
| `broadsword_head_broken.png` | `broadsword/broken_blade.png` |
| `cleaver_head_broken.png` | `cleaver/broken_head.png` |
| `excavator_head_broken.png` | `excavator/broken_head.png` |
| `frying_pan_head_broken.png` | `frypan/broken_head.png` |
| `hammer_handle_broken.png` | `hammer/broken_handle.png` (the one tool upstream breaks at layer0) |
| `hatchet_head_broken.png` | `hatchet/broken_head.png` |
| `kama_head_broken.png` | `kama/broken_head.png` |
| `longsword_head_broken.png` | `longsword/broken_blade.png` |
| `lumberaxe_head_broken.png` | `lumberaxe/broken_head.png` |
| `mattock_head_broken.png` | `mattock/broken_head.png` |
| `pickaxe_head_broken.png` | `pickaxe/broken_head.png` |
| `rapier_head_broken.png` | `rapier/broken_blade.png` |
| `scythe_head_broken.png` | `scythe/broken_head.png` |
| `shovel_head_broken.png` | `shovel/broken_head.png` |

Five tools have no upstream *broken* art at all -- the dagger, katana and scimitar (issue
#159/#198/#279), the vein hammer (#157) and the war mace (#161) -- so there is no broken art to port
for them. Their broken layers are instead an algorithmic transform of their own head layer, the same
"pixels through a documented transform" treatment `generate_pattern_textures.py` and
`generate_cast_textures.py` give their outputs. All five chip an already-*derived* head, so NOTICE.md
carries a row per file: every input pixel is still some upstream's.

The katana used to be the exception -- issue #279 made its head freshly authored, so chipping it
yielded authored pixels and its broken layer followed the head out of the derived tree. Issue #375
re-sourced that head from Spartan Weaponry (Apache-2.0, `scripts/derive_spartan_blade_art.py`), so
`katana_head_broken.png` is derived again and back under `derived/tools/` with the other four; its
NOTICE.md row cites Spartan Weaponry rather than the 1.12 clone. The katana's *other* two layers
stay authored -- see `ToolArt#ORIGINAL_ART`, which is now keyed per layer -- but neither is a
CHIPPED entry, so nothing here reads them.

The transform, `chip()`: project the layer's opaque pixels onto the shape's principal axis and erase
the outermost `CHIP_FRACTION` of them at each end. That is what upstream's hand-drawn broken art does
to a part -- compare `pickaxe/head.png` with `pickaxe/broken_head.png` and the two tips of the pick
are gone while the middle survives; the same holds for the broadsword blade (tip chipped, base
trimmed) and the scythe head. Erasure only: upstream also re-shades the fresh fracture by hand, in no
systematic direction (the broadsword's changed pixels go 9 darker and 15 lighter), so there is no
recolor rule to derive and this deliberately introduces no colors of its own. Erasing ~30% of the
part reads as broken at 16x16 and matches upstream's own removal share (the pickaxe head loses 14 of
its 39 opaque pixels).

Usage: python3 scripts/derive_broken_art.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins.
"""
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_1_12 = Path.home() / "development/minecraft/references/tinkers-1.12/resources/assets/tconstruct/textures/items"

DERIVED_TOOLS = ROOT / "src/main/resources/assets/forgeweave/textures/derived/tools"

# Forgeweave "<tool>_<layer>" -> upstream file, straight ports. See the module docstring's table.
PORTED = {
    "battleaxe_head": "battleaxe/broken_head.png",
    "battlesign_head": "battlesign/broken_head.png",
    "broadsword_head": "broadsword/broken_blade.png",
    "cleaver_head": "cleaver/broken_head.png",
    "excavator_head": "excavator/broken_head.png",
    "frying_pan_head": "frypan/broken_head.png",
    "hammer_handle": "hammer/broken_handle.png",
    "hatchet_head": "hatchet/broken_head.png",
    "kama_head": "kama/broken_head.png",
    "longsword_head": "longsword/broken_blade.png",
    "lumberaxe_head": "lumberaxe/broken_head.png",
    "mattock_head": "mattock/broken_head.png",
    "pickaxe_head": "pickaxe/broken_head.png",
    "rapier_head": "rapier/broken_blade.png",
    "scythe_head": "scythe/broken_head.png",
    "shovel_head": "shovel/broken_head.png",
}

# The five Forgeweave-only tools; their broken layer is chip() applied to their own head layer.
CHIPPED = ["dagger_head", "katana_head", "scimitar_head", "vein_hammer_head", "warmace_head"]

# How much of the part chip() erases at each end of its principal axis. See the module docstring.
CHIP_FRACTION = 0.15


def chip(part: Image.Image) -> Image.Image:
    """`part` with the outermost pixels at both ends of its long axis erased; see the docstring."""
    width, height = part.size
    pixels = part.load()
    opaque = [(x, y) for y in range(height) for x in range(width) if pixels[x, y][3] > 0]
    if not opaque:
        raise ValueError("cannot chip a fully transparent layer")

    mean_x = sum(x for x, _ in opaque) / len(opaque)
    mean_y = sum(y for _, y in opaque) / len(opaque)
    cxx = sum((x - mean_x) ** 2 for x, _ in opaque)
    cyy = sum((y - mean_y) ** 2 for _, y in opaque)
    cxy = sum((x - mean_x) * (y - mean_y) for x, y in opaque)
    angle = 0.5 * math.atan2(2 * cxy, cxx - cyy)
    axis_x, axis_y = math.cos(angle), math.sin(angle)

    # Sorted by position along that axis; the tie-break keeps the result independent of iteration order.
    def position(pixel: tuple[int, int]) -> tuple[float, int, int]:
        x, y = pixel
        return ((x - mean_x) * axis_x + (y - mean_y) * axis_y, y, x)

    ordered = sorted(opaque, key=position)
    end = max(1, round(CHIP_FRACTION * len(ordered)))
    out = part.copy()
    for x, y in ordered[:end] + ordered[-end:]:
        out.putpixel((x, y), (0, 0, 0, 0))
    return out


def main() -> None:
    if not UPSTREAM_1_12.is_dir():
        raise SystemExit(f"1.12 clone not found at {UPSTREAM_1_12} -- see CLAUDE.md for how to re-create it")

    outputs = {}
    for name, upstream in PORTED.items():
        outputs[DERIVED_TOOLS / f"{name}_broken.png"] = Image.open(UPSTREAM_1_12 / upstream).convert("RGBA")
    for name in CHIPPED:
        intact = DERIVED_TOOLS / f"{name}.png"
        if not intact.is_file():
            raise SystemExit(f"expected {intact} to exist")
        outputs[DERIVED_TOOLS / f"{name}_broken.png"] = chip(Image.open(intact).convert("RGBA"))

    for path, image in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
