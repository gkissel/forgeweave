#!/usr/bin/env python3
"""Derives the scimitar/katana tool art and the vein hammer head part icon from upstream
(issue #198, maintainer decision 2026-08-12): replaces every remaining script-generated "original"
art for these shapes with upstream-derived pixels. Every output's pixels trace to an upstream file --
either a straight copy, a reuse of an already-derived Forgeweave part (the same file another tool's
layer already uses), or a minimal geometric reshape built only from copy/paste/mirror of the source's
own pixels (no new colors, no manual painting), the same technique `derive_warmace_art.py` uses for
the war mace head.

The dagger's own art used to be derived here too (1.12's mismatched `knife_blade.png`/broadsword
handle, per #198) but issue #278 moved it to `derive_dagger_art.py`, sourced from the 1.20 clone's
real dagger instead -- see that script's docstring.

| Output | Upstream source | Transform |
| --- | --- | --- |
| `derived/item/curved_blade.png`, `derived/tools/scimitar_head.png` | 1.12 `items/parts/sword_blade.png` | RESHAPE (below) -- no scimitar/curved-blade shape exists in either clone, so per #198 this derives from the closest upstream equivalent, the sword blade family |
| `derived/tools/scimitar_handle.png` | 1.12 `items/broadsword/handle.png` | none -- same `TOOL_HANDLE` reuse as the dagger |
| `derived/tools/scimitar_binding.png` | 1.12 `items/rapier/guard.png` | none -- `ToolConstants#SCIMITAR`'s EXTRA part is the existing `cross_guard`, itself derived from this file (NOTICE.md); the layer reuses the same pixels the part icon already does |
| `derived/item/katana_blade.png`, `derived/tools/katana_head.png` | 1.12 `items/parts/large_sword_blade.png` | none -- no katana shape exists in either clone; the closest upstream equivalent's silhouette (a long straight double-edged blade) already reads as this tool's long blade, so no reshape is needed |
| `derived/tools/katana_handle.png` | 1.12 `items/broadsword/handle.png` | none -- same `TOOL_HANDLE` reuse |
| `derived/tools/katana_binding.png` | 1.12 `items/longsword/guard.png` | none -- `ToolConstants#KATANA`'s EXTRA part is the existing `hand_guard`, same reuse as the scimitar's binding |
| `derived/item/vein_hammer_head.png` | 1.20 `.../textures/item/tool/vein_hammer/head.png` (already the source for `derived/tools/vein_hammer_head.png`, issue #151/#157, NOTICE.md) | none -- the part icon just adopts the tool layer's own already-derived pixels, the same one-file-serves-both convention `pickaxe_head.png` uses |

The reshape, in one line: `sword_blade.png`'s top half (the tip end) is alpha-composited with a copy
of itself shifted one pixel toward the spine, masked to those rows only. Upstream's blade is a
straight, constant-width diagonal diamond; overlaying a one-pixel-shifted copy near the tip only
makes that end of the diamond wider while the base by the guard stays the original width, which
reads as a blade that widens toward its tip -- the scimitar's defining silhouette difference from a
straight sword blade. Every resulting pixel is a `sword_blade.png` pixel in its own color.

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


def reshape_curved(blade: Image.Image) -> Image.Image:
    """`sword_blade.png` widened toward its tip; see the module docstring."""
    w, h = blade.size
    shifted = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shifted.paste(blade, (-1, 0))
    tip_only = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    tip_rows = h // 2
    tip_only.paste(shifted.crop((0, 0, w, tip_rows)), (0, 0))
    out = blade.copy()
    out.alpha_composite(tip_only)
    return out


def save_all(images: dict[Path, Image.Image]) -> None:
    for path, image in images.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)
        print(f"wrote {path}")


def main() -> None:
    if not UPSTREAM_1_12.is_dir():
        raise SystemExit(f"1.12 clone not found at {UPSTREAM_1_12} -- see CLAUDE.md for how to re-create it")

    broadsword_handle = load("broadsword/handle.png")
    curved_blade = reshape_curved(load("parts/sword_blade.png"))
    large_sword_blade = load("parts/large_sword_blade.png")

    outputs = {
        DERIVED_ITEM / "curved_blade.png": curved_blade,
        DERIVED_TOOLS / "scimitar_head.png": curved_blade,
        DERIVED_TOOLS / "scimitar_handle.png": broadsword_handle,
        DERIVED_TOOLS / "scimitar_binding.png": load("rapier/guard.png"),
        DERIVED_ITEM / "katana_blade.png": large_sword_blade,
        DERIVED_TOOLS / "katana_head.png": large_sword_blade,
        DERIVED_TOOLS / "katana_handle.png": broadsword_handle,
        DERIVED_TOOLS / "katana_binding.png": load("longsword/guard.png"),
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
