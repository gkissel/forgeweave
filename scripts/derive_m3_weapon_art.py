#!/usr/bin/env python3
"""Derives the scimitar tool art and the vein hammer head part icon from upstream (issue #198,
maintainer decision 2026-08-12): replaces script-generated "original" art for these shapes with
upstream-derived pixels. Every output's pixels trace to an upstream file -- either a straight copy
or a reuse of an already-derived Forgeweave part (the same file another tool's layer already uses).

The dagger's own art used to be derived here too (1.12's mismatched `knife_blade.png`/broadsword
handle, per #198) but issue #278 moved it to `derive_dagger_art.py`, sourced from the 1.20 clone's
real dagger instead -- see that script's docstring.

| Output | Upstream source | Transform |
| --- | --- | --- |
| `derived/item/curved_blade.png`, `derived/tools/scimitar_head.png` | 1.12 `items/cutlass/blade.png` | none -- see below |
| `derived/tools/scimitar_handle.png` | 1.12 `items/broadsword/handle.png` | none -- the same `TOOL_HANDLE` reuse the rapier/cleaver/frypan layers already make |
| `derived/tools/scimitar_binding.png` | 1.12 `items/rapier/guard.png` | none -- `ToolConstants#SCIMITAR`'s EXTRA part is the existing `cross_guard`, itself derived from this file (NOTICE.md); the layer reuses the same pixels the part icon already does |
| `derived/item/vein_hammer_head.png` | 1.20 `.../textures/item/tool/vein_hammer/head.png` (already the source for `derived/tools/vein_hammer_head.png`, issue #151/#157, NOTICE.md) | none -- the part icon just adopts the tool layer's own already-derived pixels, the same one-file-serves-both convention `pickaxe_head.png` uses |

The scimitar's blade (issue #279): #198 read the 1.12 clone as having no curved-blade shape at all
and reshaped `parts/sword_blade.png` into an approximation of one. It does have the shape. 1.12
ships a complete `items/cutlass/` art set -- a curved, single-edged, tip-heavy sabre blade -- for a
tool whose `ToolCore` is declared but never registered (`TinkerMeleeWeapons#registerTools` leaves
`cutlass` commented out), exactly the way its battleaxe art shipped without the battleaxe and
Forgeweave's battleaxe already derives from it (NOTICE.md, issue #159). So the scimitar's head now
takes upstream's own curved blade unchanged and the reshape is gone. The handle and binding layers
are unchanged: `ToolConstants#SCIMITAR`'s EXTRA part is `cross_guard`, so the layer keeps showing
the cross guard's own pixels rather than the cutlass's knuckle bow.

The katana is no longer derived here at all -- neither clone has any counterpart shape and #198's
stand-in failed playtest, so its art is freshly authored again. See
`scripts/generate_katana_art.py` and issue #279.

Usage: python3 scripts/derive_m3_weapon_art.py
Requires Pillow (`pip install pillow`), and the 1.12 clone at the path CLAUDE.md pins.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_1_12 = Path.home() / "development/minecraft/references/tinkers-1.12/resources/assets/tconstruct/textures/items"

ASSETS = ROOT / "src/main/resources/assets/forgeweave/textures"
DERIVED_ITEM = ASSETS / "derived/item"
DERIVED_TOOLS = ASSETS / "derived/tools"


def load(*relative: str) -> Image.Image:
    return Image.open(UPSTREAM_1_12.joinpath(*relative)).convert("RGBA")


def save_all(images: dict[Path, Image.Image]) -> None:
    for path, image in images.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        print(f"wrote {path}")


def main() -> None:
    if not UPSTREAM_1_12.is_dir():
        raise SystemExit(f"1.12 clone not found at {UPSTREAM_1_12} -- see CLAUDE.md for how to re-create it")

    curved_blade = load("cutlass/blade.png")

    outputs = {
        DERIVED_ITEM / "curved_blade.png": curved_blade,
        DERIVED_TOOLS / "scimitar_head.png": curved_blade,
        DERIVED_TOOLS / "scimitar_handle.png": load("broadsword/handle.png"),
        DERIVED_TOOLS / "scimitar_binding.png": load("rapier/guard.png"),
    }

    # The vein hammer head part icon adopts the already-derived tool-layer pixels (issue #151/#157,
    # NOTICE.md) rather than upstream directly -- see the module docstring's table.
    vein_hammer_layer = DERIVED_TOOLS / "vein_hammer_head.png"
    if not vein_hammer_layer.is_file():
        raise SystemExit(f"expected the already-derived {vein_hammer_layer} to exist")
    outputs[DERIVED_ITEM / "vein_hammer_head.png"] = Image.open(vein_hammer_layer).convert("RGBA")

    save_all(outputs)


if __name__ == "__main__":
    main()
